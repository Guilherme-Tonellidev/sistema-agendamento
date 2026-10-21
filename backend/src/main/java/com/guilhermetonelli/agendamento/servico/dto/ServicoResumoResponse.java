package com.guilhermetonelli.agendamento.servico.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Versão curta do serviço, usada dentro de outras respostas (como a do profissional). */
public record ServicoResumoResponse(UUID id, String nome, int duracaoMinutos, BigDecimal preco) {}
