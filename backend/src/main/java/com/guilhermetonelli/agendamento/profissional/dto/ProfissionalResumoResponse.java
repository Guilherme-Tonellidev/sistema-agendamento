package com.guilhermetonelli.agendamento.profissional.dto;

import java.util.UUID;

/**
 * Item da listagem. Não traz os serviços de propósito: carregar coleções em consultas paginadas
 * obrigaria o Hibernate a paginar em memória.
 */
public record ProfissionalResumoResponse(UUID id, String nome, String email, String telefone, boolean ativo) {

    /** Versão pública: o e-mail é o login do profissional e não deve ficar exposto na vitrine. */
    public ProfissionalResumoResponse semContato() {
        return new ProfissionalResumoResponse(id, nome, null, null, ativo);
    }
}
