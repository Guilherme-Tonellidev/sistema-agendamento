package com.guilhermetonelli.agendamento.notificacao;

import java.time.Instant;
import java.time.ZoneId;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/** A frase do sino do barbeiro, já no horário da barbearia: quem fez o quê e para quando. */
final class TextosDeNotificacao {

    private TextosDeNotificacao() {}

    static String de(EventoDeAgendamento evento, ZoneId fuso) {
        Aviso aviso = evento.aviso();
        // Quando a administração age, o cliente deixa de ser o sujeito e vira complemento do serviço.
        boolean administracao = aviso.autor() == Papel.ADMIN;
        String quem = administracao ? "A administração" : aviso.cliente();
        String oQue = administracao ? "o %s de %s".formatted(aviso.servico(), aviso.cliente()) : aviso.servico();
        return switch (evento) {
            case AgendamentoCriado e -> "%s reservou %s para %s.".formatted(quem, oQue, quando(e.inicio(), fuso));
            case AgendamentoReagendado e -> "%s remarcou %s de %s para %s."
                    .formatted(quem, oQue, quando(e.inicioAnterior(), fuso), quando(e.inicio(), fuso));
            case AgendamentoCancelado e -> "%s cancelou %s de %s.%s".formatted(quem, oQue, quando(e.inicio(), fuso), motivo(e.motivo()));
        };
    }

    private static String quando(Instant instante, ZoneId fuso) {
        return Quando.formatar(instante, fuso);
    }

    private static String motivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return "";
        }
        String texto = motivo.strip();
        return " Motivo: " + texto + (texto.matches(".*[.!?]") ? "" : ".");
    }
}
