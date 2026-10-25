package com.guilhermetonelli.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Apoio dos testes ao RabbitMQ: limpar as filas entre testes, esperar mensagens, parar os consumidores e simular o broker fora do ar. */
public class Filas {

    private static final List<String> DO_PROJETO =
            List.of("email.cliente", "email.cliente.dlq", "notificacao.profissional", "notificacao.profissional.dlq");

    private final AmqpAdmin admin;
    private final RabbitTemplate rabbit;
    private final RabbitMQContainer broker;
    private final RabbitListenerEndpointRegistry consumidores;
    private final List<String> temporarias = new ArrayList<>();

    Filas(AmqpAdmin admin, RabbitTemplate rabbit, RabbitMQContainer broker, RabbitListenerEndpointRegistry consumidores) {
        this.admin = admin;
        this.rabbit = rabbit;
        this.broker = broker;
        this.consumidores = consumidores;
    }

    /**
     * Para os consumidores e espera os que estão no meio de uma mensagem. Parados, nenhum deles mexe no banco:
     * sem isso, o que ficou da reserva do teste anterior ainda grava notificação durante o TRUNCATE do teste
     * seguinte (deadlock) e soma consultas às que um teste conta.
     */
    public void pararConsumidores() {
        consumidores.stop();
    }

    public void religarConsumidores() {
        consumidores.start();
    }

    /** Esvazia as filas do projeto que já existem e apaga as temporárias do teste anterior. */
    public void purgar() {
        DO_PROJETO.stream().filter(fila -> admin.getQueueInfo(fila) != null).forEach(admin::purgeQueue);
        temporarias.forEach(admin::deleteQueue);
        temporarias.clear();
    }

    /** Mensagens prontas na fila; fila que não existe tem zero. */
    public long quantidadeNa(String fila) {
        QueueInformation info = admin.getQueueInfo(fila);
        return info == null ? 0 : info.getMessageCount();
    }

    /** Espera até 10 s por uma mensagem na fila e a retira. */
    public Message aguardarNa(String fila) {
        Message mensagem = rabbit.receive(fila, 10_000);
        assertThat(mensagem).as("mensagem em %s", fila).isNotNull();
        return mensagem;
    }

    /**
     * Fila só do teste, ligada a {@code agendamento.*}: lê o que a aplicação publica sem disputar com os consumidores.
     * Exclusiva da conexão, porque o RabbitMQ 4 recusa fila transitória que não seja (feature
     * {@code transient_nonexcl_queues}), mas sem auto-delete: cada {@link #aguardarNa} abre e fecha um consumidor, e o
     * broker apagaria a fila quando o primeiro saísse, deixando a segunda leitura sem fila (404).
     * Quem apaga é o {@link #purgar()} do teste seguinte.
     */
    public String filaTemporaria() {
        Queue fila = new Queue("teste." + UUID.randomUUID(), false, true, false);
        admin.declareQueue(fila);
        admin.declareBinding(BindingBuilder.bind(fila).to(new TopicExchange("agendamentos")).with("agendamento.*"));
        temporarias.add(fila.getName());
        return fila.getName();
    }

    /** Congela o contêiner do RabbitMQ durante a ação, como um broker que parou de responder. */
    public void comBrokerPausado(Runnable acao) {
        var docker = broker.getDockerClient();
        docker.pauseContainerCmd(broker.getContainerId()).exec();
        try {
            acao.run();
        } finally {
            docker.unpauseContainerCmd(broker.getContainerId()).exec();
        }
    }
}
