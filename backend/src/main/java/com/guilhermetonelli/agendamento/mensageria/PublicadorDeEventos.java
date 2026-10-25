package com.guilhermetonelli.agendamento.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;

/**
 * Leva os eventos de agendamento ao RabbitMQ depois do commit: só sai o que de fato foi gravado.
 * Se o broker estiver fora, a reserva continua valendo e o aviso daquela vez se perde (fica no log).
 */
@Component
class PublicadorDeEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeEventos.class);
    private static final long CONFIRMACAO_MS = 2000;

    private final RabbitTemplate rabbit;

    PublicadorDeEventos(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /**
     * Captura qualquer falha: uma exceção depois do commit sobe até quem chamou o método transacional e
     * viraria um erro 500 com a reserva já gravada.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publicar(EventoDeAgendamento evento) {
        try {
            rabbit.invoke(operacoes -> {
                operacoes.convertAndSend(Topologia.EXCHANGE, Topologia.chaveDe(evento), evento);
                operacoes.waitForConfirmsOrDie(CONFIRMACAO_MS);
                return null;
            });
        } catch (Exception e) {
            log.error("Não foi possível publicar {} do agendamento {}; o aviso desta vez se perde.",
                    evento.getClass().getSimpleName(), evento.agendamentoId(), e);
        }
    }
}
