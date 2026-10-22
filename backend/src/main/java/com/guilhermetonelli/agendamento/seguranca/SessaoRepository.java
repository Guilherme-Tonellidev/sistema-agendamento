package com.guilhermetonelli.agendamento.seguranca;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    /** Trava a sessão: duas renovações simultâneas com o mesmo token não geram duas sessões novas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Sessao> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE Sessao s SET s.revogadaEm = :agora WHERE s.familia = :familia AND s.revogadaEm IS NULL")
    void revogarFamilia(UUID familia, Instant agora);
}
