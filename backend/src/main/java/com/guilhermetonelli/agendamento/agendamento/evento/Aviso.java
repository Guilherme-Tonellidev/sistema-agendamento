package com.guilhermetonelli.agendamento.agendamento.evento;

import java.math.BigDecimal;

import com.guilhermetonelli.agendamento.seguranca.Papel;

/**
 * O que quem avisa as pessoas precisa saber do agendamento, montado na transação, onde as entidades
 * ainda estão carregadas. {@code autor} é quem fez a ação; {@code emailDoCliente} é nulo no encaixe,
 * em que o cliente foi cadastrado só com o nome.
 */
public record Aviso(
        Papel autor, String cliente, String emailDoCliente, String profissional, String servico, BigDecimal preco) {}
