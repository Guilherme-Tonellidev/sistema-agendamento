package com.guilhermetonelli.agendamento.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * A reserva espera o broker por pouco tempo, qualquer que seja a etapa em que ele trave. Só o TCP tinha limite:
 * sem resposta à confirmação, o cliente fecha o canal e espera o fechamento pelo timeout das chamadas do canal
 * (10 min por padrão), e a conexão nova espera o handshake (10 s). Tudo isso com a conexão do banco presa.
 */
class EsperaPeloBrokerTest {

    private static final int DOIS_SEGUNDOS = 2000;

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(MensageriaConfig.class);

    @Test
    void conexaoHandshakeECanalEsperamNoMaximo2Segundos() {
        contexto.run(ctx -> {
            var cliente = ctx.getBean(CachingConnectionFactory.class).getRabbitConnectionFactory();

            assertThat(cliente.getConnectionTimeout()).as("conectar").isEqualTo(DOIS_SEGUNDOS);
            assertThat(cliente.getHandshakeTimeout()).as("handshake").isEqualTo(DOIS_SEGUNDOS);
            assertThat(cliente.getChannelRpcTimeout()).as("abrir e fechar canal").isEqualTo(DOIS_SEGUNDOS);
        });
    }
}
