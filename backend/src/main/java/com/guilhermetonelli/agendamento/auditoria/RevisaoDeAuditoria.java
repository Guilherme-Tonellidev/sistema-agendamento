package com.guilhermetonelli.agendamento.auditoria;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Uma revisão do Envers: o instante em que um agendamento mudou e quem mudou. Não guarda chave estrangeira
 * para o usuário, porque o token pode trazer um usuário que já não existe.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(AutorDaRevisao.class)
public class RevisaoDeAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private long id;

    /** Vem do relógio do sistema: o Envers não usa o {@code Clock} do projeto. */
    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    private UUID usuarioId;

    @Column(length = 20)
    private String papel;

    /** O {@code profissionalId} ou o {@code clienteId} do autor; nulo para o administrador. */
    private UUID perfilId;

    protected RevisaoDeAuditoria() {
        // exigido pelo JPA
    }

    void definirAutor(UUID usuarioId, String papel, UUID perfilId) {
        this.usuarioId = usuarioId;
        this.papel = papel;
        this.perfilId = perfilId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getPapel() {
        return papel;
    }

    public UUID getPerfilId() {
        return perfilId;
    }
}
