package com.guilhermetonelli.agendamento.notificacao;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Data e hora como a barbearia fala: {@code sáb 24/10 às 14h30}. */
public final class Quando {

    /** Tabela fixa: a abreviação do JDK para pt-BR leva ponto ("sáb."). */
    private static final String[] DIAS = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    private Quando() {}

    public static String formatar(Instant instante, ZoneId fuso) {
        ZonedDateTime data = instante.atZone(fuso);
        return "%s %02d/%02d às %dh%02d".formatted(
                DIAS[data.getDayOfWeek().getValue() - 1], data.getDayOfMonth(), data.getMonthValue(), data.getHour(), data.getMinute());
    }
}
