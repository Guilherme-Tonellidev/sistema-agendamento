package com.guilhermetonelli.agendamento.agendamento.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Quem chegou sem reserva: o profissional encaixa na agenda com o nome e, se quiser, o telefone. */
public record EncaixeRequest(
        @NotNull UUID profissionalId,
        @NotNull UUID servicoId,

        @Schema(description = "Um dos horários livres para encaixe", example = "2026-10-21T18:15:00Z")
        @NotNull Instant inicio,

        @Schema(example = "Júlia Prado")
        @NotBlank @Size(max = 120) String nomeDoCliente,

        @Schema(description = "Opcional. Só dígitos, com DDD", example = "14998123344")
        @Pattern(regexp = "\\d{10,11}", message = "deve ter 10 ou 11 dígitos, sem espaços ou símbolos")
        String telefone) {}
