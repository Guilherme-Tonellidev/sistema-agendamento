package com.guilhermetonelli.agendamento.auditoria;

import java.time.Instant;

import com.guilhermetonelli.agendamento.seguranca.Papel;

/**
 * Um passo da vida de um agendamento. {@code de} e {@code para} só vêm no {@code REMARCADO}; {@code motivo}, só no
 * {@code CANCELADO}, e quando houve.
 */
public record EventoDoHistorico(
        Instant quando, Autor autor, TipoDeEvento tipo, Instant de, Instant para, String motivo) {

    /** Quem fez. Sem papel é o próprio sistema, como o job do Quartz. */
    public record Autor(String nome, Papel papel) {}
}
