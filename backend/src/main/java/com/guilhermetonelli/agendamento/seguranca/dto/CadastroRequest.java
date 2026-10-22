package com.guilhermetonelli.agendamento.seguranca.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Autocadastro de cliente: cria o cadastro e a conta de acesso juntos. */
public record CadastroRequest(
        @Schema(example = "Carlos Lima") @NotBlank @Size(max = 120) String nome,
        @Schema(example = "carlos@gmail.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "Opcional. Só dígitos, com DDD", example = "14988887777")
        @Pattern(regexp = "\\d{10,11}", message = "deve ter 10 ou 11 dígitos, sem espaços ou símbolos")
        String telefone,
        @Schema(example = "senha-segura-123") @NotBlank @Size(min = 8, max = 64) String senha) {}
