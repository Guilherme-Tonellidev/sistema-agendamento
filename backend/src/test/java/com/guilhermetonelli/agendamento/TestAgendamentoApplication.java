package com.guilhermetonelli.agendamento;

import org.springframework.boot.SpringApplication;

/**
 * Roda a aplicação localmente usando o PostgreSQL do Testcontainers, sem docker-compose.
 * Execute com {@code ./mvnw spring-boot:test-run} ou pelo botão "Run" da IDE.
 */
public class TestAgendamentoApplication {

    public static void main(String[] args) {
        SpringApplication.from(AgendamentoApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
