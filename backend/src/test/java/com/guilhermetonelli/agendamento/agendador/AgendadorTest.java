package com.guilhermetonelli.agendamento.agendador;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Mailpit.EmailRecebido;

/** O job está registrado no Quartz, guardado no banco, e chama de verdade a varredura dos lembretes. */
class AgendadorTest extends IntegracaoTest {

    private static final JobKey JOB = JobKey.jobKey("lembretes");

    @Autowired
    private Scheduler scheduler;

    @Test
    void usaArmazenamentoJdbcEmCluster() throws SchedulerException {
        var metadados = scheduler.getMetaData();

        assertThat(metadados.isJobStoreSupportsPersistence()).isTrue();
        assertThat(metadados.isJobStoreClustered()).isTrue();
    }

    /** Também prova que o reset do banco entre os testes preserva as tabelas do Quartz. */
    @Test
    void jobEGatilhoSaoGravadosNoBanco() throws SchedulerException {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qrtz_job_details WHERE job_name = 'lembretes'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qrtz_triggers WHERE trigger_name = 'lembretes'", Integer.class)).isEqualTo(1);
        assertThat(scheduler.getJobDetail(JOB).isConcurrentExecutionDisallowed()).isTrue();
        assertThat(scheduler.getTrigger(TriggerKey.triggerKey("lembretes")))
                .isInstanceOfSatisfying(SimpleTrigger.class, gatilho -> assertThat(gatilho.getRepeatInterval()).isEqualTo(60_000));
    }

    @Test
    void executaOLembreteJobDePontaAPonta() throws SchedulerException {
        relogio.ajustar(Instant.parse("2026-10-18T12:00:00Z"));
        var cenario = new Cenario(api);
        UUID barbeiro = cenario.barbeiro("Rafael Souza");
        var carlos = cenario.cliente("Carlos Mendes");
        cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        relogio.ajustar(Instant.parse("2026-10-20T11:00:00Z"));

        scheduler.start();
        try {
            scheduler.triggerJob(JOB);

            assertThat(mailpit.aguardar(carlos.email(), 2)).extracting(EmailRecebido::assunto)
                    .contains("Lembrete de horário: Corte, qua 21/10 às 8h00");
        } finally {
            scheduler.standby();
        }
    }
}
