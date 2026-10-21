package com.guilhermetonelli.agendamento.profissional;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProfissionalRepository extends JpaRepository<Profissional, UUID>, JpaSpecificationExecutor<Profissional> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    /** Busca o profissional já com os serviços, numa consulta só (evita o problema N+1). */
    @EntityGraph(attributePaths = "servicos")
    Optional<Profissional> findComServicosById(UUID id);
}
