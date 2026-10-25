package com.guilhermetonelli.agendamento.agendamento.evento;

import java.time.Instant;
import java.util.UUID;

public record AgendamentoCancelado(UUID agendamentoId, UUID profissionalId, UUID clienteId, Instant inicio, String motivo, Aviso aviso)
        implements EventoDeAgendamento {}
