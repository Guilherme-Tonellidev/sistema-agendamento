package com.guilhermetonelli.agendamento.agendamento.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReagendamentoRequest(@Schema(example = "2026-10-21T12:00:00Z") @NotNull Instant inicio) {}
