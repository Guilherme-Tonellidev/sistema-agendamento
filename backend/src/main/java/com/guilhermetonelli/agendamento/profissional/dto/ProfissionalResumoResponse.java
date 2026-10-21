package com.guilhermetonelli.agendamento.profissional.dto;

import java.util.UUID;

/**
 * Item da listagem. Não traz os serviços de propósito: carregar coleções em consultas paginadas
 * obrigaria o Hibernate a paginar em memória.
 */
public record ProfissionalResumoResponse(UUID id, String nome, String email, String telefone, boolean ativo) {}
