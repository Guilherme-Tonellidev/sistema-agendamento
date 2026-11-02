package com.guilhermetonelli.agendamento;

import org.springframework.boot.SpringApplication;

/**
 * Roda a aplicação localmente usando o PostgreSQL do Testcontainers, sem docker-compose.
 * Execute com {@code ./mvnw spring-boot:test-run} ou pelo botão "Run" da IDE.
 */
public class TestAgendamentoApplication {

    public static void main(String[] args) {
        // Sem perfil, a aplicação não traz valores de desenvolvimento (chave JWT, e-mail): aqui o padrão é o local.
        if (System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", "local");
        }
        SpringApplication.from(AgendamentoApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
