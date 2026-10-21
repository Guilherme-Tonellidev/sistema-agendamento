package com.guilhermetonelli.agendamento.agendamento.evento;

import java.time.Instant;
import java.util.UUID;

public record AgendamentoReagendado(
        UUID agendamentoId, UUID profissionalId, UUID clienteId, Instant inicioAnterior, Instant inicio, Instant fim)
        implements EventoDeAgendamento {}
