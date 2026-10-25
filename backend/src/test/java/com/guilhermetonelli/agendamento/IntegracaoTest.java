package com.guilhermetonelli.agendamento;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.seguranca.EmissorDeTokens;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

/**
 * Base dos testes de integração: aplicação completa numa porta aleatória, PostgreSQL real,
 * relógio controlado, eventos registrados e banco, filas e caixa de e-mail limpos antes de cada teste.
 *
 * <p>{@link #api} fala como administrador. Os testes de autorização usam {@link #apiComo} e {@link #apiAnonima}.
 *
 * <p>Todas as classes que estendem esta compartilham o mesmo contexto do Spring e o mesmo
 * container, então PostgreSQL, RabbitMQ e Mailpit sobem uma vez só por execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
    "spring.rabbitmq.listener.simple.retry.multiplier=1",
    "spring.rabbitmq.listener.simple.retry.max-interval=50ms"
})
@Import({TestcontainersConfiguration.class, RelogioDeTeste.class, EventosPublicados.class, Filas.class, Mailpit.class, EnviadorQueFalha.class})
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
    protected EnviadorQueFalha enviador;

    @Autowired
    private EmissorDeTokens emissor;

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
        eventos.limpar();
        api = apiComo(new UsuarioLogado(UUID.randomUUID(), Papel.ADMIN, null, null));
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
                """, String.class);
        if (!tabelas.isEmpty()) {
            jdbc.execute("TRUNCATE " + String.join(", ", tabelas) + " CASCADE");
        }
    }
}
