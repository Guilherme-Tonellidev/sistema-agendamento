package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.UUID;

/**
 * Publicado dentro da transação que mexe na agenda de um profissional (reserva, cancelamento, bloqueio, jornada...).
 * Depois do commit, os horários livres dele guardados no cache deixam de valer.
 */
public record AgendaAlterada(UUID profissionalId) {}
