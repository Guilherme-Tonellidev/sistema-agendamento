package com.guilhermetonelli.agendamento.shared.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Acompanha as falhas do Redis. Escreve no log no máximo uma por minuto: com o servidor fora do ar, cada login e
 * cada consulta de horários falharia, e o log viraria só isso.
 *
 * <p>Também evita a fila: sem conexão aberta, cada chamada espera o timeout de conexão (300 ms) uma depois da outra,
 * e quem espera segura uma conexão do pool do banco. Depois de uma falha, só uma chamada a cada {@value #PAUSA_EM_SEGUNDOS}
 * segundos tenta o Redis; as outras seguem sem ele até uma dar certo.
 */
@Component
public class FalhasDoRedis {

    private static final Logger log = LoggerFactory.getLogger(FalhasDoRedis.class);
    private static final Duration INTERVALO = Duration.ofMinutes(1);
    static final int PAUSA_EM_SEGUNDOS = 5;
    private static final Duration PAUSA = Duration.ofSeconds(PAUSA_EM_SEGUNDOS);

    private final Clock clock;
    private final AtomicReference<Instant> ultimoAviso = new AtomicReference<>();
    private final AtomicReference<Instant> proximaTentativa = new AtomicReference<>();

    FalhasDoRedis(Clock clock) {
        this.clock = clock;
    }

    /** Se vale tentar o Redis agora. Depois de uma falha, só uma chamada por pausa recebe {@code true}. */
    public boolean podeTentar() {
        Instant liberacao = proximaTentativa.get();
        if (liberacao == null) {
            return true;
        }
        Instant agora = Instant.now(clock);
        return !agora.isBefore(liberacao) && proximaTentativa.compareAndSet(liberacao, agora.plus(PAUSA));
    }

    /** O Redis respondeu: volta a ser tentado em toda chamada. */
    public void respondeu() {
        proximaTentativa.set(null);
    }

    /** Fecha o Redis por uma pausa e devolve se a falha foi escrita no log (a primeira de cada minuto). */
    public boolean registrar(String operacao, RuntimeException falha) {
        Instant agora = Instant.now(clock);
        proximaTentativa.set(agora.plus(PAUSA));
        Instant anterior = ultimoAviso.get();
        if (anterior != null && agora.isBefore(anterior.plus(INTERVALO))) {
            return false;
        }
        if (!ultimoAviso.compareAndSet(anterior, agora)) {
            return false;
        }
        log.warn("Redis indisponível em {} ({}); seguindo sem ele.", operacao, falha.getMessage());
        return true;
    }
}
