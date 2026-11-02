package com.guilhermetonelli.agendamento.agendamento;

import java.time.Instant;

import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties.Lembrete;

/** Quando o cliente é lembrado do horário. */
final class RegraDeLembrete {

    private RegraDeLembrete() {}

    /**
     * Com tempo de sobra, o lembrete sai {@code antecedencia} antes do início. Quem reservou depois disso recebe
     * só o da reserva tardia, se ainda faltar mais que ela. Devolve {@code null} quando não há o que lembrar.
     */
    static Instant quando(Instant agora, Instant inicio, Lembrete config) {
        Instant normal = inicio.minus(config.antecedencia());
        if (!agora.isAfter(normal)) {
            return normal;
        }
        Instant tardio = inicio.minus(config.antecedenciaReservaTardia());
        return agora.isBefore(tardio) ? tardio : null;
    }
}
