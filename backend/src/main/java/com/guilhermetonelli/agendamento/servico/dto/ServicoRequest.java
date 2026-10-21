package com.guilhermetonelli.agendamento.servico.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record ServicoRequest(
        @Schema(example = "Corte de cabelo")
        @NotBlank @Size(max = 100) String nome,

        @Schema(example = "Corte masculino com máquina e tesoura")
        @Size(max = 500) String descricao,

        @Schema(description = "Duração em minutos, de 5 a 480", example = "30")
        @NotNull @Min(5) @Max(480) Integer duracaoMinutos,

        @Schema(example = "45.00")
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal preco) {}
