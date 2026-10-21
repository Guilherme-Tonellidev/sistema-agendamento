package com.guilhermetonelli.agendamento.servico.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ServicoResponse(
        UUID id,
        String nome,
        String descricao,
        int duracaoMinutos,
        BigDecimal preco,
        boolean ativo) {}
