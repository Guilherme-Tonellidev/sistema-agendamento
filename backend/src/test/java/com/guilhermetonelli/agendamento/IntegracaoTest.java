package com.guilhermetonelli.agendamento;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.seguranca.EmissorDeTokens;
import com.guilhermetonelli.agendamento.seguranca.LimiteDeTentativas;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

/**
 * Base dos testes de integração: aplicação completa numa porta aleatória, PostgreSQL real,
 * relógio controlado, eventos registrados e banco, filas, Redis e caixa de e-mail limpos antes de cada teste.
 *
 * <p>{@link #api} fala como administrador. Os testes de autorização usam {@link #apiComo} e {@link #apiAnonima}.
 *
 * <p>Todas as classes que estendem esta compartilham o mesmo contexto do Spring e o mesmo
 * container, então PostgreSQL, RabbitMQ, Redis e Mailpit sobem uma vez só por execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// A aplicação não tem mais perfil padrão: o local dá a chave JWT e os dados de exemplo que estes testes usam.
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
    "spring.rabbitmq.listener.simple.retry.multiplier=1",
    "spring.rabbitmq.listener.simple.retry.max-interval=50ms",
    // O gatilho de 1 minuto mandaria e-mails no meio de testes que não os esperam; quem testa o Quartz liga o agendador.
    "spring.quartz.auto-startup=false"
})
@Import({TestcontainersConfiguration.class, RelogioDeTeste.class, EventosPublicados.class, Filas.class, Mailpit.class, RedisDeTeste.class, EnviadorQueFalha.class})
public abstract class IntegracaoTest {

    @Value("${local.server.port}")
    private int porta;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RelogioDeTeste relogio;

    @Autowired
    protected EventosPublicados eventos;

    @Autowired
    protected Filas filas;

    @Autowired
    protected Mailpit mailpit;

    @Autowired
    protected RedisDeTeste redis;

    @Autowired
    protected EnviadorQueFalha enviador;

    @Autowired
    private EmissorDeTokens emissor;

    @Autowired
    private LimiteDeTentativas limiteDeLogin;

    /** Cliente HTTP da API como administrador. Respostas de erro não lançam exceção, para o teste conferir o status. */
    protected RestClient api;

    @BeforeEach
    protected void prepararAmbiente() {
        enviador.falharSempre(false);
        filas.pararConsumidores();
        limparBanco();
        filas.purgar();
        filas.religarConsumidores();
        mailpit.limpar();
        relogio.reiniciar();
        // O contexto é compartilhado: sem isso, as tentativas de login e o cache de um teste valeriam para o seguinte.
        redis.limpar();
        limiteDeLogin.reiniciar();
        eventos.limpar();
        api = apiComo(new UsuarioLogado(UUID.randomUUID(), Papel.ADMIN, null, null));
    }

    protected static Duration medir(Runnable acao) {
        long inicio = System.nanoTime();
        acao.run();
        return Duration.ofNanos(System.nanoTime() - inicio);
    }

    /** Roda {@code acao} em {@code tarefas} threads largando juntas e devolve quanto cada uma demorou. */
    protected static List<Duration> demorasEmParalelo(int tarefas, IntConsumer acao) {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tarefas)) {
            List<Future<Duration>> execucoes = IntStream.range(0, tarefas)
                    .<Future<Duration>>mapToObj(i -> executor.submit(() -> {
                        largada.await();
                        return medir(() -> acao.accept(i));
                    }))
                    .toList();
            largada.countDown();
            return execucoes.stream().map(IntegracaoTest::esperar).toList();
        }
    }

    private static Duration esperar(Future<Duration> execucao) {
        try {
            return execucao.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cada requisição leva um token novo, emitido no horário atual do relógio de teste. */
    protected RestClient apiComo(UsuarioLogado usuario) {
        return construtor()
                .requestInterceptor((requisicao, corpo, execucao) -> {
                    requisicao.getHeaders().setBearerAuth(emissor.emitir(usuario).tokenDeAcesso());
                    return execucao.execute(requisicao, corpo);
                })
                .build();
    }

    protected RestClient apiAnonima() {
        return construtor().build();
    }

    protected static UsuarioLogado comoCliente(UUID clienteId) {
        return new UsuarioLogado(UUID.randomUUID(), Papel.CLIENTE, null, clienteId);
    }

    protected static UsuarioLogado comoProfissional(UUID profissionalId) {
        return new UsuarioLogado(UUID.randomUUID(), Papel.PROFISSIONAL, profissionalId, null);
    }

    private RestClient.Builder construtor() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + porta)
                .defaultStatusHandler(HttpStatusCode::isError, (requisicao, resposta) -> {});
    }

    private void limparBanco() {
        List<String> tabelas = jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                  AND tablename NOT LIKE 'qrtz!_%' ESCAPE '!'
                """, String.class);
        if (!tabelas.isEmpty()) {
            jdbc.execute("TRUNCATE " + String.join(", ", tabelas) + " CASCADE");
        }
    }
}
