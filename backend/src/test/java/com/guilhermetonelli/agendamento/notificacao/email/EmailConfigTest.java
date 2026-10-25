package com.guilhermetonelli.agendamento.notificacao.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Servidor de e-mail só conta como configurado se o host tem texto: uma variável de ambiente vazia não conta. */
class EmailConfigTest {

    private static final String REMETENTE = "Barbearia <b@exemplo.com>";

    @Test
    void comHostUsaOSmtp() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.exemplo.com");

        assertThat(EmailConfig.escolher(sender, REMETENTE)).isInstanceOf(EnviadorSmtp.class);
    }

    @Test
    void comHostVazioOuEmBrancoSoRegistraNoLog() {
        JavaMailSenderImpl vazio = new JavaMailSenderImpl();
        vazio.setHost("");
        JavaMailSenderImpl embranco = new JavaMailSenderImpl();
        embranco.setHost("  ");

        assertThat(EmailConfig.escolher(vazio, REMETENTE)).isInstanceOf(EnviadorEmLog.class);
        assertThat(EmailConfig.escolher(embranco, REMETENTE)).isInstanceOf(EnviadorEmLog.class);
    }

    @Test
    void semNenhumEnviadorConfiguradoSoRegistraNoLog() {
        assertThat(EmailConfig.escolher(null, REMETENTE)).isInstanceOf(EnviadorEmLog.class);
    }
}
