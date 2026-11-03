package com.guilhermetonelli.agendamento.shared.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

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

    @Test
    void depoisDeUmaFalhaSoUmaChamadaPorPausaTentaORedis() {
        assertThat(falhas.podeTentar()).isTrue();
        falhas.registrar("login", falha);
        assertThat(falhas.podeTentar()).isFalse();

        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(4));
        assertThat(falhas.podeTentar()).isFalse();

        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(5));
        assertThat(falhas.podeTentar()).isTrue();
        assertThat(falhas.podeTentar()).isFalse();
    }

    @Test
    void quandoORedisRespondeTodasVoltamATentar() {
        falhas.registrar("login", falha);

        falhas.respondeu();

        assertThat(falhas.podeTentar()).isTrue();
        assertThat(falhas.podeTentar()).isTrue();
    }

    @Test
    void aFalhaDaTentativaReiniciaAPausaMesmoSemEscreverNoLog() {
        falhas.registrar("login", falha);
        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(5));
        assertThat(falhas.podeTentar()).isTrue();

        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(7));
        assertThat(falhas.registrar("login", falha)).isFalse();

        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(11));
        assertThat(falhas.podeTentar()).isFalse();
        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(12));
        assertThat(falhas.podeTentar()).isTrue();
    }

    @Test
    void emParaleloSoUmaChamadaGanhaATentativa() {
        falhas.registrar("login", falha);
        relogio.ajustar(RelogioDeTeste.INICIAL.plusSeconds(5));

        long tentaram = IntStream.range(0, 200).parallel().filter(i -> falhas.podeTentar()).count();

        assertThat(tentaram).isEqualTo(1);
    }
}
