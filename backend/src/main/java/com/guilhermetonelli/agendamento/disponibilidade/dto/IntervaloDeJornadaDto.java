package com.guilhermetonelli.agendamento.disponibilidade.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Um trecho da jornada, em horário local do negócio. */
public record IntervaloDeJornadaDto(
        @Schema(example = "MONDAY") @NotNull DayOfWeek diaSemana,
        @Schema(type = "string", example = "08:00") @NotNull LocalTime inicio,
        @Schema(type = "string", example = "12:00") @NotNull LocalTime fim) {}
