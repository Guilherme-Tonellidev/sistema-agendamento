package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.List;
import java.util.UUID;

/** Horários já reservados. Implementado pelo módulo de agendamento, para a disponibilidade não depender dele. */
public interface AgendamentosAtivos {

    /** Reservas do profissional que se sobrepõem ao período, em ordem cronológica. */
    List<AgendamentoAtivo> doProfissional(UUID profissionalId, Periodo periodo);
}
