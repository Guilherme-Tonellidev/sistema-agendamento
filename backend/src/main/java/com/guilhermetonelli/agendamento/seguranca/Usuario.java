package com.guilhermetonelli.agendamento.seguranca;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;

/** Conta de acesso. O cadastro (cliente ou profissional) é referenciado pelo id. */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadeBase {

    @Column(nullable = false, updatable = false)
    private String email;

    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Papel papel;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(updatable = false)
    private UUID profissionalId;

    @Column(updatable = false)
    private UUID clienteId;

    protected Usuario() {
        // exigido pelo JPA
    }

    private Usuario(String email, String senhaHash, Papel papel, UUID profissionalId, UUID clienteId) {
        this.email = Textos.normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.profissionalId = profissionalId;
        this.clienteId = clienteId;
    }

    static Usuario admin(String email, String senhaHash) {
        return new Usuario(email, senhaHash, Papel.ADMIN, null, null);
    }

    static Usuario profissional(String email, String senhaHash, UUID profissionalId) {
        return new Usuario(email, senhaHash, Papel.PROFISSIONAL, profissionalId, null);
    }

    static Usuario cliente(String email, String senhaHash, UUID clienteId) {
        return new Usuario(email, senhaHash, Papel.CLIENTE, null, clienteId);
    }

    void definirSenha(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    UsuarioLogado comoLogado() {
        return new UsuarioLogado(getId(), papel, profissionalId, clienteId);
    }

    public String getEmail() {
        return email;
    }

    String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public UUID getProfissionalId() {
        return profissionalId;
    }

    public UUID getClienteId() {
        return clienteId;
    }
}
