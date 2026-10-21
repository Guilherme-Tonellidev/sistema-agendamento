package com.guilhermetonelli.agendamento.shared.exception;

/** Os dados são válidos, mas a operação viola uma regra do negócio. Vira HTTP 422. */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
