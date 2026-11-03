package com.guilhermetonelli.agendamento.agendador;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.guilhermetonelli.agendamento.notificacao.NotificacaoService;
import com.guilhermetonelli.agendamento.seguranca.AutenticacaoService;

/** Uma vez por dia apaga o que só faz as tabelas crescerem: sessões vencidas e notificações antigas. */
@DisallowConcurrentExecution
public class LimpezaJob extends QuartzJobBean {

    private final AutenticacaoService autenticacao;
    private final NotificacaoService notificacoes;

    LimpezaJob(AutenticacaoService autenticacao, NotificacaoService notificacoes) {
        this.autenticacao = autenticacao;
        this.notificacoes = notificacoes;
    }

    @Override
    protected void executeInternal(JobExecutionContext contexto) {
        autenticacao.apagarSessoesVencidas();
        notificacoes.apagarAntigas();
    }
}
