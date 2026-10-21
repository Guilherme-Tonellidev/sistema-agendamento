package com.guilhermetonelli.agendamento.shared.persistence;

import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/** Predicados reutilizáveis para as consultas com filtros dinâmicos (JPA Specifications). */
public final class Filtros {

    private static final char ESCAPE = '\\';

    private Filtros() {}

    /**
     * Busca por trecho de texto, sem diferenciar maiúsculas de minúsculas.
     *
     * <p>Os caracteres curinga do LIKE ({@code %} e {@code _}) digitados pelo usuário são escapados,
     * para que uma busca por "50%" procure exatamente esse texto.
     */
    public static Predicate contemIgnorandoCaixa(CriteriaBuilder cb, Expression<String> campo, String termo) {
        String escapado = termo.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(campo), "%" + escapado + "%", ESCAPE);
    }
}
