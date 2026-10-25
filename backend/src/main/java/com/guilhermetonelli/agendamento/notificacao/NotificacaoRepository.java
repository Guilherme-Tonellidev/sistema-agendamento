package com.guilhermetonelli.agendamento.notificacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    List<Notificacao> findTop20ByProfissionalIdOrderByCriadoEmDesc(UUID profissionalId);

    long countByProfissionalIdAndLidaEmIsNull(UUID profissionalId);

    boolean existsByIdAndProfissionalId(UUID id, UUID profissionalId);

    /** Só toca quem ainda não foi lida: marcar de novo não muda a hora da leitura, nem com dois cliques juntos. */
    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora, n.atualizadoEm = :agora where n.id = :id and n.lidaEm is null")
    void marcarComoLida(UUID id, Instant agora);

    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora, n.atualizadoEm = :agora where n.profissionalId = :profissionalId and n.lidaEm is null")
    void marcarTodasComoLidas(UUID profissionalId, Instant agora);
}
