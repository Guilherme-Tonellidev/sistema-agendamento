package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/**
 * Um trecho da jornada semanal, em horário local do negócio (ex.: segunda, 08:00–12:00).
 *
 * <p>A jornada é sempre substituída inteira, por isso não tem versão nem auditoria.
 */
@Entity
@Table(name = "jornada")
public class IntervaloDeJornada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID profissionalId;

    @Convert(converter = DiaDaSemanaConverter.class)
    @Column(nullable = false)
    private DayOfWeek diaSemana;

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fim;

    protected IntervaloDeJornada() {
        // exigido pelo JPA
    }

    public IntervaloDeJornada(UUID profissionalId, DayOfWeek diaSemana, LocalTime inicio, LocalTime fim) {
        if (!inicio.isBefore(fim)) {
            throw new RegraDeNegocioException("O início do intervalo deve ser antes do fim (" + inicio + "–" + fim + ").");
        }
        this.profissionalId = profissionalId;
        this.diaSemana = diaSemana;
        this.inicio = inicio;
        this.fim = fim;
    }

    boolean sobrepoe(IntervaloDeJornada outro) {
        return diaSemana == outro.diaSemana && inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    Periodo naData(LocalDate data, ZoneId fuso) {
        return new Periodo(
                ZonedDateTime.of(data, inicio, fuso).toInstant(),
                ZonedDateTime.of(data, fim, fuso).toInstant());
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFim() {
        return fim;
    }
}
