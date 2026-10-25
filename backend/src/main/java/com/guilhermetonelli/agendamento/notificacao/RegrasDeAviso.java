package com.guilhermetonelli.agendamento.notificacao;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/** Quem agiu já sabe. A exceção é a reserva, em que o cliente recebe o e-mail como comprovante. */
final class RegrasDeAviso {

    private RegrasDeAviso() {}

    /** Cliente de encaixe não tem e-mail, então nunca é avisado. */
    static boolean avisaCliente(EventoDeAgendamento evento) {
        return evento.aviso().emailDoCliente() != null
                && (evento instanceof AgendamentoCriado || evento.aviso().autor() != Papel.CLIENTE);
    }

    static boolean avisaProfissional(EventoDeAgendamento evento) {
        return evento.aviso().autor() != Papel.PROFISSIONAL;
    }
}
