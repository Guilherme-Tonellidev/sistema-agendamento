package com.guilhermetonelli.agendamento.agendamento.evento;

import java.time.Instant;
import java.util.UUID;

/** Lembrete ao cliente, enviado pelo sistema: o {@code autor} do {@link Aviso} é nulo. */
public record LembreteDeAgendamento(UUID agendamentoId, UUID profissionalId, UUID clienteId, Instant inicio, Aviso aviso)
        implements EventoDeAgendamento {}
