package com.guilhermetonelli.agendamento.servico.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Filtros opcionais da listagem de serviços, recebidos pela query string. */
public record ServicoFiltro(
        @Schema(description = "Trecho do nome, sem diferenciar maiúsculas", example = "corte") String nome,
        @Schema(description = "Somente ativos (true) ou inativos (false)") Boolean ativo) {}
