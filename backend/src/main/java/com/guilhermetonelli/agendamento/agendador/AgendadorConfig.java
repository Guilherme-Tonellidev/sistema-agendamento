package com.guilhermetonelli.agendamento.agendador;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AgendadorConfig {

    private static final String LEMBRETES = "lembretes";
    private static final String LIMPEZA = "limpeza";

    @Bean
    JobDetail lembretesJob() {
        return JobBuilder.newJob(LembreteJob.class).withIdentity(LEMBRETES).storeDurably().build();
    }

    @Bean
    Trigger lembretesGatilho(JobDetail lembretesJob) {
        return TriggerBuilder.newTrigger()
                .forJob(lembretesJob)
                .withIdentity(LEMBRETES)
                .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever())
                .build();
    }

    @Bean
    JobDetail limpezaJob() {
        return JobBuilder.newJob(LimpezaJob.class).withIdentity(LIMPEZA).storeDurably().build();
    }

    @Bean
    Trigger limpezaGatilho(JobDetail limpezaJob) {
        return TriggerBuilder.newTrigger()
                .forJob(limpezaJob)
                .withIdentity(LIMPEZA)
                .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(3, 0))
                .build();
    }
}
