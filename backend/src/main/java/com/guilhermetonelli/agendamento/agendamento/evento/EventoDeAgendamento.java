package com.guilhermetonelli.agendamento.agendamento.evento;

import java.util.UUID;

/** Mudança na agenda, publicada como evento do Spring para quem precisar reagir (notificações, lembretes, cache). */
public sealed interface EventoDeAgendamento permits AgendamentoCriado, AgendamentoCancelado, AgendamentoReagendado {

    UUID agendamentoId();

    UUID profissionalId();

    UUID clienteId();
}
