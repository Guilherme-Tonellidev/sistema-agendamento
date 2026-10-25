package com.guilhermetonelli.agendamento.notificacao.dto;

import java.util.List;

/** {@code naoLidas} conta todas; {@code recentes} traz só as últimas, lidas ou não. */
public record NotificacoesResponse(long naoLidas, List<NotificacaoResponse> recentes) {}
