package com.guilhermetonelli.agendamento.agendamento.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record CancelamentoRequest(@Schema(example = "Imprevisto no trabalho") @Size(max = 200) String motivo) {}
