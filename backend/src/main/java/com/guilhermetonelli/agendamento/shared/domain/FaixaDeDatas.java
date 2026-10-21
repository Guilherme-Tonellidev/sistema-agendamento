package com.guilhermetonelli.agendamento.shared.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/**
 * Filtro de listagem por datas do fuso do negócio (limites inclusivos e opcionais), convertido em
 * instantes {@code [inicio, fim)}. Um limite nulo deixa aquele lado em aberto.
 */
public record FaixaDeDatas(Instant inicio, Instant fim) {

    /** Faixa de anos aceita nas datas; fora dela o PostgreSQL recusaria o valor. */
    public static final int ANO_MINIMO = 1900;
    public static final int ANO_MAXIMO = 9999;

    public static FaixaDeDatas entre(LocalDate de, LocalDate ate, ZoneId fuso) {
        garantirNaFaixa(de);
        garantirNaFaixa(ate);
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        }
        return new FaixaDeDatas(
                de == null ? null : de.atStartOfDay(fuso).toInstant(),
                ate == null ? null : ate.plusDays(1).atStartOfDay(fuso).toInstant());
    }

    private static void garantirNaFaixa(LocalDate data) {
        if (data != null && (data.getYear() < ANO_MINIMO || data.getYear() > ANO_MAXIMO)) {
            throw new RegraDeNegocioException(
                    "As datas devem estar entre os anos %d e %d.".formatted(ANO_MINIMO, ANO_MAXIMO));
        }
    }
}
