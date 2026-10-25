package com.guilhermetonelli.agendamento.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.notificacao.email.Email;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;

/** Liga a regra de quem é avisado ao modelo e ao envio; o broker de verdade fica para o teste de integração. */
class ConsumidorDeEmailTest {

    private static final Instant INICIO = Instant.parse("2026-10-24T17:30:00Z");
    private static final UUID ID = UUID.randomUUID();

    private final List<Email> enviados = new ArrayList<>();
    private final ConsumidorDeEmail consumidor = new ConsumidorDeEmail(enviados::add, propriedades());

    @Test
    void reservaEnviaOComprovanteComOHorarioNoFusoDaBarbearia() {
        consumidor.tratar(new AgendamentoCriado(ID, ID, ID, INICIO, INICIO, aviso(Papel.CLIENTE, "carlos@exemplo.com")));

        assertThat(enviados).singleElement().satisfies(email -> {
            assertThat(email.para()).isEqualTo("carlos@exemplo.com");
            assertThat(email.assunto()).isEqualTo("Horário confirmado: Corte, sáb 24/10 às 14h30");
        });
    }

    @Test
    void clienteQueRemarcaOuCancelaNaoRecebeEmail() {
        Aviso feitoPeloCliente = aviso(Papel.CLIENTE, "carlos@exemplo.com");

        consumidor.tratar(new AgendamentoReagendado(ID, ID, ID, INICIO, INICIO, INICIO, feitoPeloCliente));
        consumidor.tratar(new AgendamentoCancelado(ID, ID, ID, INICIO, null, feitoPeloCliente));

        assertThat(enviados).isEmpty();
    }

    @Test
    void barbeiroQueCancelaAvisaOCliente() {
        consumidor.tratar(new AgendamentoCancelado(ID, ID, ID, INICIO, "imprevisto", aviso(Papel.PROFISSIONAL, "carlos@exemplo.com")));

        assertThat(enviados).singleElement().satisfies(email -> assertThat(email.texto()).contains("Motivo: imprevisto"));
    }

    @Test
    void clienteDeEncaixeSemEmailNaoRecebeNada() {
        consumidor.tratar(new AgendamentoCriado(ID, ID, ID, INICIO, INICIO, aviso(Papel.PROFISSIONAL, null)));

        assertThat(enviados).isEmpty();
    }

    @Test
    void falhaNoEnvioSobeParaAcionarAsTentativas() {
        var falho = new ConsumidorDeEmail(email -> { throw new IllegalStateException("SMTP fora do ar"); }, propriedades());

        assertThatThrownBy(() -> falho.tratar(new AgendamentoCriado(ID, ID, ID, INICIO, INICIO, aviso(Papel.CLIENTE, "carlos@exemplo.com"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Aviso aviso(Papel autor, String email) {
        return new Aviso(autor, "Carlos Mendes", email, "Rafael Souza", "Corte", new BigDecimal("45.00"));
    }

    private static AgendamentoProperties propriedades() {
        return new AgendamentoProperties(ZoneId.of("America/Sao_Paulo"), Duration.ofMinutes(15), Period.ofDays(90), Duration.ZERO, Duration.ZERO,
                new AgendamentoProperties.Notificacoes("Barbearia <b@exemplo.com>"));
    }
}
