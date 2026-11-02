package com.guilhermetonelli.agendamento.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.TentativasDemaisException;

/**
 * Limita as tentativas de login: no máximo {@value #MAXIMO} por janela de 15 minutos para cada par
 * e-mail + endereço de origem. Sem isso, a senha de qualquer conta podia ser adivinhada a tiros.
 *
 * <p>A tentativa é contada <em>antes</em> de a senha ser conferida, e de forma atômica: requisições em
 * paralelo não passam todas pela checagem antes de alguma ser registrada. Um login bem-sucedido zera a contagem.
 *
 * <p>O estado fica na memória desta instância. Com várias instâncias da API cada uma conta a sua parte, e um
 * reinício zera tudo; um armazenamento compartilhado (Redis, no roteiro do projeto) resolve isso. Atrás de um
 * proxy reverso o endereço de origem é o do proxy, a não ser que o servidor esteja configurado para confiar nos
 * cabeçalhos {@code X-Forwarded-For} dele.
 */
@Component
public class LimiteDeTentativas {

    static final int MAXIMO = 5;
    static final Duration JANELA = Duration.ofMinutes(15);
    /** Teto de chaves na memória, para uma enxurrada de e-mails diferentes não esgotar o heap. */
    private static final int MAXIMO_DE_CHAVES = 10_000;

    private record Janela(Instant inicio, int tentativas) {

        boolean expirou(Instant agora) {
            return !agora.isBefore(inicio.plus(JANELA));
        }
    }

    private final Clock clock;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    LimiteDeTentativas(Clock clock) {
        this.clock = clock;
    }

    static String chave(String email, String origem) {
        return Textos.normalizarEmail(email) + "|" + origem;
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

    /** Login bem-sucedido: recomeça a contagem desta chave. */
    void limpar(String chave) {
        janelas.remove(chave);
    }

    /** Esquece tudo. Usado pelos testes, que compartilham a mesma instância da aplicação. */
    public void reiniciar() {
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

    private static TentativasDemaisException bloqueio(Duration falta) {
        long minutos = Math.max(1, (falta.toSeconds() + 59) / 60);
        return new TentativasDemaisException(
                "Muitas tentativas de login. Tente de novo em " + Textos.duracao(Duration.ofMinutes(minutos)) + ".", falta);
    }
}
