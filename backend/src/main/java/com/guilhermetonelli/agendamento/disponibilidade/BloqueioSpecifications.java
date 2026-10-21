package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

final class BloqueioSpecifications {

    private BloqueioSpecifications() {}

    /** Bloqueios do profissional que tocam o período; um limite nulo deixa aquele lado em aberto. */
    static Specification<Bloqueio> sobrepostos(UUID profissionalId, Instant inicio, Instant fim) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.equal(root.get("profissionalId"), profissionalId));

            if (inicio != null) {
                condicoes.add(cb.greaterThan(root.get("fim"), inicio));
            }
            if (fim != null) {
                condicoes.add(cb.lessThan(root.get("inicio"), fim));
            }

            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }
}
