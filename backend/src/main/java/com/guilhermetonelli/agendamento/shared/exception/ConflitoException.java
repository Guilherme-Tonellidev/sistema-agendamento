package com.guilhermetonelli.agendamento.shared.exception;

import java.util.Map;

/**
 * A operação conflita com o estado atual, como um e-mail já cadastrado. Vira HTTP 409.
 * Os {@code detalhes} entram como campos extras da resposta (por exemplo, os registros envolvidos).
 */
public class ConflitoException extends RuntimeException {

    private final transient Map<String, Object> detalhes;

    public ConflitoException(String mensagem) {
        this(mensagem, Map.of());
    }

    public ConflitoException(String mensagem, Map<String, Object> detalhes) {
        super(mensagem);
        this.detalhes = Map.copyOf(detalhes);
    }

    public Map<String, Object> getDetalhes() {
        return detalhes;
    }
}
