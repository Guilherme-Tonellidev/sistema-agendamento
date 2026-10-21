package com.guilhermetonelli.agendamento.shared.web;

/** Um campo inválido na requisição, devolvido na lista "erros" das respostas 400. */
public record ErroDeCampo(String campo, String mensagem) {}
