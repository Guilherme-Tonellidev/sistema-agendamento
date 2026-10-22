package com.guilhermetonelli.agendamento.seguranca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Senha inicial de acesso de um profissional, definida pelo administrador. */
public record AcessoRequest(@Schema(example = "senha-inicial-123") @NotBlank @Size(min = 8, max = 64) String senha) {}
