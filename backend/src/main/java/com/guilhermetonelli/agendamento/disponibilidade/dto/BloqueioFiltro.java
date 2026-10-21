package com.guilhermetonelli.agendamento.disponibilidade.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Período opcional da listagem de bloqueios, em datas do fuso do negócio (limites inclusivos). */
public record BloqueioFiltro(
        @Schema(example = "2026-10-01") LocalDate de,
        @Schema(example = "2026-10-31") LocalDate ate) {}
