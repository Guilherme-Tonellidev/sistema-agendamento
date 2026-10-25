package com.guilhermetonelli.agendamento;

import org.springframework.context.annotation.Primary;

import com.guilhermetonelli.agendamento.notificacao.email.Email;
import com.guilhermetonelli.agendamento.notificacao.email.EnviadorDeEmail;

/** Envolve o enviador real e, quando mandado, falha como um SMTP fora do ar. Começa desligado em todo teste. */
@Primary
public class EnviadorQueFalha implements EnviadorDeEmail {

    private final EnviadorDeEmail real;
    private volatile boolean falharSempre;

    EnviadorQueFalha(EnviadorDeEmail real) {
        this.real = real;
    }

    public void falharSempre(boolean falhar) {
        this.falharSempre = falhar;
    }

    @Override
    public void enviar(Email email) {
        if (falharSempre) {
            throw new IllegalStateException("SMTP fora do ar (simulado pelo teste)");
        }
        real.enviar(email);
    }
}
