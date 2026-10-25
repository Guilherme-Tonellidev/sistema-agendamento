package com.guilhermetonelli.agendamento.notificacao.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class EnviadorEmLogTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(EnviadorEmLog.class);
    private final ListAppender<ILoggingEvent> registros = new ListAppender<>();

    /** O nível é fixado aqui para o teste não depender da configuração de log que outro teste deixou no processo. */
    @BeforeEach
    void escutar() {
        logger.setLevel(Level.INFO);
        registros.start();
        logger.addAppender(registros);
    }

    @AfterEach
    void parar() {
        logger.detachAppender(registros);
        logger.setLevel(null);
    }

    @Test
    void registraSoODestinatarioEOAssunto() {
        new EnviadorEmLog().enviar(new Email("carlos@exemplo.com", "Horário confirmado: Corte", "Olá, Carlos Mendes! Valor: R$ 45,00", "<p>Olá</p>"));

        assertThat(registros.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .contains("carlos@exemplo.com", "Horário confirmado: Corte")
                .doesNotContain("Carlos Mendes", "45,00");
    }
}
