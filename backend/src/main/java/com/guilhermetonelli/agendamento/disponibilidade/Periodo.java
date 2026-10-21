package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Intervalo semiaberto [inicio, fim): um período pode começar exatamente onde o anterior termina. */
public record Periodo(Instant inicio, Instant fim) {

    public static Periodo doDia(LocalDate data, ZoneId fuso) {
        return new Periodo(data.atStartOfDay(fuso).toInstant(), data.plusDays(1).atStartOfDay(fuso).toInstant());
    }

    public boolean sobrepoe(Periodo outro) {
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }
}
