package com.guilhermetonelli.agendamento.notificacao.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;

@Configuration
class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    EnviadorDeEmail enviadorDeEmail(ObjectProvider<JavaMailSenderImpl> sender, AgendamentoProperties propriedades) {
        return escolher(sender.getIfAvailable(), propriedades.notificacoes().remetente());
    }

    /**
     * O Spring cria o enviador SMTP mesmo com {@code MAIL_HOST} vazio (a variável existe, só não tem valor),
     * por isso é o texto do host que diz se há servidor.
     */
    static EnviadorDeEmail escolher(JavaMailSenderImpl sender, String remetente) {
        if (sender != null && StringUtils.hasText(sender.getHost())) {
            return new EnviadorSmtp(sender, remetente);
        }
        log.warn("MAIL_HOST não configurado: os e-mails não serão enviados, só registrados no log (destinatário e assunto).");
        return new EnviadorEmLog();
    }
}
