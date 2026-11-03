package com.guilhermetonelli.agendamento.disponibilidade;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_9H;
import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;

/** Toda escrita que muda a agenda de um profissional troca a versão do cache dele, e só essa. */
class AgendaAlteradaTest extends IntegracaoTest {

    private Cenario cenario;
    private UUID barbeiro;
    private UUID cliente;

    @BeforeEach
    void montar() {
        cenario = new Cenario(api);
        barbeiro = cenario.barbeiro("Rafael Souza");
        cliente = cenario.cliente("Carlos Mendes").id();
    }

    @Test
    void reservarTrocaAVersao() {
        trocaAVersao(() -> cenario.reservar(api, barbeiro, cliente, AS_8H));
    }

    @Test
    void encaixarTrocaAVersao() {
        trocaAVersao(() -> cenario.encaixar(api, barbeiro, AS_8H, "Quem chegou agora"));
    }

    @Test
    void cancelarTrocaAVersao() {
        UUID reserva = cenario.reservar(api, barbeiro, cliente, AS_8H);

        trocaAVersao(() -> cenario.cancelar(api, reserva, null));
    }

    @Test
    void reagendarTrocaAVersao() {
        UUID reserva = cenario.reservar(api, barbeiro, cliente, AS_8H);

        trocaAVersao(() -> cenario.reagendar(api, reserva, AS_9H));
    }

    @Test
    void concluirTrocaAVersao() {
        UUID reserva = cenario.reservar(api, barbeiro, cliente, AS_8H);
        relogio.ajustar(Instant.parse(AS_9H));

        trocaAVersao(() -> encerrar(reserva, "concluir"));
    }

    @Test
    void registrarFaltaTrocaAVersao() {
        UUID reserva = cenario.reservar(api, barbeiro, cliente, AS_8H);
        relogio.ajustar(Instant.parse(AS_9H));

        trocaAVersao(() -> encerrar(reserva, "nao-compareceu"));
    }

    @Test
    void bloquearTrocaAVersao() {
        trocaAVersao(this::bloquear);
    }

    @Test
    void removerBloqueioTrocaAVersao() {
        UUID bloqueio = bloquear().id();

        trocaAVersao(() -> {
            var resposta = api.delete().uri("/api/profissionais/{id}/bloqueios/{b}", barbeiro, bloqueio)
                    .retrieve().toBodilessEntity();
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        });
    }

    @Test
    void definirJornadaTrocaAVersao() {
        trocaAVersao(() -> {
            var resposta = api.put().uri("/api/profissionais/{id}/jornada", barbeiro)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new JornadaDto(List.of(new IntervaloDeJornadaDto(MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)))))
                    .retrieve().toBodilessEntity();
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        });
    }

    @Test
    void escritaNaAgendaDeUmNaoMudaAVersaoDoOutro() {
        UUID outro = cenario.barbeiro("Bruno Dias");
        String antes = versao(outro);

        cenario.reservar(api, barbeiro, cliente, AS_8H);

        assertThat(versao(outro)).isNotNull().isEqualTo(antes);
    }

    @Test
    void reservaRecusadaNaoTrocaAVersao() {
        cenario.reservar(api, barbeiro, cliente, AS_8H);
        UUID outroCliente = cenario.cliente("Diana Alves").id();
        String antes = versao(barbeiro);

        var recusada = api.post().uri("/api/agendamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(barbeiro, cenario.corte(), outroCliente, Instant.parse(AS_8H)))
                .retrieve()
                .toEntity(String.class);

        assertThat(recusada.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(versao(barbeiro)).isNotNull().isEqualTo(antes);
    }

    private void trocaAVersao(Runnable escrita) {
        String antes = versao(barbeiro);

        escrita.run();

        assertThat(versao(barbeiro)).isNotNull().isNotEqualTo(antes);
    }

    private String versao(UUID profissional) {
        return redis.valor(CacheDeHorarios.chaveDaVersao(profissional));
    }

    private void encerrar(UUID agendamento, String acao) {
        var resposta = api.patch().uri("/api/agendamentos/{id}/{acao}", agendamento, acao).retrieve().toBodilessEntity();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private BloqueioResponse bloquear() {
        var resposta = api.post().uri("/api/profissionais/{id}/bloqueios", barbeiro)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloqueioRequest(Instant.parse("2026-10-21T15:00:00Z"), Instant.parse("2026-10-21T16:00:00Z"), "Almoço"))
                .retrieve()
                .toEntity(BloqueioResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }
}
