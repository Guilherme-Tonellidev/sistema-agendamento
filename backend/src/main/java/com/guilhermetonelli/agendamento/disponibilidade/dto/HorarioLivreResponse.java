package com.guilhermetonelli.agendamento.disponibilidade.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record HorarioLivreResponse(
        @Schema(example = "2026-10-21T11:00:00Z") Instant inicio,
        @Schema(example = "2026-10-21T11:30:00Z") Instant fim) {}
