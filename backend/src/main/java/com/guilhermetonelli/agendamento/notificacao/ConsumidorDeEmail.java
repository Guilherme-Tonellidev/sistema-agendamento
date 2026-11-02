package com.guilhermetonelli.agendamento.notificacao;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.agendamento.Lembretes;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.agendamento.evento.LembreteDeAgendamento;
import com.guilhermetonelli.agendamento.mensageria.Topologia;
import com.guilhermetonelli.agendamento.notificacao.email.EnviadorDeEmail;
import com.guilhermetonelli.agendamento.notificacao.email.ModeloDeEmail;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;

@Component
class ConsumidorDeEmail {

    private final EnviadorDeEmail enviador;
    private final AgendamentoProperties propriedades;
    private final Lembretes lembretes;

    ConsumidorDeEmail(EnviadorDeEmail enviador, AgendamentoProperties propriedades, Lembretes lembretes) {
        this.enviador = enviador;
        this.propriedades = propriedades;
        this.lembretes = lembretes;
    }

    /**
     * A exceção do envio não é capturada: é ela que aciona as tentativas e, no fim, a fila de erros. O lembrete
     * só sai se o agendamento ainda valer.
     */
    @RabbitListener(queues = Topologia.FILA_EMAIL)
    void tratar(EventoDeAgendamento evento) {
        if (RegrasDeAviso.avisaCliente(evento) && (!(evento instanceof LembreteDeAgendamento lembrete) || lembretes.aindaVale(lembrete))) {
            enviador.enviar(ModeloDeEmail.montar(evento, propriedades.fusoHorario()));
        }
    }
}
