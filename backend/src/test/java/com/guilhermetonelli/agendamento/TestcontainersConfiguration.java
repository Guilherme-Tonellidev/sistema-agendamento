package com.guilhermetonelli.agendamento;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe PostgreSQL, RabbitMQ e Mailpit reais em contêineres para os testes. O {@code @ServiceConnection}
 * entrega a conexão ao Spring sem configurar nada à mão; o Mailpit não tem suporte pronto, então o
 * SMTP e a API dele entram por propriedades.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18-alpine");
    static final DockerImageName RABBITMQ = DockerImageName.parse("rabbitmq:4-management-alpine");
    static final DockerImageName MAILPIT = DockerImageName.parse("axllent/mailpit");

    private static final int SMTP = 1025;
    private static final int API = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES);
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(RABBITMQ);
    }

    @Bean
    @SuppressWarnings("resource") // o Spring sobe e para o contêiner junto com o contexto
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(MAILPIT).withExposedPorts(SMTP, API);
    }

    @Bean
    DynamicPropertyRegistrar mailpitPropriedades(@Qualifier("mailpitContainer") GenericContainer<?> mailpit) {
        return registro -> {
            registro.add("spring.mail.host", mailpit::getHost);
            registro.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP));
            registro.add("mailpit.url", () -> "http://%s:%d".formatted(mailpit.getHost(), mailpit.getMappedPort(API)));
        };
    }
}
