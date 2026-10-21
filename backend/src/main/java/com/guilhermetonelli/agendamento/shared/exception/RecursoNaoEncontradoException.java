package com.guilhermetonelli.agendamento.shared.exception;

/** O recurso pedido não existe. Vira HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Object id) {
        super(recurso + " não encontrado(a): " + id);
    }
}
