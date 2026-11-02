package com.guilhermetonelli.agendamento.agendamento;

import java.util.function.Predicate;

import com.guilhermetonelli.agendamento.agendamento.evento.LembreteDeAgendamento;

/** {@link Lembretes} sem banco, para os testes de quem só precisa da resposta de {@code aindaVale}. */
public final class LembretesDeTeste {

    private LembretesDeTeste() {}

    public static Lembretes que(Predicate<LembreteDeAgendamento> vale) {
        return new Lembretes(null, null, null) {
            @Override
            public boolean aindaVale(LembreteDeAgendamento lembrete) {
                return vale.test(lembrete);
            }
        };
    }
}
