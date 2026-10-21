package com.guilhermetonelli.agendamento.agendamento.evento;

import java.time.Instant;
import java.util.UUID;

public record AgendamentoCriado(UUID agendamentoId, UUID profissionalId, UUID clienteId, Instant inicio, Instant fim)
        implements EventoDeAgendamento {}
