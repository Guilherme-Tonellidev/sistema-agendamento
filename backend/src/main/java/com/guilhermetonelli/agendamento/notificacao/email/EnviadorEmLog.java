package com.guilhermetonelli.agendamento.notificacao.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sem servidor de e-mail configurado: o corpo (nome, valor) não vai para o log, só quem receberia e o assunto. */
final class EnviadorEmLog implements EnviadorDeEmail {

    private static final Logger log = LoggerFactory.getLogger(EnviadorEmLog.class);

    @Override
    public void enviar(Email email) {
        log.info("E-mail não enviado (sem servidor SMTP): para={}, assunto={}", email.para(), email.assunto());
    }
}
