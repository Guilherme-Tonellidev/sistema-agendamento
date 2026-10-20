package com.guilhermetonelli.agendamento.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação, sempre em UTC.
 *
 * <p>Toda regra que depende da hora atual recebe este {@link Clock} por injeção, em vez de chamar
 * {@code Instant.now()} direto. Assim os testes podem fixar a data e a hora com
 * {@code Clock.fixed(...)}.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
