package com.guilhermetonelli.agendamento.agendamento.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.guilhermetonelli.agendamento.agendamento.StatusAgendamento;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

import io.swagger.v3.oas.annotations.media.Schema;

/** Filtros opcionais da listagem. O período usa datas do fuso do negócio, com limites inclusivos. */
public record AgendamentoFiltro(
        UUID profissionalId,
        UUID clienteId,
        StatusAgendamento status,
        @Schema(example = "2026-10-01") LocalDate de,
        @Schema(example = "2026-10-31") LocalDate ate) {

    /** Cliente só enxerga os próprios agendamentos; profissional, só os da própria agenda. */
    public AgendamentoFiltro visivelPara(UsuarioLogado eu) {
        return switch (eu.papel()) {
            case ADMIN -> this;
            case PROFISSIONAL -> new AgendamentoFiltro(eu.profissionalId(), clienteId, status, de, ate);
            case CLIENTE -> new AgendamentoFiltro(profissionalId, eu.clienteId(), status, de, ate);
        };
    }
}
