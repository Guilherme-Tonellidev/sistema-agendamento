package com.guilhermetonelli.agendamento.cliente.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Filtros opcionais da listagem de clientes, recebidos pela query string. */
public record ClienteFiltro(
        @Schema(description = "Trecho do nome, sem diferenciar maiúsculas", example = "carlos") String nome,
        @Schema(description = "Trecho do e-mail", example = "gmail") String email) {}
