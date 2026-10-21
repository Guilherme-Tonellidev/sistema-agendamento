package com.guilhermetonelli.agendamento.disponibilidade;

import static com.guilhermetonelli.agendamento.shared.domain.FaixaDeDatas.ANO_MAXIMO;
import static com.guilhermetonelli.agendamento.shared.domain.FaixaDeDatas.ANO_MINIMO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.shared.domain.EntidadeBase;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/** Período em que o profissional não atende, como folga, férias ou consulta médica. */
@Entity
@Table(name = "bloqueio")
public class Bloqueio extends EntidadeBase {

    private static final Instant LIMITE_INFERIOR = LocalDate.of(ANO_MINIMO, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant LIMITE_SUPERIOR = LocalDate.of(ANO_MAXIMO + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

    @Column(nullable = false, updatable = false)
    private UUID profissionalId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(length = 200)
    private String motivo;

    protected Bloqueio() {
        // exigido pelo JPA
    }

    /** As datas são truncadas para microssegundos, a precisão do {@code timestamptz}. */
    public Bloqueio(UUID profissionalId, Instant inicio, Instant fim, String motivo) {
        this.profissionalId = profissionalId;
        this.inicio = inicio.truncatedTo(ChronoUnit.MICROS);
        this.fim = fim.truncatedTo(ChronoUnit.MICROS);
        if (!this.inicio.isBefore(this.fim)) {
            throw new RegraDeNegocioException("O início do bloqueio deve ser antes do fim.");
        }
        if (this.inicio.isBefore(LIMITE_INFERIOR) || this.fim.isAfter(LIMITE_SUPERIOR)) {
            throw new RegraDeNegocioException(
                    "O bloqueio deve estar entre os anos %d e %d.".formatted(ANO_MINIMO, ANO_MAXIMO));
        }
        this.motivo = Textos.limparOpcional(motivo);
    }

    public Periodo periodo() {
        return new Periodo(inicio, fim);
    }

    public UUID getProfissionalId() {
        return profissionalId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public String getMotivo() {
        return motivo;
    }
}
