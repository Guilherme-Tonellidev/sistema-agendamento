package com.guilhermetonelli.agendamento.notificacao.email;

/** Mensagem pronta para enviar: o mesmo conteúdo em texto puro e em HTML. */
public record Email(String para, String assunto, String texto, String html) {}
