package com.guilhermetonelli.agendamento.auditoria;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static com.guilhermetonelli.agendamento.Cenario.AS_9H;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.CANCELADO;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.CONCLUIDO;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.CRIADO;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.NAO_COMPARECEU;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.REMARCADO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.Agendamento;
import com.guilhermetonelli.agendamento.auditoria.EventoDoHistorico.Autor;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.shared.domain.Antecedencia;
import com.jayway.jsonpath.JsonPath;

/** A linha do tempo de um agendamento, derivada das revisões do Envers; só o administrador a lê. */
class HistoricoApiTest extends IntegracaoTest {

    private static final Autor ADMINISTRADOR = new Autor("Administrador", Papel.ADMIN);

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private EntityManager em;

    private Cenario cenario;
    private UUID ana;
    private Cadastro carlos;
    private Cadastro diana;

    @BeforeEach
    void cadastrar() {
        cenario = new Cenario(api);
        ana = cenario.barbeiro("Ana");
        carlos = cenario.cliente("Carlos Lima");
        diana = cenario.cliente("Diana Reis");
    }

    @Test
    void contaAHistoriaDeUmAgendamento() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);
        cenario.reagendar(api, id, AS_8H30);
        cenario.cancelar(api, id, "Imprevisto");

        var eventos = historico(api, id);

        assertThat(eventos).extracting(EventoDoHistorico::tipo).containsExactly(CRIADO, REMARCADO, CANCELADO);
        assertThat(eventos.get(1).de()).isEqualTo(Instant.parse(AS_8H));
        assertThat(eventos.get(1).para()).isEqualTo(Instant.parse(AS_8H30));
        assertThat(eventos.get(2).motivo()).isEqualTo("Imprevisto");
        assertThat(eventos).extracting(EventoDoHistorico::quando).isSorted();
        // A conta do administrador do teste não existe no cadastro: o nome cai para o do papel.
        assertThat(eventos).extracting(EventoDoHistorico::autor).containsOnly(ADMINISTRADOR);
    }

    @Test
    void oJsonTemOsCamposQueATelaLe() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);
        cenario.reagendar(api, id, AS_8H30);

        String json = api.get().uri("/api/agendamentos/{id}/historico", id).retrieve().body(String.class);

        assertThat(JsonPath.<String>read(json, "$[0].tipo")).isEqualTo("CRIADO");
        assertThat(JsonPath.<String>read(json, "$[0].autor.papel")).isEqualTo("ADMIN");
        assertThat(JsonPath.<String>read(json, "$[0].de")).isNull();
        assertThat(JsonPath.<String>read(json, "$[1].de")).isEqualTo(AS_8H);
        assertThat(JsonPath.<String>read(json, "$[1].para")).isEqualTo(AS_8H30);
        assertThat(JsonPath.<String>read(json, "$[1].motivo")).isNull();
        assertThat(JsonPath.<String>read(json, "$[1].quando")).isNotBlank();
    }

    @Test
    void concluirEFaltaViramEventos() {
        UUID atendido = cenario.reservar(api, ana, carlos.id(), AS_8H);
        UUID faltou = cenario.reservar(api, ana, diana.id(), AS_9H);
        relogio.ajustar(Instant.parse(AS_9H));
        var daAna = apiComo(comoProfissional(ana));

        assertThat(encerrar(daAna, atendido, "concluir")).isEqualTo(HttpStatus.OK);
        assertThat(encerrar(daAna, faltou, "nao-compareceu")).isEqualTo(HttpStatus.OK);

        var autor = new Autor("Ana", Papel.PROFISSIONAL);
        var concluido = historico(api, atendido);
        var falta = historico(api, faltou);
        assertThat(concluido).extracting(EventoDoHistorico::tipo).containsExactly(CRIADO, CONCLUIDO);
        assertThat(concluido.getLast().autor()).isEqualTo(autor);
        assertThat(falta).extracting(EventoDoHistorico::tipo).containsExactly(CRIADO, NAO_COMPARECEU);
        assertThat(falta.getLast().autor()).isEqualTo(autor);
    }

    @Test
    void clienteCancelandoTemONomeDoCliente() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);

        cenario.cancelar(apiComo(comoCliente(carlos.id())), id, null);

        var eventos = historico(api, id);
        assertThat(eventos).extracting(EventoDoHistorico::tipo).containsExactly(CRIADO, CANCELADO);
        assertThat(eventos.getFirst().autor()).isEqualTo(ADMINISTRADOR);
        assertThat(eventos.getLast().autor()).isEqualTo(new Autor("Carlos Lima", Papel.CLIENTE));
        assertThat(eventos.getLast().motivo()).isNull();
    }

    @Test
    void semUsuarioViraSistema() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);

        transacao.executeWithoutResult(status ->
                em.find(Agendamento.class, id).cancelar("Sem resposta", relogio.instant(), Antecedencia.LIVRE));

        var ultimo = historico(api, id).getLast();
        assertThat(ultimo.tipo()).isEqualTo(CANCELADO);
        assertThat(ultimo.motivo()).isEqualTo("Sem resposta");
        assertThat(ultimo.autor()).isEqualTo(new Autor("Sistema", null));
    }

    @Test
    void historicoSemRevisaoDeCriacaoNaoQuebra() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);
        cenario.reagendar(api, id, AS_8H30);
        cenario.cancelar(api, id, "Imprevisto");
        // Como um agendamento de antes da V10: sem a revisão de criação, a primeira que sobra é só o ponto de partida.
        jdbc.update("DELETE FROM agendamento_aud WHERE revtype = 0");

        var eventos = historico(api, id);

        assertThat(eventos).extracting(EventoDoHistorico::tipo).containsExactly(CANCELADO);
    }

    @Test
    void aOrdemVemDoNumeroDaRevisaoEntaoRelogioNaoImporta() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);
        cenario.reagendar(api, id, AS_8H30);
        cenario.cancelar(api, id, "Imprevisto");
        // Revisões na mesma milissegundo, ou com o relógio do servidor voltando, não podem embaralhar a história.
        jdbc.update("UPDATE revinfo SET revtstmp = 2000000000000 - rev");

        var eventos = historico(api, id);

        assertThat(eventos).extracting(EventoDoHistorico::tipo).containsExactly(CRIADO, REMARCADO, CANCELADO);
    }

    @Test
    void soOAdminLeOHistorico() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);

        assertThat(status(api, id)).isEqualTo(HttpStatus.OK);
        assertThat(status(apiComo(comoProfissional(ana)), id)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(apiComo(comoCliente(carlos.id())), id)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(apiAnonima(), id)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(api, UUID.randomUUID())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private List<EventoDoHistorico> historico(RestClient quem, UUID agendamento) {
        // Confere o status antes de ler o corpo: uma falha vira "esperava 200", não um erro de JSON.
        return quem.get().uri("/api/agendamentos/{id}/historico", agendamento).exchange((requisicao, resposta) -> {
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            return List.of(resposta.bodyTo(EventoDoHistorico[].class));
        });
    }

    private HttpStatus status(RestClient quem, UUID agendamento) {
        return HttpStatus.valueOf(quem.get().uri("/api/agendamentos/{id}/historico", agendamento)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value());
    }

    private HttpStatus encerrar(RestClient quem, UUID agendamento, String acao) {
        return HttpStatus.valueOf(quem.patch().uri("/api/agendamentos/{id}/" + acao, agendamento)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value());
    }
}
