package com.guilhermetonelli.agendamento.auditoria;

/** O que aconteceu com o agendamento, na linha do tempo. */
public enum TipoDeEvento {
    CRIADO,
    REMARCADO,
    CANCELADO,
    CONCLUIDO,
    NAO_COMPARECEU
}
