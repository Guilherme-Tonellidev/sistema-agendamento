package com.guilhermetonelli.agendamento.profissional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;

/** Quem presta os serviços. Cada profissional atende um conjunto de serviços do catálogo. */
@Entity
@Table(name = "profissional")
public class Profissional extends EntidadeBase {

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToMany
    @JoinTable(
            name = "profissional_servico",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new HashSet<>();

    protected Profissional() {
        // exigido pelo JPA
    }

    public Profissional(String nome, String email, String telefone) {
        atualizar(nome, email, telefone);
    }

    public void atualizar(String nome, String email, String telefone) {
        this.nome = nome.strip();
        this.email = Textos.normalizarEmail(email);
        this.telefone = Textos.limparOpcional(telefone);
    }

    /** Substitui a lista de serviços atendidos pela nova. */
    public void definirServicos(Collection<Servico> novos) {
        servicos.clear();
        servicos.addAll(novos);
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

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Serviços em ordem alfabética, para a resposta da API ser sempre a mesma. */
    public List<Servico> getServicos() {
        return servicos.stream().sorted(Comparator.comparing(Servico::getNome)).toList();
    }
}
