package com.guilhermetonelli.agendamento.shared.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base das entidades: identificador UUID, controle de concorrência otimista e datas de auditoria.
 *
 * <p>O UUID evita que alguém descubra quantos registros existem ou tente IDs em sequência na API.
 * A versão faz uma atualização sobre dados desatualizados falhar, em vez de sobrescrever
 * silenciosamente a alteração de outra pessoa.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long versao;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant atualizadoEm;

    public UUID getId() {
        return id;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    /**
     * Igualdade pelo identificador. Entidades ainda não salvas (sem id) só são iguais a si mesmas,
     * e a comparação de classes considera os proxies do Hibernate.
     */
    @Override
    public final boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EntidadeBase entidade) || Hibernate.getClass(this) != Hibernate.getClass(outro)) {
            return false;
        }
        return id != null && id.equals(entidade.getId());
    }

    /** Constante por classe, para o hash não mudar quando o id é gerado no momento de salvar. */
    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
