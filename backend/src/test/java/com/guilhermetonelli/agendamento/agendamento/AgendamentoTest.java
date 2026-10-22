package com.guilhermetonelli.agendamento.agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.guilhermetonelli.agendamento.cliente.Cliente;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

class AgendamentoTest {

    private static final Instant INICIO = Instant.parse("2026-10-21T11:00:00Z");
    private static final Duration DUAS_HORAS = Duration.ofHours(2);
    private static final Periodo NOVO_HORARIO =
            new Periodo(INICIO.plus(DUAS_HORAS), INICIO.plus(DUAS_HORAS).plus(Duration.ofMinutes(30)));

    private final Agendamento agendamento = new Agendamento(
            new Profissional("Ana Souza", "ana@barbearia.com", null),
            new Servico("Corte", null, 30, new BigDecimal("45.00")),
            new Cliente("Carlos Lima", "carlos@gmail.com", null),
            new Periodo(INICIO, INICIO.plus(Duration.ofMinutes(30))));

    @Test
    void nasceConfirmadoComOPrecoDoServico() {
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(agendamento.getPreco()).isEqualByComparingTo("45.00");
    }

    @Test
    void cancelaComAntecedenciaERegistraMomentoEMotivo() {
        Instant agora = INICIO.minus(DUAS_HORAS);

        agendamento.cancelar("  Imprevisto ", agora, DUAS_HORAS);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getCanceladoEm()).isEqualTo(agora);
        assertThat(agendamento.getMotivoCancelamento()).isEqualTo("Imprevisto");
    }

    @Test
    void recusaCancelamentoTardioOuDepoisDoInicio() {
        assertThatThrownBy(() -> agendamento.cancelar(null, INICIO.minus(Duration.ofMinutes(90)), DUAS_HORAS))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("2h de antecedência");
        assertThatThrownBy(() -> agendamento.cancelar(null, INICIO, Duration.ZERO))
                .hasMessageContaining("já começou");
    }

    @Test
    void semAntecedenciaMinimaCancelaAteOInicio() {
        agendamento.cancelar(null, INICIO.minusSeconds(1), Duration.ZERO);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void concluiERegistraFaltaSoDepoisDoInicioESoSeConfirmado() {
        assertThatThrownBy(() -> agendamento.concluir(INICIO.minusSeconds(1))).hasMessageContaining("depois do início");

        agendamento.concluir(INICIO);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThatThrownBy(() -> agendamento.registrarFalta(INICIO)).hasMessageContaining("status CONCLUIDO");
    }

    @Test
    void reagendaSoAntesDoInicio() {
        assertThatThrownBy(() -> agendamento.reagendar(NOVO_HORARIO, INICIO, Duration.ZERO)).hasMessageContaining("já começou");

        agendamento.reagendar(NOVO_HORARIO, INICIO.minusSeconds(1), Duration.ZERO);
        assertThat(agendamento.horario()).isEqualTo(NOVO_HORARIO);
    }

    @Test
    void reagendamentoSegueOPrazoDoCancelamento() {
        assertThatThrownBy(() -> agendamento.reagendar(NOVO_HORARIO, INICIO.minus(Duration.ofMinutes(90)), DUAS_HORAS))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("O reagendamento precisa ser feito com pelo menos 2h de antecedência");

        agendamento.reagendar(NOVO_HORARIO, INICIO.minus(DUAS_HORAS), DUAS_HORAS);
        assertThat(agendamento.horario()).isEqualTo(NOVO_HORARIO);
    }
}
