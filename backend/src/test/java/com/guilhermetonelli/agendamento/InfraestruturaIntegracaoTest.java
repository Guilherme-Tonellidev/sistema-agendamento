package com.guilhermetonelli.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InfraestruturaIntegracaoTest extends IntegracaoTest {

    @Test
    void healthDeveResponderUp() {
        var resposta = api.get().uri("/actuator/health").retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void migracoesDoFlywayDevemSerAplicadas() {
        Integer extensoes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(extensoes).isEqualTo(1);
    }

    @Test
    void documentacaoOpenApiDeveEstarDisponivel() {
        var resposta = api.get().uri("/v3/api-docs").retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("/api/profissionais", "/api/servicos", "/api/clientes", "/horarios-livres", "/api/agendamentos");
    }
}
