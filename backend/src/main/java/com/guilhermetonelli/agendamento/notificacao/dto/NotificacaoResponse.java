package com.guilhermetonelli.agendamento.notificacao.dto;

import java.time.Instant;
import java.util.UUID;

import com.guilhermetonelli.agendamento.notificacao.TipoDeNotificacao;

/** @param inicio horário da reserva, para o front abrir a Agenda no dia certo */
public record NotificacaoResponse(UUID id, TipoDeNotificacao tipo, String texto, Instant inicio, Instant criadaEm, boolean lida) {}
