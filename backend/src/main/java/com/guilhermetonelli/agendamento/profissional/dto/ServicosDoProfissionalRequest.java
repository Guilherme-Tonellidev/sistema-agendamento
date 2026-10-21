package com.guilhermetonelli.agendamento.profissional.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

public record ServicosDoProfissionalRequest(
        @Schema(description = "IDs dos serviços que o profissional atende. Lista vazia remove todos.")
        @NotNull Set<@NotNull UUID> servicoIds) {}
