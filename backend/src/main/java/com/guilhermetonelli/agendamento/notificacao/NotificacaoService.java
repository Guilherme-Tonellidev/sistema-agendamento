package com.guilhermetonelli.agendamento.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.notificacao.dto.NotificacaoResponse;
import com.guilhermetonelli.agendamento.notificacao.dto.NotificacoesResponse;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
class NotificacaoService {

    private final NotificacaoRepository repository;
    private final Clock clock;

    NotificacaoService(NotificacaoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    void registrar(UUID profissionalId, TipoDeNotificacao tipo, String texto, Instant inicio) {
        repository.save(new Notificacao(profissionalId, tipo, texto, inicio));
    }

    NotificacoesResponse listar(UUID profissionalId) {
        var recentes = repository.findTop20ByProfissionalIdOrderByCriadoEmDesc(profissionalId).stream()
                .map(n -> new NotificacaoResponse(n.getId(), n.getTipo(), n.getTexto(), n.getInicio(), n.getCriadoEm(), n.getLidaEm() != null))
                .toList();
        return new NotificacoesResponse(repository.countByProfissionalIdAndLidaEmIsNull(profissionalId), recentes);
    }

    /** Notificação de outro barbeiro responde como se não existisse, para não revelar que existe. */
    @Transactional
    void marcarComoLida(UUID profissionalId, UUID id) {
        if (!repository.existsByIdAndProfissionalId(id, profissionalId)) {
            throw new RecursoNaoEncontradoException("Notificação", id);
        }
        repository.marcarComoLida(id, clock.instant());
    }

    @Transactional
    void marcarTodasComoLidas(UUID profissionalId) {
        repository.marcarTodasComoLidas(profissionalId, clock.instant());
    }
}
