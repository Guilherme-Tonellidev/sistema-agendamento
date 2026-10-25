package com.guilhermetonelli.agendamento.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.notificacao.dto.NotificacoesResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;

/** O sino do barbeiro: cada um vê, lê e marca só as próprias notificações. As linhas são semeadas direto no banco. */
class NotificacaoApiTest extends IntegracaoTest {

    private static final Instant BASE = Instant.parse("2026-10-20T12:00:00Z");

    private UUID ana;
    private UUID bruno;
    private RestClient comoAna;

    @BeforeEach
    void cadastrarBarbeiros() {
        ana = cadastrarProfissional("Ana Souza", "ana@barbearia.com");
        bruno = cadastrarProfissional("Bruno Dias", "bruno@barbearia.com");
        comoAna = apiComo(comoProfissional(ana));
    }

    @Test
    void semNotificacoesDevolveZeroEListaVazia() {
        NotificacoesResponse resposta = listar(comoAna);

        assertThat(resposta.naoLidas()).isZero();
        assertThat(resposta.recentes()).isEmpty();
    }

    @Test
    void listaDaMaisRecenteParaAMaisAntigaEMostraAsLidas() {
        semear(ana, "Primeira.", BASE, true);
        semear(ana, "Segunda.", BASE.plusSeconds(60), false);
        semear(ana, "Terceira.", BASE.plusSeconds(120), false);
        semear(bruno, "Do outro barbeiro.", BASE.plusSeconds(180), false);

        NotificacoesResponse resposta = listar(comoAna);

        assertThat(resposta.naoLidas()).isEqualTo(2);
        assertThat(resposta.recentes()).extracting("texto", "lida")
                .containsExactly(
                        tuple("Terceira.", false),
                        tuple("Segunda.", false),
                        tuple("Primeira.", true));
    }

    @Test
    void devolveAsVinteMaisRecentesMasContaTodasAsNaoLidas() {
        for (int i = 0; i < 25; i++) {
            semear(ana, "Notificação " + i, BASE.plusSeconds(i), false);
        }

        NotificacoesResponse resposta = listar(comoAna);

        assertThat(resposta.naoLidas()).isEqualTo(25);
        assertThat(resposta.recentes()).hasSize(20);
        assertThat(resposta.recentes().getFirst().texto()).isEqualTo("Notificação 24");
        assertThat(resposta.recentes().getLast().texto()).isEqualTo("Notificação 5");
    }

    @Test
    void lerMarcaUmaEPodeSerRepetidoSemMudarAHoraDeLeitura() {
        UUID alvo = semear(ana, "Alvo.", BASE, false);
        semear(ana, "Outra.", BASE.plusSeconds(60), false);

        assertThat(patch(comoAna, "/api/notificacoes/{id}/ler", alvo)).isEqualTo(HttpStatus.NO_CONTENT);
        Instant primeiraLeitura = lidaEm(alvo);
        assertThat(primeiraLeitura).isNotNull();
        assertThat(listar(comoAna).naoLidas()).isEqualTo(1);

        relogio.ajustar(BASE.plusSeconds(3600));
        assertThat(patch(comoAna, "/api/notificacoes/{id}/ler", alvo)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(lidaEm(alvo)).isEqualTo(primeiraLeitura);
    }

    @Test
    void lerTodasMarcaSoAsDoBarbeiroEPodeSerRepetido() {
        semear(ana, "Uma.", BASE, false);
        semear(ana, "Duas.", BASE.plusSeconds(60), false);
        semear(bruno, "Do outro barbeiro.", BASE, false);

        assertThat(patch(comoAna, "/api/notificacoes/ler-todas")).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(patch(comoAna, "/api/notificacoes/ler-todas")).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(listar(comoAna).naoLidas()).isZero();
        assertThat(listar(apiComo(comoProfissional(bruno))).naoLidas()).isEqualTo(1);
    }

    @Test
    void barbeiroNaoMarcaNotificacaoDeOutroNemUmaQueNaoExiste() {
        UUID daAna = semear(ana, "Da Ana.", BASE, false);
        RestClient comoBruno = apiComo(comoProfissional(bruno));

        assertThat(patch(comoBruno, "/api/notificacoes/{id}/ler", daAna)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(comoBruno, "/api/notificacoes/{id}/ler", UUID.randomUUID())).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(listar(comoAna).naoLidas()).isEqualTo(1);
    }

    @Test
    void clienteEAdministradorRecebem403NasTresRotas() {
        UUID alvo = semear(ana, "Da Ana.", BASE, false);

        for (RestClient quem : new RestClient[] {api, apiComo(comoCliente(UUID.randomUUID()))}) {
            assertThat(status(quem.get().uri("/api/notificacoes"))).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(patch(quem, "/api/notificacoes/{id}/ler", alvo)).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(patch(quem, "/api/notificacoes/ler-todas")).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(listar(comoAna).naoLidas()).isEqualTo(1);
    }

    @Test
    void semTokenRecebe401NasTresRotas() {
        RestClient anonima = apiAnonima();

        assertThat(status(anonima.get().uri("/api/notificacoes"))).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(patch(anonima, "/api/notificacoes/{id}/ler", UUID.randomUUID())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(patch(anonima, "/api/notificacoes/ler-todas")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private NotificacoesResponse listar(RestClient quem) {
        return quem.get().uri("/api/notificacoes").retrieve().body(NotificacoesResponse.class);
    }

    private HttpStatus patch(RestClient quem, String uri, Object... variaveis) {
        return HttpStatus.valueOf(quem.patch().uri(uri, variaveis).retrieve().toBodilessEntity().getStatusCode().value());
    }

    private HttpStatus status(RestClient.RequestHeadersSpec<?> requisicao) {
        return HttpStatus.valueOf(requisicao.retrieve().toBodilessEntity().getStatusCode().value());
    }

    private UUID cadastrarProfissional(String nome, String email) {
        return api.post().uri("/api/profissionais").contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest(nome, email, null))
                .retrieve().body(ProfissionalResponse.class).id();
    }

    private UUID semear(UUID profissional, String texto, Instant criadaEm, boolean lida) {
        UUID id = UUID.randomUUID();
        OffsetDateTime criada = criadaEm.atOffset(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO notificacao (id, profissional_id, tipo, texto, inicio, lida_em, versao, criado_em, atualizado_em)
                VALUES (?, ?, 'CRIADO', ?, ?, ?, 0, ?, ?)
                """, id, profissional, texto, criada.plusDays(1), lida ? criada : null, criada, criada);
        return id;
    }

    private Instant lidaEm(UUID id) {
        return jdbc.queryForObject("SELECT lida_em FROM notificacao WHERE id = ?", OffsetDateTime.class, id).toInstant();
    }
}
