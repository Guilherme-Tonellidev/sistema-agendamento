package com.guilhermetonelli.agendamento.disponibilidade.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record BloqueioRequest(
        @Schema(example = "2026-10-21T15:00:00Z") @NotNull Instant inicio,
        @Schema(example = "2026-10-21T18:00:00Z") @NotNull Instant fim,
        @Schema(example = "Consulta médica") @Size(max = 200) String motivo) {}
