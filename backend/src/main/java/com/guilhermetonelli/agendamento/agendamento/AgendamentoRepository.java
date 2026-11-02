package com.guilhermetonelli.agendamento.agendamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface AgendamentoRepository extends JpaRepository<Agendamento, UUID>, JpaSpecificationExecutor<Agendamento> {

    /** Já traz profissional, serviço e cliente na mesma consulta (evita o problema N+1 na resposta). */
    @EntityGraph(attributePaths = {"profissional", "servico", "cliente"})
    Optional<Agendamento> findDetalhadoById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"profissional", "servico", "cliente"})
    Page<Agendamento> findAll(Specification<Agendamento> specification, Pageable pageable);

    /** Os lembretes que já deviam ter saído e cujo horário ainda não começou, do mais antigo ao mais novo. */
    @EntityGraph(attributePaths = {"profissional", "servico", "cliente"})
    @Query("""
            select a from Agendamento a
            where a.status = com.guilhermetonelli.agendamento.agendamento.StatusAgendamento.CONFIRMADO
              and a.lembreteEnviadoEm is null and a.lembrarEm <= :agora and a.inicio > :agora
            order by a.lembrarEm, a.id""")
    List<Agendamento> lembretesVencidos(Instant agora, Limit limite);

    /** Lembrete que não saiu até o horário começar não vale mais; limpar mantém a varredura nos que importam. */
    @Modifying
    @Transactional
    @Query("""
            update Agendamento a set a.lembrarEm = null
            where a.status = com.guilhermetonelli.agendamento.agendamento.StatusAgendamento.CONFIRMADO
              and a.lembreteEnviadoEm is null and a.lembrarEm is not null and a.inicio <= :agora""")
    int descartarLembretesVencidos(Instant agora);

    /**
     * Direto no banco, sem versão nem data de alteração: marcar o envio não pode derrubar por conflito uma
     * remarcação ou um cancelamento em andamento. Só vale para o horário que foi lembrado.
     */
    @Modifying
    @Transactional
    @Query("""
            update Agendamento a set a.lembreteEnviadoEm = :agora
            where a.id = :id and a.lembreteEnviadoEm is null and a.lembrarEm = :lembrarEm""")
    int marcarLembreteEnviado(UUID id, Instant lembrarEm, Instant agora);

    boolean existsByIdAndStatusAndInicio(UUID id, StatusAgendamento status, Instant inicio);
}
