package com.guilhermetonelli.agendamento.shared.exception;

/** A operação conflita com o estado atual, como um e-mail já cadastrado. Vira HTTP 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
