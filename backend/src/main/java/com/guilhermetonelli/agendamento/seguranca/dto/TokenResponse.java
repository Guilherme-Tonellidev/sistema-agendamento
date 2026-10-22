package com.guilhermetonelli.agendamento.seguranca.dto;

/** Token de acesso para o cabeçalho {@code Authorization}; o refresh token vai num cookie HttpOnly. */
public record TokenResponse(String tokenDeAcesso, String tipo, long expiraEmSegundos) {}
