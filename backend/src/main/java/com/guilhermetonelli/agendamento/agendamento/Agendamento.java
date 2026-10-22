package com.guilhermetonelli.agendamento.agendamento;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.cliente.Cliente;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/** Reserva de um horário: um cliente, um profissional e um serviço, com o preço combinado. */
@Entity
@Table(name = "agendamento")
public class Agendamento extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false, updatable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false, updatable = false)
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.CONFIRMADO;

    private Instant canceladoEm;

    @Column(length = 200)
    private String motivoCancelamento;

    protected Agendamento() {
        // exigido pelo JPA
    }

    public Agendamento(Profissional profissional, Servico servico, Cliente cliente, Periodo horario) {
        this.profissional = profissional;
        this.servico = servico;
        this.cliente = cliente;
        this.preco = servico.getPreco();
        definirHorario(horario);
    }

    /** Só antes do início; com {@code antecedenciaMinima} positiva, só até esse tempo antes do início. */
    public void cancelar(String motivo, Instant agora, Duration antecedenciaMinima) {
        garantirQueAindaNaoComecou(agora, "cancelar");
        garantirAntecedencia(agora, antecedenciaMinima, "O cancelamento");
        this.status = StatusAgendamento.CANCELADO;
        this.canceladoEm = agora.truncatedTo(ChronoUnit.MICROS);
        this.motivoCancelamento = Textos.limparOpcional(motivo);
    }

    /** Mesmo prazo do cancelamento: reagendar também libera o horário combinado. */
    public void garantirQuePodeReagendar(Instant agora, Duration antecedenciaMinima) {
        garantirQueAindaNaoComecou(agora, "reagendar");
        garantirAntecedencia(agora, antecedenciaMinima, "O reagendamento");
    }

    public void reagendar(Periodo horario, Instant agora, Duration antecedenciaMinima) {
        garantirQuePodeReagendar(agora, antecedenciaMinima);
        definirHorario(horario);
    }

    public void concluir(Instant agora) {
        encerrar(StatusAgendamento.CONCLUIDO, agora, "concluir");
    }

    public void registrarFalta(Instant agora) {
        encerrar(StatusAgendamento.NAO_COMPARECEU, agora, "registrar a falta de");
    }

    public Periodo horario() {
        return new Periodo(inicio, fim);
    }

    private void definirHorario(Periodo horario) {
        this.inicio = horario.inicio();
        this.fim = horario.fim();
    }

    private void encerrar(StatusAgendamento novo, Instant agora, String acao) {
        garantirConfirmado(acao);
        if (agora.isBefore(inicio)) {
            throw new RegraDeNegocioException("Só é possível " + acao + " um agendamento depois do início.");
        }
        this.status = novo;
    }

    private void garantirAntecedencia(Instant agora, Duration antecedenciaMinima, String acao) {
        if (agora.plus(antecedenciaMinima).isAfter(inicio)) {
            throw new RegraDeNegocioException(
                    acao + " precisa ser feito com pelo menos " + Textos.duracao(antecedenciaMinima) + " de antecedência.");
        }
    }

    private void garantirQueAindaNaoComecou(Instant agora, String acao) {
        garantirConfirmado(acao);
        if (!agora.isBefore(inicio)) {
            throw new RegraDeNegocioException("Não é possível " + acao + " um agendamento que já começou.");
        }
    }

    private void garantirConfirmado(String acao) {
        if (status != StatusAgendamento.CONFIRMADO) {
            throw new RegraDeNegocioException(
                    "Não é possível " + acao + " um agendamento com status " + status + ".");
        }
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public Servico getServico() {
        return servico;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public Instant getCanceladoEm() {
        return canceladoEm;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }
}
