package com.guilhermetonelli.agendamento.seguranca;

import com.guilhermetonelli.agendamento.seguranca.dto.TokenResponse;

/** Resultado de login, cadastro ou renovação: o token de acesso e o refresh token que vai para o cookie. */
record SessaoAberta(TokenResponse acesso, String refreshToken) {}
