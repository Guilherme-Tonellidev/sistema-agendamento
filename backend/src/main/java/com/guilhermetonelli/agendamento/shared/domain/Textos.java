package com.guilhermetonelli.agendamento.shared.domain;

import java.util.Locale;

/** Normalizações de texto usadas pelas entidades antes de salvar. */
public final class Textos {

    private Textos() {}

    /** E-mails são guardados em minúsculas, para que "Ana@x.com" e "ana@x.com" sejam o mesmo. */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Remove espaços das pontas e transforma texto vazio em {@code null}. */
    public static String limparOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }
}
