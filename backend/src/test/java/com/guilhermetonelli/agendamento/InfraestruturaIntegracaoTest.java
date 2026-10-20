package com.guilhermetonelli.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class InfraestruturaIntegracaoTest {

    @Value("${local.server.port}")
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void healthDeveResponderUp() throws Exception {
        var requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/actuator/health"))
                .GET()
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());

            assertThat(resposta.statusCode()).isEqualTo(200);
            assertThat(resposta.body()).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void migracoesDoFlywayDevemSerAplicadas() {
        Integer extensoes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(extensoes).isEqualTo(1);
    }
}
