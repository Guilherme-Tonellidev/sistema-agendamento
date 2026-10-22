package com.guilhermetonelli.agendamento.seguranca;

import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Pagina;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;

/** Quem pode o quê. Jornada de quarta, 21/10/2026, das 08:00 às 12:00 em São Paulo (11:00 às 15:00 UTC). */
class AutorizacaoApiTest extends IntegracaoTest {

    private static final Instant AS_8H = Instant.parse("2026-10-21T11:00:00Z");
    private static final Instant AS_9H = Instant.parse("2026-10-21T12:00:00Z");
    private static final JornadaDto QUARTA_DE_MANHA =
            new JornadaDto(List.of(new IntervaloDeJornadaDto(WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

    private UUID corte;
    private UUID ana;
    private UUID bruno;
    private UUID carlos;
    private UUID diana;

    @BeforeEach
    void cadastrarCenario() {
        corte = api.post().uri("/api/servicos").contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Corte", null, 30, new BigDecimal("45.00")))
                .retrieve().body(ServicoResponse.class).id();
        ana = cadastrarProfissional("Ana Souza", "ana@barbearia.com");
        bruno = cadastrarProfissional("Bruno Dias", "bruno@barbearia.com");
        carlos = cadastrarCliente("Carlos Lima", "carlos@gmail.com");
        diana = cadastrarCliente("Diana Reis", "diana@gmail.com");
    }

    @Test
    void anonimoConsultaAVitrineMasNaoAlteraNada() {
        RestClient anonima = apiAnonima();

        assertThat(status(anonima.get().uri("/api/servicos"))).isEqualTo(HttpStatus.OK);
        var perfil = anonima.get().uri("/api/profissionais/{id}", ana).retrieve().body(ProfissionalResponse.class);
        assertThat(perfil.nome()).isEqualTo("Ana Souza");
        assertThat(perfil.email()).as("o e-mail é o login e não aparece na vitrine").isNull();
        assertThat(status(anonima.get().uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data=2026-10-21", ana, corte)))
                .isEqualTo(HttpStatus.OK);

        var escrita = anonima.post().uri("/api/servicos").contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Barba", null, 30, BigDecimal.TEN)).retrieve().toEntity(String.class);
        assertThat(escrita.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(escrita.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(escrita.getHeaders().getContentType()).isNotNull()
                .matches(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith, "application/problem+json");
        assertThat(status(anonima.get().uri("/api/agendamentos"))).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(anonima.get().uri("/api/profissionais/{id}/bloqueios", ana))).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clienteNaoEntraNaAreaDoAdministrador() {
        RestClient cliente = apiComo(comoCliente(carlos));

        var servico = cliente.post().uri("/api/servicos").contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Barba", null, 30, BigDecimal.TEN)).retrieve().toEntity(String.class);

        assertThat(servico.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(cliente.get().uri("/api/clientes"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(cliente.put().uri("/api/profissionais/{id}/jornada", ana)
                .contentType(MediaType.APPLICATION_JSON).body(QUARTA_DE_MANHA))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clienteAcessaSoOProprioCadastro() {
        RestClient cliente = apiComo(comoCliente(carlos));

        assertThat(status(cliente.get().uri("/api/clientes/{id}", carlos))).isEqualTo(HttpStatus.OK);
        assertThat(status(cliente.get().uri("/api/clientes/{id}", diana))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clienteReservaSoParaSiEEnxergaSoOsProprios() {
        RestClient cliente = apiComo(comoCliente(carlos));

        UUID doCarlos = reservar(cliente, ana, carlos, AS_8H);
        UUID daDiana = reservar(api, bruno, diana, AS_8H);
        var paraOutro = cliente.post().uri("/api/agendamentos").contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(ana, corte, diana, AS_9H)).retrieve().toEntity(String.class);

        assertThat(paraOutro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(listar(cliente, "")).containsExactly(doCarlos);
        assertThat(listar(cliente, "?clienteId=" + diana)).containsExactly(doCarlos);
        // Agendamento alheio responde como se não existisse.
        assertThat(status(cliente.get().uri("/api/agendamentos/{id}", daDiana))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(cliente.patch().uri("/api/agendamentos/{id}/cancelar", daDiana))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void profissionalGerenciaSoAPropriaAgenda() {
        RestClient profissional = apiComo(comoProfissional(ana));
        UUID naAgendaDaAna = reservar(api, ana, carlos, AS_8H);
        reservar(api, bruno, diana, AS_8H);

        assertThat(status(profissional.put().uri("/api/profissionais/{id}/jornada", ana)
                .contentType(MediaType.APPLICATION_JSON).body(QUARTA_DE_MANHA))).isEqualTo(HttpStatus.OK);
        assertThat(status(profissional.put().uri("/api/profissionais/{id}/jornada", bruno)
                .contentType(MediaType.APPLICATION_JSON).body(QUARTA_DE_MANHA))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(profissional.get().uri("/api/profissionais/{id}/bloqueios", bruno))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(profissional.put().uri("/api/profissionais/{id}", ana).contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest("Ana", "ana@barbearia.com", null)))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(listar(profissional, "")).containsExactly(naAgendaDaAna);
    }

    @Test
    void soOProfissionalOuOAdminEncerramOAtendimento() {
        UUID agendamento = reservar(apiComo(comoCliente(carlos)), ana, carlos, AS_8H);
        relogio.ajustar(AS_9H);

        assertThat(status(apiComo(comoCliente(carlos)).patch().uri("/api/agendamentos/{id}/concluir", agendamento)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(apiComo(comoProfissional(bruno)).patch().uri("/api/agendamentos/{id}/concluir", agendamento)))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(apiComo(comoProfissional(ana)).patch().uri("/api/agendamentos/{id}/concluir", agendamento)))
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void prazoDeCancelamentoValeSoParaOCliente() {
        UUID doCarlos = reservar(api, ana, carlos, AS_8H);
        UUID daDiana = reservar(api, ana, diana, AS_9H);
        relogio.ajustar(AS_8H.minusSeconds(30 * 60));

        assertThat(status(apiComo(comoCliente(carlos)).patch().uri("/api/agendamentos/{id}/cancelar", doCarlos)))
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(status(apiComo(comoProfissional(ana)).patch().uri("/api/agendamentos/{id}/cancelar", doCarlos)))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(api.patch().uri("/api/agendamentos/{id}/cancelar", daDiana))).isEqualTo(HttpStatus.OK);
    }

    private static HttpStatusCode status(RestClient.RequestHeadersSpec<?> requisicao) {
        return requisicao.retrieve().toBodilessEntity().getStatusCode();
    }

    private UUID reservar(RestClient cliente, UUID profissional, UUID quem, Instant inicio) {
        var resposta = cliente.post().uri("/api/agendamentos").contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(profissional, corte, quem, inicio))
                .retrieve().toEntity(AgendamentoResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().id();
    }

    private static List<UUID> listar(RestClient cliente, String filtro) {
        Pagina<AgendamentoResponse> pagina = cliente.get().uri("/api/agendamentos" + filtro)
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return pagina.content().stream().map(AgendamentoResponse::id).toList();
    }

    private UUID cadastrarProfissional(String nome, String email) {
        UUID id = api.post().uri("/api/profissionais").contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest(nome, email, null))
                .retrieve().body(ProfissionalResponse.class).id();
        api.put().uri("/api/profissionais/{id}/servicos", id).contentType(MediaType.APPLICATION_JSON)
                .body(new ServicosDoProfissionalRequest(Set.of(corte))).retrieve().toBodilessEntity();
        api.put().uri("/api/profissionais/{id}/jornada", id).contentType(MediaType.APPLICATION_JSON)
                .body(QUARTA_DE_MANHA).retrieve().toBodilessEntity();
        return id;
    }

    private UUID cadastrarCliente(String nome, String email) {
        return api.post().uri("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                .body(new ClienteRequest(nome, email, null))
                .retrieve().body(ClienteResponse.class).id();
    }
}
