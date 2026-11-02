package com.guilhermetonelli.agendamento.shared.exception;

/** Parâmetro de consulta que a API não aceita para quem pediu. Vira HTTP 400. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
