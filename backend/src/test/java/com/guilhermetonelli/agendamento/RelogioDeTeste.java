package com.guilhermetonelli.agendamento;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Primary;

/** Relógio da aplicação nos testes: parado em {@link #INICIAL} até o teste mandar mudar. */
@Primary
public class RelogioDeTeste extends Clock {

    /** Terça-feira, 20/10/2026, 09:00 em São Paulo. */
    public static final Instant INICIAL = Instant.parse("2026-10-20T12:00:00Z");

    private volatile Instant agora = INICIAL;

    public void ajustar(Instant instante) {
        agora = instante;
    }

    public void reiniciar() {
        agora = INICIAL;
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(agora, zona);
    }
}
