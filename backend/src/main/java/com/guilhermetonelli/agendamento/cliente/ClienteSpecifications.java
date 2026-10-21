package com.guilhermetonelli.agendamento.cliente;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.guilhermetonelli.agendamento.cliente.dto.ClienteFiltro;
import com.guilhermetonelli.agendamento.shared.persistence.Filtros;

/** Monta a consulta de clientes só com os filtros que foram informados. */
final class ClienteSpecifications {

    private ClienteSpecifications() {}

    static Specification<Cliente> de(ClienteFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();

            if (StringUtils.hasText(filtro.nome())) {
                condicoes.add(Filtros.contemIgnorandoCaixa(cb, root.get("nome"), filtro.nome()));
            }
            if (StringUtils.hasText(filtro.email())) {
                condicoes.add(Filtros.contemIgnorandoCaixa(cb, root.get("email"), filtro.email()));
            }

            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }
}
