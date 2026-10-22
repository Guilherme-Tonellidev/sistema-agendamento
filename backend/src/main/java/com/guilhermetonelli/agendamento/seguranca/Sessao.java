package com.guilhermetonelli.agendamento.seguranca;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Um refresh token emitido. Cada renovação marca este como usado e cria outro na mesma família
 * (rotação); usar de novo um token já trocado indica roubo, e a família inteira é revogada.
 */
@Entity
@Table(name = "sessao")
class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false, updatable = false)
    private UUID familia;

    @Column(nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiraEm;

    private Instant usadaEm;

    private Instant revogadaEm;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    protected Sessao() {
        // exigido pelo JPA
    }

    Sessao(Usuario usuario, UUID familia, String tokenHash, Instant agora, Instant expiraEm) {
        this.usuario = usuario;
        this.familia = familia;
        this.tokenHash = tokenHash;
        this.criadoEm = agora;
        this.expiraEm = expiraEm;
    }

    /** Válida se nunca foi usada nem revogada e ainda não expirou. */
    boolean valida(Instant agora) {
        return usadaEm == null && revogadaEm == null && agora.isBefore(expiraEm);
    }

    /** Já foi trocada ou revogada: apresentá-la de novo é sinal de token vazado. */
    boolean reutilizada() {
        return usadaEm != null || revogadaEm != null;
    }

    void marcarUsada(Instant agora) {
        this.usadaEm = agora;
    }

    Usuario getUsuario() {
        return usuario;
    }

    UUID getFamilia() {
        return familia;
    }
}
