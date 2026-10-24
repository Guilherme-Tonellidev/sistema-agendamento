package com.guilhermetonelli.agendamento.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;

/** Quem reserva os horários. Quem tem conta entra com o e-mail; quem foi encaixado no balcão não tem e-mail. */
@Entity
@Table(name = "cliente")
public class Cliente extends EntidadeBase {

    @Column(nullable = false, length = 120)
    private String nome;

    private String email;

    @Column(length = 20)
    private String telefone;

    protected Cliente() {
        // exigido pelo JPA
    }

    public Cliente(String nome, String email, String telefone) {
        atualizar(nome, email, telefone);
    }

    /** Encaixado pelo profissional: sem e-mail e, portanto, sem conta de acesso. */
    public static Cliente semConta(String nome, String telefone) {
        Cliente cliente = new Cliente();
        cliente.nome = nome.strip();
        cliente.telefone = Textos.limparOpcional(telefone);
        return cliente;
    }

    public void atualizar(String nome, String email, String telefone) {
        this.nome = nome.strip();
        this.email = Textos.normalizarEmail(email);
        this.telefone = Textos.limparOpcional(telefone);
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }
}
