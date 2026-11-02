package com.guilhermetonelli.agendamento.shared.exception;

import java.time.Duration;

/** Limite de tentativas estourado. Vira HTTP 429, com o cabeçalho {@code Retry-After}. */
public class TentativasDemaisException extends RuntimeException {

    private final Duration esperar;

    public TentativasDemaisException(String mensagem, Duration esperar) {
        super(mensagem);
        this.esperar = esperar;
    }

    /** Quanto falta para a próxima tentativa ser aceita. */
    public Duration getEsperar() {
        return esperar;
    }
}
