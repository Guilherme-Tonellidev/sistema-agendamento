package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface BloqueioRepository extends JpaRepository<Bloqueio, UUID>, JpaSpecificationExecutor<Bloqueio> {

    Optional<Bloqueio> findByIdAndProfissionalId(UUID id, UUID profissionalId);
}
