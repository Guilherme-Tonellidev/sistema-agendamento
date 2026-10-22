package com.guilhermetonelli.agendamento.shared.exception;

/** Credenciais ou sessão inválidas. Vira HTTP 401, sempre com mensagem genérica. */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
