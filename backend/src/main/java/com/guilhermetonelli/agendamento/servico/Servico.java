package com.guilhermetonelli.agendamento.servico;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;

/** Um serviço oferecido, como "Corte de cabelo", com duração e preço. */
@Entity
@Table(name = "servico")
public class Servico extends EntidadeBase {

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false)
    private int duracaoMinutos;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Servico() {
        // exigido pelo JPA
    }

    public Servico(String nome, String descricao, int duracaoMinutos, BigDecimal preco) {
        atualizar(nome, descricao, duracaoMinutos, preco);
    }

    public void atualizar(String nome, String descricao, int duracaoMinutos, BigDecimal preco) {
        this.nome = nome.strip();
        this.descricao = Textos.limparOpcional(descricao);
        this.duracaoMinutos = duracaoMinutos;
        this.preco = preco;
    }

    public void ativar() {
        this.ativo = true;
    }

    public void inativar() {
        this.ativo = false;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
