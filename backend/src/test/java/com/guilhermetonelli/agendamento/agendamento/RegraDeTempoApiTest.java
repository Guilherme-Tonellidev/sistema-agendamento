package com.guilhermetonelli.agendamento.agendamento;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static com.guilhermetonelli.agendamento.Cenario.AS_9H;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.agendamento.dto.EncaixeRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.jayway.jsonpath.JsonPath;

/**
 * O tempo muda com o papel: o administrador não tem antecedência, passado nem janela de reserva; o profissional
 * não tem antecedência, mas só reserva o futuro; o cliente segue o prazo de sempre. Jornada de quarta,
 * 21/10/2026, das 08:00 às 12:00 em São Paulo (11:00 às 15:00 UTC); serviço de 30 minutos.
 */
class RegraDeTempoApiTest extends IntegracaoTest {

    private static final String DAQUI_A_91_DIAS = "2027-01-20T11:00:00Z";

    private Cenario cenario;
    private UUID ana;
    private UUID bruno;
    private Cadastro carlos;
    private Cadastro diana;

    @BeforeEach
    void cadastrar() {
        cenario = new Cenario(api);
        ana = cenario.barbeiro("Ana Souza");
        bruno = cenario.barbeiro("Bruno Dias");
        carlos = cenario.cliente("Carlos Lima");
        diana = cenario.cliente("Diana Reis");
    }

    @Test
    void profissionalReservaSemAntecedenciaMasNaoNoPassado() {
        relogio.ajustar(Instant.parse("2026-10-21T10:50:00Z"));

        var doCliente = reservar(apiComo(comoCliente(carlos.id())), ana, carlos, AS_8H);
        var daAna = reservar(apiComo(comoProfissional(ana)), ana, carlos, AS_8H);

        assertThat(doCliente).satisfies(regraDeNegocio("antecedência"));
        assertThat(daAna.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        relogio.ajustar(Instant.parse("2026-10-21T11:20:00Z"));

        assertThat(reservar(apiComo(comoProfissional(bruno)), bruno, diana, AS_8H)).satisfies(regraDeNegocio("passou"));
    }

    @Test
    void adminReservaNoPassadoEAlemDaJanela() {
        relogio.ajustar(Instant.parse("2026-10-21T13:00:00Z"));

        var noPassado = reservar(api, ana, carlos, AS_8H);
        var alemDaJanela = reservar(api, ana, carlos, DAQUI_A_91_DIAS);
        var doCliente = reservar(apiComo(comoCliente(carlos.id())), ana, carlos, DAQUI_A_91_DIAS);

        assertThat(noPassado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(alemDaJanela.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(doCliente).satisfies(regraDeNegocio("só está aberta"));
    }

    @Test
    void adminEncaixaNoPassadoEAlemDaJanelaMasProfissionalNao() {
        relogio.ajustar(Instant.parse("2026-10-21T13:00:00Z"));

        var noPassado = encaixar(api, ana, AS_8H, "Júlia Prado");
        var alemDaJanela = encaixar(api, ana, DAQUI_A_91_DIAS, "Marcos Vieira");
        var daAna = encaixar(apiComo(comoProfissional(ana)), ana, AS_8H30, "Rita Nunes");

        assertThat(noPassado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(alemDaJanela.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(daAna).satisfies(regraDeNegocio("passou"));
    }

    @Test
    void adminCancelaEReagendaAtendimentoEmAndamento() {
        UUID daAna = cenario.reservar(api, ana, carlos.id(), AS_8H);
        UUID doBruno = cenario.reservar(api, bruno, diana.id(), AS_8H);
        relogio.ajustar(Instant.parse("2026-10-21T11:10:00Z"));

        var peloProfissional = cancelar(apiComo(comoProfissional(bruno)), doBruno);
        var cancelamento = cancelar(api, daAna);
        var reagendamento = reagendar(api, doBruno, AS_9H);

        assertThat(peloProfissional).satisfies(regraDeNegocio("já começou"));
        assertThat(cancelamento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reagendamento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(buscar(daAna).status()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(buscar(doBruno).inicio()).isEqualTo(Instant.parse(AS_9H));
    }

    @Test
    void adminContinuaSujeitoAJornadaAlinhamentoEChoque() {
        cenario.reservar(api, ana, carlos.id(), AS_8H);

        assertThat(reservar(api, ana, diana, "2026-10-21T16:00:00Z")).satisfies(regraDeNegocio("jornada"));
        assertThat(reservar(api, ana, diana, "2026-10-21T11:05:00Z")).satisfies(regraDeNegocio("a cada 15 minutos"));
        assertThat(reservar(api, ana, diana, AS_8H)).satisfies(conflito("o profissional já tem"));
        assertThat(reservar(api, ana, diana, "+300000-01-01T11:00:00Z")).satisfies(regraDeNegocio("entre os anos"));
        assertThat(reservar(api, ana, diana, "+10000-01-05T11:00:00Z")).satisfies(regraDeNegocio("entre os anos"));
    }

    @Test
    void adminNaoReservaDentroDeBloqueio() {
        bloquear(ana, AS_9H, "2026-10-21T13:00:00Z");

        assertThat(reservar(api, ana, diana, "2026-10-21T12:15:00Z")).satisfies(regraDeNegocio("bloqueado"));
        assertThat(reservar(api, ana, diana, "2026-10-21T13:00:00Z").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void profissionalReagendaParaMenosDe1h30MasNaoParaOPassado() {
        UUID agendamento = cenario.reservar(api, ana, carlos.id(), AS_9H);
        relogio.ajustar(Instant.parse("2026-10-21T11:20:00Z"));
        var daAna = apiComo(comoProfissional(ana));

        var emDezMinutos = reagendar(daAna, agendamento, AS_8H30);
        var noPassado = reagendar(daAna, agendamento, AS_8H);

        assertThat(emDezMinutos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(noPassado).satisfies(regraDeNegocio("passou"));
        assertThat(buscar(agendamento).inicio()).isEqualTo(Instant.parse(AS_8H30));
    }

    @Test
    void adminNaoConcluiAntesDoInicio() {
        UUID agendamento = cenario.reservar(api, ana, carlos.id(), AS_8H);

        var resposta = api.patch().uri("/api/agendamentos/{id}/concluir", agendamento).retrieve().toEntity(String.class);

        assertThat(resposta).satisfies(regraDeNegocio("depois do início"));
    }

    private ResponseEntity<String> reservar(RestClient quem, UUID barbeiro, Cadastro cliente, String inicio) {
        return quem.post().uri("/api/agendamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(barbeiro, cenario.corte(), cliente.id(), Instant.parse(inicio)))
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> encaixar(RestClient quem, UUID barbeiro, String inicio, String nomeDoCliente) {
        return quem.post().uri("/api/agendamentos/encaixe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EncaixeRequest(barbeiro, cenario.corte(), Instant.parse(inicio), nomeDoCliente, null))
                .retrieve()
                .toEntity(String.class);
    }

    private void bloquear(UUID barbeiro, String inicio, String fim) {
        var resposta = api.post().uri("/api/profissionais/{id}/bloqueios", barbeiro)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloqueioRequest(Instant.parse(inicio), Instant.parse(fim), null))
                .retrieve()
                .toBodilessEntity();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> cancelar(RestClient quem, UUID agendamento) {
        return quem.patch().uri("/api/agendamentos/{id}/cancelar", agendamento).retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> reagendar(RestClient quem, UUID agendamento, String inicio) {
        return quem.patch().uri("/api/agendamentos/{id}/reagendar", agendamento)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReagendamentoRequest(Instant.parse(inicio)))
                .retrieve()
                .toEntity(String.class);
    }

    private AgendamentoResponse buscar(UUID agendamento) {
        return api.get().uri("/api/agendamentos/{id}", agendamento).retrieve().body(AgendamentoResponse.class);
    }

    private static Consumer<ResponseEntity<String>> regraDeNegocio(String trecho) {
        return erro(HttpStatus.UNPROCESSABLE_CONTENT, trecho);
    }

    private static Consumer<ResponseEntity<String>> conflito(String trecho) {
        return erro(HttpStatus.CONFLICT, trecho);
    }

    private static Consumer<ResponseEntity<String>> erro(HttpStatus status, String trecho) {
        return resposta -> {
            assertThat(resposta.getStatusCode()).isEqualTo(status);
            assertThat(JsonPath.<String>read(resposta.getBody(), "$.detail")).contains(trecho);
        };
    }
}
