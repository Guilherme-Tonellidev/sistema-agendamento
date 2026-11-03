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
 * @param lembrete quando o cliente é lembrado do horário marcado
 * @param cacheHorarios cache dos horários livres no Redis
 */
@ConfigurationProperties("agendamento")
public record AgendamentoProperties(
        @DefaultValue("America/Sao_Paulo") ZoneId fusoHorario,
        @DefaultValue("PT15M") Duration intervaloEntreHorarios,
        @DefaultValue("P90D") Period janelaDeReserva,
        @DefaultValue("PT1H30M") Duration antecedenciaMinimaReserva,
        @DefaultValue("PT2H") Duration antecedenciaMinimaCancelamento,
        @DefaultValue Notificacoes notificacoes,
        @DefaultValue Lembrete lembrete,
        @DefaultValue CacheHorarios cacheHorarios) {

    /** @param remetente quem aparece como autor dos e-mails ({@code MAIL_REMETENTE} em produção) */
    public record Notificacoes(String remetente) {}

    /**
     * @param antecedencia quanto tempo antes do início o lembrete sai
     * @param antecedenciaReservaTardia quanto tempo antes do início sai o lembrete de quem reservou com menos
     *     de {@code antecedencia}: esse cliente recebe só este, e menos de uma hora de aviso não vale o e-mail
     */
    public record Lembrete(
            @DefaultValue("PT24H") Duration antecedencia, @DefaultValue("PT1H") Duration antecedenciaReservaTardia) {

        public Lembrete {
            if (!antecedencia.isPositive()) {
                throw new IllegalArgumentException("agendamento.lembrete.antecedencia deve ser positiva");
            }
            if (!antecedenciaReservaTardia.isPositive() || antecedenciaReservaTardia.compareTo(antecedencia) >= 0) {
                throw new IllegalArgumentException(
                        "agendamento.lembrete.antecedencia-reserva-tardia deve ser positiva e menor que agendamento.lembrete.antecedencia");
            }
        }
    }

    /**
     * @param ttl por quanto tempo uma consulta de horários livres fica guardada; também é o quanto um horário velho
     *     pode aparecer se o Redis estiver fora do ar quando a agenda mudar
     */
    public record CacheHorarios(@DefaultValue("PT2M") Duration ttl) {

        public CacheHorarios {
            if (!ttl.isPositive()) {
                throw new IllegalArgumentException("agendamento.cache-horarios.ttl deve ser positivo");
            }
        }
    }

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
