package com.guilhermetonelli.agendamento.shared.config;

import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Regras do negócio configuráveis em {@code agendamento.*}.
 *
 * @param fusoHorario fuso em que a jornada e as datas pedidas pela API são interpretadas
 * @param intervaloEntreHorarios os horários oferecidos começam em múltiplos deste intervalo
 * @param janelaDeReserva até quanto tempo à frente é possível consultar e reservar
 * @param antecedenciaMinimaReserva quanto tempo antes do início um horário ainda pode ser reservado
 * @param antecedenciaMinimaCancelamento até quanto tempo antes do início o cliente pode cancelar ou reagendar
 * @param notificacoes avisos enviados às pessoas quando a agenda muda
 */
@ConfigurationProperties("agendamento")
public record AgendamentoProperties(
        @DefaultValue("America/Sao_Paulo") ZoneId fusoHorario,
        @DefaultValue("PT15M") Duration intervaloEntreHorarios,
        @DefaultValue("P90D") Period janelaDeReserva,
        @DefaultValue("PT1H30M") Duration antecedenciaMinimaReserva,
        @DefaultValue("PT2H") Duration antecedenciaMinimaCancelamento,
        @DefaultValue Notificacoes notificacoes) {

    /** @param remetente quem aparece como autor dos e-mails ({@code MAIL_REMETENTE} em produção) */
    public record Notificacoes(String remetente) {}

    public AgendamentoProperties {
        if (intervaloEntreHorarios.toSeconds() < 60) {
            throw new IllegalArgumentException("agendamento.intervalo-entre-horarios deve ser de pelo menos 1 minuto");
        }
        if (antecedenciaMinimaReserva.isNegative()) {
            throw new IllegalArgumentException("agendamento.antecedencia-minima-reserva não pode ser negativa");
        }
        if (antecedenciaMinimaCancelamento.isNegative()) {
            throw new IllegalArgumentException("agendamento.antecedencia-minima-cancelamento não pode ser negativa");
        }
    }
}
