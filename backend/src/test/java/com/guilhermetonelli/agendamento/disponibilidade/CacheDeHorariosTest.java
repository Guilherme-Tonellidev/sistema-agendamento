package com.guilhermetonelli.agendamento.disponibilidade;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.transaction.support.TransactionTemplate;

import com.guilhermetonelli.agendamento.IntegracaoTest;

class CacheDeHorariosTest extends IntegracaoTest {

    private static final Periodo SETE = periodo("2026-10-21T10:00:00Z");
    private static final Periodo OITO = periodo("2026-10-21T11:00:00Z");
    private static final Periodo NOVE = periodo("2026-10-21T12:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 21);

    @Autowired
    private CacheDeHorarios cache;

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private LettuceConnectionFactory conexoes;

    private final UUID prof = UUID.randomUUID();
    private final AtomicInteger chamadas = new AtomicInteger();
    private final Supplier<List<Periodo>> calculo = () -> {
        chamadas.incrementAndGet();
        return List.of(OITO);
    };

    @Test
    void escreverELerPreservaOrdemEListaVazia() {
        assertThat(CacheDeHorarios.escrever(List.of())).isEmpty();
        assertThat(CacheDeHorarios.ler("")).isEmpty();
        assertThat(CacheDeHorarios.escrever(List.of(SETE, OITO)))
                .isEqualTo("%d,%d;%d,%d".formatted(SETE.inicio().getEpochSecond(), SETE.fim().getEpochSecond(),
                        OITO.inicio().getEpochSecond(), OITO.fim().getEpochSecond()));
        for (List<Periodo> lista : List.of(List.of(OITO), List.of(NOVE, SETE, OITO))) {
            assertThat(CacheDeHorarios.ler(CacheDeHorarios.escrever(lista))).isEqualTo(lista);
        }
        assertThatThrownBy(() -> CacheDeHorarios.ler("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void segundaChamadaNaoExecutaOCalculo() {
        var primeira = cache.livres(prof, 30, DIA, calculo);
        var segunda = cache.livres(prof, 30, DIA, calculo);

        assertThat(chamadas).hasValue(1);
        assertThat(segunda).isEqualTo(primeira).containsExactly(OITO);
    }

    @Test
    void duracaoOuDataDiferentesNaoCompartilham() {
        cache.livres(prof, 30, DIA, calculo);
        cache.livres(prof, 45, DIA, calculo);
        cache.livres(prof, 30, DIA.plusDays(1), calculo);

        assertThat(chamadas).hasValue(3);
        assertThat(redis.chaves("horarios:*")).hasSize(3);
    }

    @Test
    void semVersaoOCacheCriaUmaEUsaNaChave() {
        cache.livres(prof, 30, DIA, calculo);

        assertThat(versao()).isNotNull().isNotEqualTo("0");
        assertThat(redis.chaves("horarios:*")).containsExactly("horarios:%s:%s:30:2026-10-21".formatted(prof, versao()));
        assertThat(redis.pttl(CacheDeHorarios.chaveDaVersao(prof))).isBetween(1L, 3_600_000L);
    }

    @Test
    void versaoPerdidaTrocaDeToken() {
        cache.livres(prof, 30, DIA, calculo);
        String antes = versao();

        redis.apagar(CacheDeHorarios.chaveDaVersao(prof));
        cache.livres(prof, 30, DIA, calculo);

        assertThat(versao()).isNotNull().isNotEqualTo(antes);
        assertThat(chamadas).hasValue(2);
    }

    @Test
    void invalidarFazOCalculoRodarDeNovo() {
        cache.livres(prof, 30, DIA, calculo);

        cache.invalidar(prof);
        cache.livres(prof, 30, DIA, calculo);

        assertThat(chamadas).hasValue(2);
    }

    @Test
    void eventoDeTransacaoRevertidaNaoTrocaAVersao() {
        cache.invalidar(prof);
        String antes = versao();

        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            eventos.publishEvent(new AgendaAlterada(prof));
            throw new IllegalStateException("desfaz");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(versao()).isEqualTo(antes);
    }

    @Test
    void eventoSoTrocaAVersaoDepoisDoCommit() {
        cache.invalidar(prof);
        String antes = versao();

        transacao.executeWithoutResult(status -> {
            eventos.publishEvent(new AgendaAlterada(prof));
            assertThat(versao()).isEqualTo(antes);
        });

        assertThat(versao()).isNotEqualTo(antes);
    }

    @Test
    void eventoForaDeTransacaoTrocaAVersao() {
        cache.invalidar(prof);
        String antes = versao();

        eventos.publishEvent(new AgendaAlterada(prof));

        assertThat(versao()).isNotEqualTo(antes);
    }

    @Test
    void osPrazosSaoOsDaSpec() {
        cache.invalidar(prof);
        assertThat(redis.pttl(CacheDeHorarios.chaveDaVersao(prof))).isBetween(1L, 3_600_000L);

        cache.livres(prof, 30, DIA, calculo);

        assertThat(redis.pttl(redis.chaves("horarios:*").getFirst())).isBetween(1L, 120_000L);
    }

    @Test
    void comORedisPausadoOCalculoRodaENadaLanca() {
        List<Duration> demoras = new ArrayList<>();
        List<List<Periodo>> resultados = new ArrayList<>();
        redis.comRedisPausado(() -> {
            demoras.add(medir(() -> resultados.add(cache.livres(prof, 30, DIA, calculo))));
            demoras.add(medir(() -> cache.invalidar(prof)));
        });

        assertThat(resultados).containsExactly(List.of(OITO));
        assertThat(demoras).hasSize(2).allSatisfy(demora -> assertThat(demora).isLessThan(Duration.ofSeconds(1)));
    }

    @Test
    void semConexaoERedisTravadoSoAPrimeiraChamadaPagaOTempo() {
        conexoes.resetConnection();
        List<Duration> demoras = new ArrayList<>();

        redis.comRedisPausado(() -> {
            demoras.add(medir(() -> cache.livres(prof, 30, DIA, calculo)));
            demoras.addAll(demorasEmParalelo(8, i -> cache.livres(prof, 30, DIA.plusDays(i), calculo)));
            demoras.add(medir(() -> cache.invalidar(prof)));
        });

        assertThat(demoras).hasSize(10).allSatisfy(demora -> assertThat(demora).isLessThan(Duration.ofSeconds(1)));
    }

    @Test
    void valorCorrompidoViraMiss() {
        cache.livres(prof, 30, DIA, calculo);
        String chave = redis.chaves("horarios:*").getFirst();
        redis.definir(chave, "lixo");

        var horarios = cache.livres(prof, 30, DIA, calculo);

        assertThat(chamadas).hasValue(2);
        assertThat(horarios).containsExactly(OITO);
        assertThat(redis.valor(chave)).isEqualTo(CacheDeHorarios.escrever(List.of(OITO)));
    }

    @Test
    void excecaoDoCalculoSobe() {
        assertThatThrownBy(() -> cache.livres(prof, 30, DIA, () -> {
            throw new IllegalStateException("banco fora do ar");
        })).isInstanceOf(IllegalStateException.class).hasMessage("banco fora do ar");
    }

    @Test
    void doisLeitoresNoMissNaoDaoErro() throws Exception {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Future<List<Periodo>>> leituras = IntStream.range(0, 10)
                    .<Future<List<Periodo>>>mapToObj(i -> executor.submit(() -> {
                        largada.await();
                        return cache.livres(prof, 30, DIA, calculo);
                    }))
                    .toList();
            largada.countDown();
            for (Future<List<Periodo>> leitura : leituras) {
                assertThat(leitura.get(10, SECONDS)).containsExactly(OITO);
            }
        }

        assertThat(redis.chaves("horarios:*")).hasSize(1);
    }

    private String versao() {
        return redis.valor(CacheDeHorarios.chaveDaVersao(prof));
    }

    private static Periodo periodo(String inicio) {
        Instant i = Instant.parse(inicio);
        return new Periodo(i, i.plus(Duration.ofMinutes(30)));
    }
}
