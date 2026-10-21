package com.guilhermetonelli.agendamento.shared.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Liga a auditoria (criadoEm e atualizadoEm) usando o mesmo {@link Clock} da aplicação. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "provedorDeDataHora")
class JpaConfig {

    @Bean
    DateTimeProvider provedorDeDataHora(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
