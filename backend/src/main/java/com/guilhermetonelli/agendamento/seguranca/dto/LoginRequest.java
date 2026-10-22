package com.guilhermetonelli.agendamento.seguranca.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(example = "carlos@gmail.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "senha-segura-123") @NotBlank @Size(max = 64) String senha) {}
