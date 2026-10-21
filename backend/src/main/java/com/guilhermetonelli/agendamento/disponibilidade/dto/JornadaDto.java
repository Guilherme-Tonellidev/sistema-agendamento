package com.guilhermetonelli.agendamento.disponibilidade.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Jornada semanal completa. Usada tanto para definir quanto para consultar. */
public record JornadaDto(
        @Schema(description = "Vários intervalos por dia são permitidos, desde que não se sobreponham. Lista vazia remove a jornada.")
        @NotNull @Size(max = 50) List<@NotNull @Valid IntervaloDeJornadaDto> intervalos) {}
