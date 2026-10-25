package com.guilhermetonelli.agendamento.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/** O aviso é um extra: se o RabbitMQ falhar, a reserva já gravada não pode virar erro. */
@ExtendWith(OutputCaptureExtension.class)
class PublicadorDeEventosTest {

    private final AgendamentoCriado evento = new AgendamentoCriado(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(),
            new Aviso(Papel.CLIENTE, "Carlos", "carlos@exemplo.com", "Rafael", "Corte", BigDecimal.TEN));

    @Test
    void falhaDeConexaoNaoSobeEFicaNoLog(CapturedOutput saida) {
        var rabbit = rabbitQueFalha(new AmqpConnectException(new ConnectException("recusada")));

        assertThatCode(() -> new PublicadorDeEventos(rabbit).publicar(evento)).doesNotThrowAnyException();
        assertThat(saida).contains("ERROR").contains(evento.agendamentoId().toString());
    }

    @Test
    void confirmacaoQueNaoChegaNaoSobeEFicaNoLog(CapturedOutput saida) {
        var rabbit = rabbitQueFalha(new AmqpException("a confirmação do broker não chegou em 2000 ms"));

        assertThatCode(() -> new PublicadorDeEventos(rabbit).publicar(evento)).doesNotThrowAnyException();
        assertThat(saida).contains("ERROR").contains(evento.agendamentoId().toString());
    }

    private static RabbitTemplate rabbitQueFalha(RuntimeException erro) {
        return new RabbitTemplate() {
            @Override
            public <T> T invoke(
                    RabbitOperations.OperationsCallback<T> acao,
                    com.rabbitmq.client.ConfirmCallback ack,
                    com.rabbitmq.client.ConfirmCallback nack) {
                throw erro;
            }
        };
    }
}
