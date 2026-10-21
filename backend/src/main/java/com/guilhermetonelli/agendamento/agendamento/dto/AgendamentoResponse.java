package com.guilhermetonelli.agendamento.agendamento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.guilhermetonelli.agendamento.agendamento.StatusAgendamento;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResumoResponse;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResumoResponse;

public record AgendamentoResponse(
        UUID id,
        Instant inicio,
        Instant fim,
        BigDecimal preco,
        StatusAgendamento status,
        Instant canceladoEm,
        String motivoCancelamento,
        ProfissionalResumoResponse profissional,
        ServicoResumoResponse servico,
        ClienteResponse cliente) {}
