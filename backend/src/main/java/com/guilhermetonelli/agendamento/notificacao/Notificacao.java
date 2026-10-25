package com.guilhermetonelli.agendamento.notificacao;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;

/** Aviso para o sino de um barbeiro. Depois de criada, só muda ao ser lida. */
@Entity
@Table(name = "notificacao")
class Notificacao extends EntidadeBase {

    @Column(nullable = false, updatable = false)
    private UUID profissionalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoDeNotificacao tipo;

    @Column(nullable = false, updatable = false, length = 600)
    private String texto;

    @Column(nullable = false, updatable = false)
    private Instant inicio;

    private Instant lidaEm;

    protected Notificacao() {
        // exigido pelo JPA
    }

    /** O início é truncado para microssegundos, a precisão do {@code timestamptz}. */
    Notificacao(UUID profissionalId, TipoDeNotificacao tipo, String texto, Instant inicio) {
        this.profissionalId = profissionalId;
        this.tipo = tipo;
        this.texto = texto;
        this.inicio = inicio.truncatedTo(ChronoUnit.MICROS);
    }

    UUID getProfissionalId() {
        return profissionalId;
    }

    TipoDeNotificacao getTipo() {
        return tipo;
    }

    String getTexto() {
        return texto;
    }

    Instant getInicio() {
        return inicio;
    }

    Instant getLidaEm() {
        return lidaEm;
    }
}
