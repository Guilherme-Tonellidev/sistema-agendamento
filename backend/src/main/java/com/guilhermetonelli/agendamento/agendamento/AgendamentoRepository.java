package com.guilhermetonelli.agendamento.agendamento;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AgendamentoRepository extends JpaRepository<Agendamento, UUID>, JpaSpecificationExecutor<Agendamento> {

    /** Já traz profissional, serviço e cliente na mesma consulta (evita o problema N+1 na resposta). */
    @EntityGraph(attributePaths = {"profissional", "servico", "cliente"})
    Optional<Agendamento> findDetalhadoById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"profissional", "servico", "cliente"})
    Page<Agendamento> findAll(Specification<Agendamento> specification, Pageable pageable);
}
