package com.guilhermetonelli.agendamento.agendamento.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

public record AgendamentoRequest(
        @NotNull UUID profissionalId,
        @NotNull UUID servicoId,
        @NotNull UUID clienteId,
        @Schema(description = "Um dos horários livres do profissional", example = "2026-10-21T11:00:00Z")
        @NotNull Instant inicio) {}
