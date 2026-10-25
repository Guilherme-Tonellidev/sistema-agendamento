package com.guilhermetonelli.agendamento.mensageria;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;

/**
 * O que existe no RabbitMQ: uma exchange e, para cada consumidor, a sua fila e a fila de erros dela
 * ({@code <fila>.dlq}). Duas filas separadas fazem a falha de um consumidor não repetir nem travar o outro.
 * A mensagem que esgota as tentativas vai para a {@code .dlq} pela exchange padrão, com a chave igual ao nome dela.
 */
@Configuration
public class Topologia {

    public static final String EXCHANGE = "agendamentos";
    public static final String FILA_EMAIL = "email.cliente";
    public static final String FILA_NOTIFICACAO = "notificacao.profissional";

    static String chaveDe(EventoDeAgendamento evento) {
        return switch (evento) {
            case AgendamentoCriado criado -> "agendamento.criado";
            case AgendamentoReagendado remarcado -> "agendamento.remarcado";
            case AgendamentoCancelado cancelado -> "agendamento.cancelado";
        };
    }

    @Bean
    Declarables mensageria() {
        TopicExchange exchange = new TopicExchange(EXCHANGE);
        List<Declarable> itens = new ArrayList<>(List.of(exchange));
        for (String nome : List.of(FILA_EMAIL, FILA_NOTIFICACAO)) {
            Queue fila = QueueBuilder.durable(nome)
                    .deadLetterExchange("")
                    .deadLetterRoutingKey(nome + ".dlq")
                    .build();
            Binding ligacao = BindingBuilder.bind(fila).to(exchange).with("agendamento.*");
            itens.addAll(List.of(fila, ligacao, QueueBuilder.durable(nome + ".dlq").build()));
        }
        return new Declarables(itens);
    }
}
