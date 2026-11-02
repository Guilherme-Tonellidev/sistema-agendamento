package com.guilhermetonelli.agendamento.notificacao;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.agendamento.evento.LembreteDeAgendamento;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/**
 * Quem agiu já sabe. As exceções: a reserva, em que o cliente recebe o e-mail como comprovante, e o lembrete,
 * que vem do sistema e é só do cliente.
 */
final class RegrasDeAviso {

    private RegrasDeAviso() {}

    /** Cliente de encaixe não tem e-mail, então nunca é avisado. */
    static boolean avisaCliente(EventoDeAgendamento evento) {
        return evento.aviso().emailDoCliente() != null
                && (evento instanceof AgendamentoCriado
                        || evento instanceof LembreteDeAgendamento
                        || evento.aviso().autor() != Papel.CLIENTE);
    }

    static boolean avisaProfissional(EventoDeAgendamento evento) {
        return !(evento instanceof LembreteDeAgendamento) && evento.aviso().autor() != Papel.PROFISSIONAL;
    }
}
