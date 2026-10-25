package com.guilhermetonelli.agendamento.notificacao.email;

/** Entrega o e-mail. Falha lançando exceção, que é o que aciona as tentativas do consumidor. */
public interface EnviadorDeEmail {

    void enviar(Email email);
}
