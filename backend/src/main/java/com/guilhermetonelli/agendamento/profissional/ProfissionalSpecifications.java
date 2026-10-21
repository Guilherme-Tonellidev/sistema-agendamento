package com.guilhermetonelli.agendamento.profissional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalFiltro;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.shared.persistence.Filtros;

/** Monta a consulta de profissionais só com os filtros que foram informados. */
final class ProfissionalSpecifications {

    private ProfissionalSpecifications() {}

    static Specification<Profissional> de(ProfissionalFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();

            if (StringUtils.hasText(filtro.nome())) {
                condicoes.add(Filtros.contemIgnorandoCaixa(cb, root.get("nome"), filtro.nome()));
            }
            if (filtro.ativo() != null) {
                condicoes.add(cb.equal(root.get("ativo"), filtro.ativo()));
            }
            if (filtro.servicoId() != null) {
                // Subconsulta em vez de JOIN direto: não duplica linhas e não atrapalha a paginação.
                Subquery<UUID> queAtendem = query.subquery(UUID.class);
                Root<Profissional> profissional = queAtendem.from(Profissional.class);
                Join<Profissional, Servico> servico = profissional.join("servicos");
                queAtendem.select(profissional.get("id"))
                        .where(cb.equal(servico.get("id"), filtro.servicoId()));
                condicoes.add(root.get("id").in(queAtendem));
            }

            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }
}
