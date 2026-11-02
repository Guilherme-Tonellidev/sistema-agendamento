package com.guilhermetonelli.agendamento.servico.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Filtros opcionais da listagem de serviços, recebidos pela query string. */
public record ServicoFiltro(
        @Schema(description = "Trecho do nome, sem diferenciar maiúsculas", example = "corte") String nome,
        @Schema(description = "Somente ativos (true) ou inativos (false); quem não é administrador só vê ativos") Boolean ativo) {

    /** O mesmo filtro, mas só com ativos: é o que quem não é administrador enxerga. */
    public ServicoFiltro somenteAtivos() {
        return new ServicoFiltro(nome, true);
    }
}
