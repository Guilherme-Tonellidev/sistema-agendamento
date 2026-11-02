package com.guilhermetonelli.agendamento.agendador;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.guilhermetonelli.agendamento.agendamento.Lembretes;

/** A cada minuto manda os lembretes vencidos. Nunca roda duas vezes ao mesmo tempo, nem em instâncias diferentes. */
@DisallowConcurrentExecution
public class LembreteJob extends QuartzJobBean {

    private final Lembretes lembretes;

    LembreteJob(Lembretes lembretes) {
        this.lembretes = lembretes;
    }

    @Override
    protected void executeInternal(JobExecutionContext contexto) {
        lembretes.enviarVencidos();
    }
}
