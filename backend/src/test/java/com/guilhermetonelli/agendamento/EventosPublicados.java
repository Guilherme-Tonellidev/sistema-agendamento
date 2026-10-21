package com.guilhermetonelli.agendamento;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;

import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;

/** Guarda os eventos de agendamento publicados pela aplicação, inclusive os das threads das requisições. */
public class EventosPublicados {

    private final List<EventoDeAgendamento> eventos = new CopyOnWriteArrayList<>();

    @EventListener
    void registrar(EventoDeAgendamento evento) {
        eventos.add(evento);
    }

    public List<EventoDeAgendamento> todos() {
        return List.copyOf(eventos);
    }

    public void limpar() {
        eventos.clear();
    }
}
