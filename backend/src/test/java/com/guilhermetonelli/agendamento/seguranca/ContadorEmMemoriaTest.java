package com.guilhermetonelli.agendamento.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.guilhermetonelli.agendamento.RelogioDeTeste;
import com.guilhermetonelli.agendamento.shared.exception.TentativasDemaisException;

class ContadorEmMemoriaTest {

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final ContadorEmMemoria contador = new ContadorEmMemoria(relogio);

    @Test
    void cincoPassamASextaBloqueiaComQuinzeMinutos() {
        tentar("ana", 5);

        assertThatThrownBy(() -> contador.registrarTentativa("ana"))
                .isInstanceOfSatisfying(TentativasDemaisException.class,
                        e -> assertThat(e.getEsperar()).isEqualTo(Duration.ofMinutes(15)));
    }

    @Test
    void voltaDepoisDeQuinzeMinutos() {
        tentar("ana", 5);
        assertThatThrownBy(() -> contador.registrarTentativa("ana")).isInstanceOf(TentativasDemaisException.class);

        relogio.ajustar(RelogioDeTeste.INICIAL.plus(Duration.ofMinutes(15)));

        assertThatCode(() -> contador.registrarTentativa("ana")).doesNotThrowAnyException();
    }

    @Test
    void limparZeraAContagem() {
        tentar("ana", 4);

        contador.limpar("ana");

        tentar("ana", 5);
    }

    @Test
    void oTetoDeChavesEsquecePrimeiroAMaisAntiga() {
        tentar("chave-0", 5);
        for (int i = 1; i < 10_000; i++) {
            relogio.ajustar(RelogioDeTeste.INICIAL.plusMillis(i));
            contador.registrarTentativa("chave-" + i);
        }
        // 10 000 chaves: ainda cabem todas, e a primeira continua bloqueada.
        assertThatThrownBy(() -> contador.registrarTentativa("chave-0")).isInstanceOf(TentativasDemaisException.class);

        relogio.ajustar(RelogioDeTeste.INICIAL.plusMillis(10_000));
        contador.registrarTentativa("chave-10000");

        assertThatCode(() -> contador.registrarTentativa("chave-0")).doesNotThrowAnyException();
    }

    private void tentar(String chave, int vezes) {
        for (int i = 0; i < vezes; i++) {
            contador.registrarTentativa(chave);
        }
    }
}
