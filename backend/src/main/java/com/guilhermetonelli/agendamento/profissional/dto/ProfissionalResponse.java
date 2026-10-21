package com.guilhermetonelli.agendamento.profissional.dto;

import java.util.List;
import java.util.UUID;

import com.guilhermetonelli.agendamento.servico.dto.ServicoResumoResponse;

/** Detalhe do profissional, com os serviços que ele atende. */
public record ProfissionalResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        boolean ativo,
        List<ServicoResumoResponse> servicos) {}
