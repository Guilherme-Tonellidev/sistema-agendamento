package com.guilhermetonelli.agendamento.servico;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.guilhermetonelli.agendamento.servico.dto.ServicoFiltro;
import com.guilhermetonelli.agendamento.shared.persistence.Filtros;

/** Monta a consulta de serviços só com os filtros que foram informados. */
final class ServicoSpecifications {

    private ServicoSpecifications() {}

    static Specification<Servico> de(ServicoFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();

            if (StringUtils.hasText(filtro.nome())) {
                condicoes.add(Filtros.contemIgnorandoCaixa(cb, root.get("nome"), filtro.nome()));
            }
            if (filtro.ativo() != null) {
                condicoes.add(cb.equal(root.get("ativo"), filtro.ativo()));
            }

            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }
}
