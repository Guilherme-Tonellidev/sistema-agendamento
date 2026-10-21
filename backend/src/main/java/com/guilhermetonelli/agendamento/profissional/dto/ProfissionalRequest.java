package com.guilhermetonelli.agendamento.profissional.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfissionalRequest(
        @Schema(example = "Ana Souza")
        @NotBlank @Size(max = 120) String nome,

        @Schema(example = "ana@barbearia.com")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "Opcional. Só dígitos, com DDD", example = "14999998888")
        @Pattern(regexp = "\\d{10,11}", message = "deve ter 10 ou 11 dígitos, sem espaços ou símbolos")
        String telefone) {

    /** Tira os espaços das pontas antes da validação, senão o {@code @Email} recusa " ana@x.com ". */
    public ProfissionalRequest {
        email = email == null ? null : email.strip();
    }
}
