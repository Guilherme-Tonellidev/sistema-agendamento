package com.guilhermetonelli.agendamento.seguranca;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.shared.exception.TentativasDemaisException;

class LimiteDeTentativasRedisTest extends IntegracaoTest {

    private final String chave = LimiteDeTentativas.chave("carlos@gmail.com", "127.0.0.1");

    @Autowired
    private LimiteDeTentativas limite;

    @Test
    void vinteEmParaleloDeixamPassarExatamenteCinco() throws Exception {
        var largada = new CountDownLatch(1);
        var passaram = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Future<?>> tarefas = IntStream.range(0, 20).<Future<?>>mapToObj(i -> executor.submit(() -> {
                largada.await();
                try {
                    limite.registrarTentativa(chave);
                    passaram.incrementAndGet();
                } catch (TentativasDemaisException e) {
                    // bloqueada: não conta como passou
                }
                return null;
            })).toList();
            largada.countDown();
            for (Future<?> tarefa : tarefas) {
                tarefa.get(10, SECONDS);
            }
        }

        assertThat(passaram).hasValue(5);
    }

    @Test
    void aChaveTemPrazoDeAteQuinzeMinutos() {
        limite.registrarTentativa(chave);

        List<String> chaves = redis.chaves("login:*");

        assertThat(chaves).hasSize(1);
        assertThat(redis.pttl(chaves.getFirst())).isBetween(1L, 900_000L);
    }

    @Test
    void aChaveNaoGuardaOEmail() {
        limite.registrarTentativa(chave);

        assertThat(redis.chaves("*")).singleElement().asString().matches("login:[0-9a-f]{64}");
    }

    @Test
    void chaveSemPrazoRecebePrazoNaTentativaSeguinte() {
        limite.registrarTentativa(chave);
        String chaveRedis = redis.chaves("login:*").getFirst();
        redis.definir(chaveRedis, "2");
        assertThat(redis.pttl(chaveRedis)).isEqualTo(-1L);

        limite.registrarTentativa(chave);

        assertThat(redis.pttl(chaveRedis)).isPositive();
    }

    @Test
    void emailExoticoGeraChaveValida() {
        String exotica = LimiteDeTentativas.chave("a|b:ç@x.com", "::1");

        assertThatCode(() -> limite.registrarTentativa(exotica)).doesNotThrowAnyException();

        assertThat(redis.chaves("login:*")).hasSize(1);
    }

    @Test
    void comORedisPausadoOLoginSegueEOLimiteVaiParaAMemoria() {
        List<Duration> demoras = new ArrayList<>();
        redis.comRedisPausado(() -> {
            for (int i = 0; i < 5; i++) {
                demoras.add(medir(() -> limite.registrarTentativa(chave)));
            }
            demoras.add(medir(() -> assertThatThrownBy(() -> limite.registrarTentativa(chave))
                    .isInstanceOf(TentativasDemaisException.class)));
        });

        assertThat(demoras).hasSize(6).allSatisfy(demora -> assertThat(demora).isLessThan(Duration.ofSeconds(1)));

        // Com o Redis de volta, o limite volta a usá-lo. Os comandos que ficaram pendentes na pausa também chegam,
        // por isso cada tentativa recomeça de um Redis limpo.
        String outra = LimiteDeTentativas.chave("ana@gmail.com", "127.0.0.1");
        await().atMost(15, SECONDS).ignoreExceptions().untilAsserted(() -> {
            redis.limpar();
            limite.registrarTentativa(outra);
            assertThat(redis.chaves("login:*")).hasSize(1);
        });
    }

    private static Duration medir(Runnable acao) {
        long inicio = System.nanoTime();
        acao.run();
        return Duration.ofNanos(System.nanoTime() - inicio);
    }
}
