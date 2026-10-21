package com.guilhermetonelli.agendamento.shared.domain;

import java.time.Duration;
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

    /** Duração legível para mensagens: "2h", "1h30", "45 min". */
    public static String duracao(Duration duracao) {
        long horas = duracao.toHours();
        int minutos = duracao.toMinutesPart();
        if (horas == 0) {
            return minutos + " min";
        }
        return minutos == 0 ? horas + "h" : "%dh%02d".formatted(horas, minutos);
    }
}
