package com.guilhermetonelli.agendamento.shared.web;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.guilhermetonelli.agendamento.shared.exception.ParametroInvalidoException;

/**
 * Lista de campos pelos quais uma listagem pública pode ser ordenada. Sem ela, {@code ?sort=email} ordenaria a
 * vitrine por um dado que o chamador não pode ler, e a ordem das páginas o entregaria aos poucos.
 */
public final class Ordenacao {

    private Ordenacao() {}

    /** Recusa (HTTP 400) uma ordenação por qualquer campo fora de {@code permitidos}. */
    public static void permitirSomente(Pageable pageable, Set<String> permitidos) {
        for (Sort.Order ordem : pageable.getSort()) {
            if (!permitidos.contains(ordem.getProperty())) {
                throw new ParametroInvalidoException("Não é possível ordenar pelo campo '" + ordem.getProperty() + "'.");
            }
        }
    }
}
