package com.guilhermetonelli.agendamento.agendamento;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.guilhermetonelli.agendamento.agendamento.evento.LembreteDeAgendamento;
import com.guilhermetonelli.agendamento.mensageria.PublicadorDeEventos;

/**
 * Manda ao RabbitMQ os lembretes que já venceram. O banco é a fonte de verdade: o job do Quartz só chama
 * {@link #enviarVencidos()} a cada minuto.
 */
@Service
public class Lembretes {

    private static final Logger log = LoggerFactory.getLogger(Lembretes.class);
    private static final int LOTE = 100;

    private final AgendamentoRepository repository;
    private final PublicadorDeEventos publicador;
    private final Clock clock;

    Lembretes(AgendamentoRepository repository, PublicadorDeEventos publicador, Clock clock) {
        this.repository = repository;
        this.publicador = publicador;
        this.clock = clock;
    }

    /** Devolve quantos lembretes saíram. */
    public int enviarVencidos() {
        return enviarVencidos(LOTE);
    }

    /**
     * Só marca o envio depois que o broker confirmou: se o processo cair no meio, o cliente pode receber o
     * lembrete duas vezes, mas nunca nenhuma. Com o broker fora, para e recomeça no minuto seguinte.
     */
    int enviarVencidos(int limite) {
        Instant agora = Instant.now(clock);
        repository.descartarLembretesVencidos(agora);
        int enviados = 0;
        for (Agendamento agendamento : repository.lembretesVencidos(agora, Limit.of(limite))) {
            try {
                publicador.enviar(new LembreteDeAgendamento(
                        agendamento.getId(),
                        agendamento.getProfissional().getId(),
                        agendamento.getCliente().getId(),
                        agendamento.getInicio(),
                        agendamento.aviso(null)));
            } catch (Exception e) {
                log.warn("O lembrete do agendamento {} não saiu ({}); a próxima varredura tenta de novo.",
                        agendamento.getId(), e.getMessage());
                break;
            }
            repository.marcarLembreteEnviado(agendamento.getId(), agendamento.getLembrarEm(), agora);
            enviados++;
        }
        if (enviados > 0) {
            log.info("Lembretes enviados: {}.", enviados);
        }
        return enviados;
    }

    /**
     * A mensagem pode esperar na fila, nas repetições ou na DLQ: se o horário já começou, ou se o cliente cancelou
     * ou remarcou nesse meio tempo, o lembrete daquele horário não vale mais.
     */
    public boolean aindaVale(LembreteDeAgendamento lembrete) {
        return lembrete.inicio().isAfter(Instant.now(clock))
                && repository.existsByIdAndStatusAndInicio(
                        lembrete.agendamentoId(), StatusAgendamento.CONFIRMADO, lembrete.inicio());
    }
}
