package com.guilhermetonelli.agendamento.agendamento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoFiltro;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;
import com.guilhermetonelli.agendamento.shared.domain.FaixaDeDatas;

final class AgendamentoSpecifications {

    private AgendamentoSpecifications() {}

    /** Listagem: só os filtros informados; o período pega os agendamentos que tocam a faixa de datas. */
    static Specification<Agendamento> de(AgendamentoFiltro filtro, FaixaDeDatas faixa) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();

            if (filtro.profissionalId() != null) {
                condicoes.add(cb.equal(root.get("profissional").get("id"), filtro.profissionalId()));
            }
            if (filtro.clienteId() != null) {
                condicoes.add(cb.equal(root.get("cliente").get("id"), filtro.clienteId()));
            }
            if (filtro.status() != null) {
                condicoes.add(cb.equal(root.get("status"), filtro.status()));
            }
            if (faixa.inicio() != null) {
                condicoes.add(cb.greaterThan(root.<Instant>get("fim"), faixa.inicio()));
            }
            if (faixa.fim() != null) {
                condicoes.add(cb.lessThan(root.<Instant>get("inicio"), faixa.fim()));
            }

            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    /** Agendamentos não cancelados do profissional que se sobrepõem ao período (mesma regra da constraint). */
    static Specification<Agendamento> ativosDoProfissional(UUID profissionalId, Periodo periodo) {
        return ativosSobrepostos("profissional", profissionalId, periodo);
    }

    static Specification<Agendamento> ativosDoCliente(UUID clienteId, Periodo periodo) {
        return ativosSobrepostos("cliente", clienteId, periodo);
    }

    static Specification<Agendamento> exceto(UUID id) {
        return (root, query, cb) -> cb.notEqual(root.get("id"), id);
    }

    private static Specification<Agendamento> ativosSobrepostos(String participante, UUID id, Periodo periodo) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get(participante).get("id"), id),
                cb.notEqual(root.get("status"), StatusAgendamento.CANCELADO),
                cb.lessThan(root.<Instant>get("inicio"), periodo.fim()),
                cb.greaterThan(root.<Instant>get("fim"), periodo.inicio()));
    }
}
