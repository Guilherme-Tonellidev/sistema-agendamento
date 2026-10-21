package com.guilhermetonelli.agendamento;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Base dos testes de integração: aplicação completa numa porta aleatória, PostgreSQL real
 * e banco limpo antes de cada teste.
 *
 * <p>Todas as classes que estendem esta compartilham o mesmo contexto do Spring e o mesmo
 * container, então o PostgreSQL sobe uma vez só por execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class IntegracaoTest {

    @Value("${local.server.port}")
    private int porta;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Cliente HTTP da API. Respostas de erro não lançam exceção, para o teste conferir o status. */
    protected RestClient api;

    @BeforeEach
    protected void prepararAmbiente() {
        limparBanco();
        api = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + porta)
                .defaultStatusHandler(HttpStatusCode::isError, (requisicao, resposta) -> {})
                .build();
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
