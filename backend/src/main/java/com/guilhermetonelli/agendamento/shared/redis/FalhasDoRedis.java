package com.guilhermetonelli.agendamento.shared.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registra as falhas do Redis no log, no máximo uma por minuto: com o servidor fora do ar, cada login e cada
 * consulta de horários falharia, e o log viraria só isso.
 */
@Component
public class FalhasDoRedis {

    private static final Logger log = LoggerFactory.getLogger(FalhasDoRedis.class);
    private static final Duration INTERVALO = Duration.ofMinutes(1);

    private final Clock clock;
    private final AtomicReference<Instant> ultimoAviso = new AtomicReference<>();

    FalhasDoRedis(Clock clock) {
        this.clock = clock;
    }

    /** Devolve se a falha foi escrita no log (a primeira de cada minuto). */
    public boolean registrar(String operacao, RuntimeException falha) {
        Instant agora = Instant.now(clock);
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
