package com.guilhermetonelli.agendamento.profissional.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Filtros opcionais da listagem de profissionais, recebidos pela query string. */
public record ProfissionalFiltro(
        @Schema(description = "Trecho do nome, sem diferenciar maiúsculas", example = "ana") String nome,
        @Schema(description = "Somente ativos (true) ou inativos (false)") Boolean ativo,
        @Schema(description = "Somente quem atende este serviço") UUID servicoId) {}
