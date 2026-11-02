package com.guilhermetonelli.agendamento.shared.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.guilhermetonelli.agendamento.RelogioDeTeste;

class FalhasDoRedisTest {

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final FalhasDoRedis falhas = new FalhasDoRedis(relogio);
    private final RuntimeException falha = new IllegalStateException("conexão recusada");

    @Test
    void escreveNoMaximoUmAvisoPorMinuto() {
        assertThat(falhas.registrar("login", falha)).isTrue();
        assertThat(falhas.registrar("login", falha)).isFalse();

        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(59));
        assertThat(falhas.registrar("cache", falha)).isFalse();

        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(60));
        assertThat(falhas.registrar("cache", falha)).isTrue();
    }
}
