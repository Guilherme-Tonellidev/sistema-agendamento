package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.List;
import java.util.UUID;

/** Horários já reservados. Implementado pelo módulo de agendamento, para a disponibilidade não depender dele. */
public interface AgendamentosAtivos {

    List<Periodo> doProfissional(UUID profissionalId, Periodo periodo);
}
