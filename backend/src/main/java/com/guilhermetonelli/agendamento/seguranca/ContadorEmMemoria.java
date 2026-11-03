package com.guilhermetonelli.agendamento.seguranca;

import static com.guilhermetonelli.agendamento.seguranca.LimiteDeTentativas.JANELA;
import static com.guilhermetonelli.agendamento.seguranca.LimiteDeTentativas.MAXIMO;
import static com.guilhermetonelli.agendamento.seguranca.LimiteDeTentativas.bloqueio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.shared.exception.TentativasDemaisException;

/** Conta as tentativas de login na memória desta instância: é o que segura o limite quando o Redis está fora do ar. */
@Component
class ContadorEmMemoria {

    /** Teto de chaves na memória, para uma enxurrada de e-mails diferentes não esgotar o heap. */
    private static final int MAXIMO_DE_CHAVES = 10_000;

    private record Janela(Instant inicio, int tentativas) {

        boolean expirou(Instant agora) {
            return !agora.isBefore(inicio.plus(JANELA));
        }
    }

    private final Clock clock;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    ContadorEmMemoria(Clock clock) {
        this.clock = clock;
    }

    /** Conta mais uma tentativa ou, se o limite da janela já foi alcançado, lança {@link TentativasDemaisException}. */
    void registrarTentativa(String chave) {
        Instant agora = Instant.now(clock);
        if (janelas.size() >= MAXIMO_DE_CHAVES && !janelas.containsKey(chave)) {
            abrirEspaco(agora);
        }
        janelas.compute(chave, (k, atual) -> {
            if (atual == null || atual.expirou(agora)) {
                return new Janela(agora, 1);
            }
            if (atual.tentativas() >= MAXIMO) {
                throw bloqueio(Duration.between(agora, atual.inicio().plus(JANELA)));
            }
            return new Janela(atual.inicio(), atual.tentativas() + 1);
        });
    }

    /** Recomeça a contagem desta chave. */
    void limpar(String chave) {
        janelas.remove(chave);
    }

    void reiniciar() {
        janelas.clear();
    }

    private void abrirEspaco(Instant agora) {
        janelas.values().removeIf(janela -> janela.expirou(agora));
        if (janelas.size() >= MAXIMO_DE_CHAVES) {
            janelas.entrySet().stream()
                    .min(Comparator.comparing(entrada -> entrada.getValue().inicio()))
                    .ifPresent(maisAntiga -> janelas.remove(maisAntiga.getKey()));
        }
    }
}
