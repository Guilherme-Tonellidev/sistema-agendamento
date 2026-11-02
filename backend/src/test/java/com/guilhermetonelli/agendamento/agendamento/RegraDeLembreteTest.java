package com.guilhermetonelli.agendamento.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties.Lembrete;

/** Reserva com tempo de sobra lembra 24 h antes; reserva tardia, 1 h antes; faltando menos de 1 h, não lembra. */
class RegraDeLembreteTest {

    private static final Instant INICIO = Instant.parse("2026-10-24T17:30:00Z");
    private static final Lembrete CONFIG = new Lembrete(Duration.ofHours(24), Duration.ofHours(1));

    @ParameterizedTest(name = "reservado {0} s antes: lembra {1} s antes")
    @CsvSource({
        "259200,86400",
        "86400,86400",
        "86399,3600",
        "36000,3600",
        "3601,3600",
        "3600,",
        "1800,"
    })
    void escolheOMomentoDoLembrete(long reservadoAntes, Long lembraAntes) {
        Instant agora = INICIO.minusSeconds(reservadoAntes);

        Instant lembrarEm = RegraDeLembrete.quando(agora, INICIO, CONFIG);

        assertThat(lembrarEm).isEqualTo(lembraAntes == null ? null : INICIO.minusSeconds(lembraAntes));
    }
}
