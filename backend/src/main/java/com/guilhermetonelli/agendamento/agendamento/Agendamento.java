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

import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.cliente.Cliente;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.shared.domain.Antecedencia;
import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/**
 * Reserva de um horário: um cliente, um profissional e um serviço, com o preço combinado.
 *
 * <p>É a única entidade com histórico (Envers). Profissional, serviço e cliente entram na revisão só pelo id.
 */
@Entity
@Table(name = "agendamento")
@Audited
public class Agendamento extends EntidadeBase {

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false, updatable = false)
    private Profissional profissional;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false, updatable = false)
    private Servico servico;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
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

    /**
     * Quando o lembrete deve sair; nulo se não há lembrete a mandar. Fora do histórico: o Quartz mexe nele sem
     * usuário, e o Envers só cria revisão quando um campo auditado muda.
     */
    @NotAudited
    private Instant lembrarEm;

    /** Quando o broker confirmou o lembrete; só o job grava, direto no banco. Fora do histórico, como {@code lembrarEm}. */
    @NotAudited
    private Instant lembreteEnviadoEm;

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

    /** Só antes do início, e com a antecedência pedida; quem é livre cancela até depois de começar. */
    public void cancelar(String motivo, Instant agora, Antecedencia antecedencia) {
        garantirPrazo(agora, antecedencia, "cancelar", "O cancelamento");
        this.status = StatusAgendamento.CANCELADO;
        this.canceladoEm = agora.truncatedTo(ChronoUnit.MICROS);
        this.motivoCancelamento = Textos.limparOpcional(motivo);
    }

    /** Mesmo prazo do cancelamento: reagendar também libera o horário combinado. */
    public void garantirQuePodeReagendar(Instant agora, Antecedencia antecedencia) {
        garantirPrazo(agora, antecedencia, "reagendar", "O reagendamento");
    }

    public void reagendar(Periodo horario, Instant agora, Antecedencia antecedencia) {
        garantirQuePodeReagendar(agora, antecedencia);
        definirHorario(horario);
    }

    public void concluir(Instant agora) {
        encerrar(StatusAgendamento.CONCLUIDO, agora, "concluir");
    }

    public void registrarFalta(Instant agora) {
        encerrar(StatusAgendamento.NAO_COMPARECEU, agora, "registrar a falta de");
    }

    /** Vale para o horário atual: um lembrete já enviado de um horário antigo não conta para o novo. */
    public void programarLembrete(Instant lembrarEm) {
        this.lembrarEm = lembrarEm;
        this.lembreteEnviadoEm = null;
    }

    public Periodo horario() {
        return new Periodo(inicio, fim);
    }

    /** O preço é o gravado no agendamento, não o atual do serviço. */
    public Aviso aviso(Papel autor) {
        return new Aviso(autor, cliente.getNome(), cliente.getEmail(), profissional.getNome(), servico.getNome(), preco);
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

    /** O agendamento precisa estar confirmado para todos; o prazo só vale para quem não é livre. */
    private void garantirPrazo(Instant agora, Antecedencia antecedencia, String acao, String nomeDaAcao) {
        garantirConfirmado(acao);
        if (!antecedencia.livre()) {
            garantirQueAindaNaoComecou(agora, acao);
            garantirAntecedencia(agora, antecedencia.minima(), nomeDaAcao);
        }
    }

    private void garantirQueAindaNaoComecou(Instant agora, String acao) {
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

    public Instant getLembrarEm() {
        return lembrarEm;
    }

    public Instant getLembreteEnviadoEm() {
        return lembreteEnviadoEm;
    }
}
