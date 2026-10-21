package com.guilhermetonelli.agendamento.agendamento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.disponibilidade.AgendamentoAtivo;
import com.guilhermetonelli.agendamento.disponibilidade.AgendamentosAtivos;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;

@Component
class AgendamentosAtivosJpa implements AgendamentosAtivos {

    private final AgendamentoRepository repository;

    AgendamentosAtivosJpa(AgendamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AgendamentoAtivo> doProfissional(UUID profissionalId, Periodo periodo) {
        return repository.findAll(AgendamentoSpecifications.ativosDoProfissional(profissionalId, periodo), Sort.by("inicio"))
                .stream()
                .map(agendamento -> new AgendamentoAtivo(agendamento.getId(), agendamento.horario()))
                .toList();
    }
}
