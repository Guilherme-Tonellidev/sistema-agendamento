package com.guilhermetonelli.agendamento.servico;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ServicoRepository extends JpaRepository<Servico, UUID>, JpaSpecificationExecutor<Servico> {

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
