package com.guilhermetonelli.agendamento.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.core.retry.RetryPolicy;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;

/** A mensagem é o evento em JSON; o tipo viaja no cabeçalho e só os eventos do projeto são aceitos. */
class MensageriaConfigTest {

    private static final Instant INICIO = Instant.parse("2026-10-24T17:30:00Z");
    private static final Aviso AVISO =
            new Aviso(Papel.CLIENTE, "Carlos Mendes", "carlos@exemplo.com", "Rafael Souza", "Corte", new BigDecimal("45.00"));

    private final MessageConverter conversor = new MensageriaConfig().conversorJson();

    @Test
    void cadaEventoViajaEVoltaIgual() {
        UUID id = UUID.randomUUID();
        List<EventoDeAgendamento> eventos = List.of(
                new AgendamentoCriado(id, id, id, INICIO, INICIO.plusSeconds(1800), AVISO),
                new AgendamentoReagendado(id, id, id, INICIO.minusSeconds(3600), INICIO, INICIO.plusSeconds(1800), AVISO),
                new AgendamentoCancelado(id, id, id, INICIO, "imprevisto", AVISO));

        for (EventoDeAgendamento evento : eventos) {
            Message mensagem = conversor.toMessage(evento, new MessageProperties());

            assertThat(conversor.fromMessage(mensagem)).isEqualTo(evento);
        }
    }

    /** É assim que o consumidor recebe: o parâmetro do {@code @RabbitListener} é a interface, e o tipo concreto vem do cabeçalho. */
    @Test
    void consumidorQueDeclaraAInterfaceRecebeOTipoConcretoDoCabecalho() {
        UUID id = UUID.randomUUID();
        AgendamentoCancelado evento = new AgendamentoCancelado(id, id, id, INICIO, "imprevisto", AVISO);
        Message mensagem = conversor.toMessage(evento, new MessageProperties());
        mensagem.getMessageProperties().setInferredArgumentType(EventoDeAgendamento.class);

        assertThat(conversor.fromMessage(mensagem)).isEqualTo(evento);
    }

    /**
     * Uma classe do próprio projeto que o Jackson saberia montar: só a lista de pacotes a barra. O erro precisa
     * ser de conversão, que o Spring AMQP não repete; qualquer outro seria tentado 4 vezes antes da fila de erros.
     */
    @Test
    void tipoDeForaDoPacoteDosEventosEhRecusadoComoMensagemIlegivel() {
        UUID id = UUID.randomUUID();
        Message mensagem = conversor.toMessage(new AgendamentoCriado(id, id, id, INICIO, INICIO, AVISO), new MessageProperties());
        mensagem.getMessageProperties().setHeader("__TypeId__", LoginRequest.class.getName());

        assertThatThrownBy(() -> conversor.fromMessage(mensagem)).isInstanceOf(MessageConversionException.class);
    }

    @Test
    void jsonIlegivelEhRecusado() {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setHeader("__TypeId__", AgendamentoCriado.class.getName());

        assertThatThrownBy(() -> conversor.fromMessage(new Message("{ não é json".getBytes(), propriedades)))
                .isInstanceOf(MessageConversionException.class);
    }

    /**
     * O Spring AMQP repete qualquer erro do consumidor; sem esta exceção, um corpo ilegível ocuparia a fila por
     * ~26 s antes de ir para a fila de erros, embora nunca vá passar a ser lido.
     */
    @Test
    void mensagemIlegivelNaoEhRepetidaMasFalhaDeEnvioEh() {
        RetryPolicySettings ajustes = new RetryPolicySettings();
        new MensageriaConfig().semRepeticaDeMensagemIlegivel().customize(ajustes);
        RetryPolicy politica = ajustes.createRetryPolicy();

        MessageConversionException ilegivel = new MessageConversionException("ilegível");
        assertThat(politica.shouldRetry(ilegivel)).isFalse();
        assertThat(politica.shouldRetry(new ListenerExecutionFailedException("falhou", ilegivel))).isFalse();
        assertThat(politica.shouldRetry(new IllegalStateException("SMTP fora do ar"))).isTrue();
    }
}
