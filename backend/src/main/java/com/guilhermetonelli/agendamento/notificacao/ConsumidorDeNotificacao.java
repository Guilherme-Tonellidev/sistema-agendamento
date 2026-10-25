package com.guilhermetonelli.agendamento.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.mensageria.Topologia;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;

@Component
class ConsumidorDeNotificacao {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeNotificacao.class);

    private final NotificacaoService service;
    private final ProfissionalService profissionais;
    private final AgendamentoProperties propriedades;

    ConsumidorDeNotificacao(NotificacaoService service, ProfissionalService profissionais, AgendamentoProperties propriedades) {
        this.service = service;
        this.profissionais = profissionais;
        this.propriedades = propriedades;
    }

    /**
     * Falha do banco sobe e aciona as tentativas. Barbeiro que não existe mais não: repetir não o traria de
     * volta, e a mensagem atrasada travaria a fila.
     */
    @RabbitListener(queues = Topologia.FILA_NOTIFICACAO)
    void tratar(EventoDeAgendamento evento) {
        if (!RegrasDeAviso.avisaProfissional(evento)) {
            return;
        }
        if (!profissionais.existe(evento.profissionalId())) {
            log.warn("Notificação descartada: o profissional {} não existe mais (agendamento {}).",
                    evento.profissionalId(), evento.agendamentoId());
            return;
        }
        service.registrar(evento.profissionalId(), tipo(evento), TextosDeNotificacao.de(evento, propriedades.fusoHorario()), evento.inicio());
    }

    private static TipoDeNotificacao tipo(EventoDeAgendamento evento) {
        return switch (evento) {
            case AgendamentoCriado e -> TipoDeNotificacao.CRIADO;
            case AgendamentoReagendado e -> TipoDeNotificacao.REMARCADO;
            case AgendamentoCancelado e -> TipoDeNotificacao.CANCELADO;
        };
    }
}
