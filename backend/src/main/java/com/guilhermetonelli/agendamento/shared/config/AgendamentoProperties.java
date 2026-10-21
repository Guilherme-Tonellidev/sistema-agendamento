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
 */
@ConfigurationProperties("agendamento")
public record AgendamentoProperties(
        @DefaultValue("America/Sao_Paulo") ZoneId fusoHorario,
        @DefaultValue("PT15M") Duration intervaloEntreHorarios,
        @DefaultValue("P90D") Period janelaDeReserva) {

    public AgendamentoProperties {
        if (intervaloEntreHorarios.toSeconds() < 60) {
            throw new IllegalArgumentException("agendamento.intervalo-entre-horarios deve ser de pelo menos 1 minuto");
        }
    }
}
