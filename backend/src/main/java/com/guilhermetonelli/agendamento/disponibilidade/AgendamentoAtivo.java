package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.UUID;

/** Reserva que ocupa a agenda do profissional (qualquer status exceto cancelado). */
public record AgendamentoAtivo(UUID id, Periodo horario) {}
