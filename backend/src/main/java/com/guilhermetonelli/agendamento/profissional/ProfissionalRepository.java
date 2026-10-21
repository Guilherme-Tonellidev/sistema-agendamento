package com.guilhermetonelli.agendamento.profissional;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

interface ProfissionalRepository extends JpaRepository<Profissional, UUID>, JpaSpecificationExecutor<Profissional> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    /** Busca o profissional já com os serviços, numa consulta só (evita o problema N+1). */
    @EntityGraph(attributePaths = "servicos")
    Optional<Profissional> findComServicosById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Profissional> findParaAtualizarById(UUID id);
}
