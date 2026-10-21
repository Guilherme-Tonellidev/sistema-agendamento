package com.guilhermetonelli.agendamento.servico;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Pagina;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.jayway.jsonpath.JsonPath;

class ServicoApiTest extends IntegracaoTest {

    @Test
    void cadastraServicoERetornaLocalizacao() {
        var resposta = cadastrar(new ServicoRequest("  Corte de cabelo  ", "Máquina e tesoura", 30, new BigDecimal("45.00")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ServicoResponse servico = resposta.getBody();
        assertThat(servico.id()).isNotNull();
        assertThat(servico.nome()).isEqualTo("Corte de cabelo");
        assertThat(servico.duracaoMinutos()).isEqualTo(30);
        assertThat(servico.preco()).isEqualByComparingTo("45.00");
        assertThat(servico.ativo()).isTrue();
        assertThat(resposta.getHeaders().getLocation()).hasPath("/api/servicos/" + servico.id());
    }

    @Test
    void recusaDadosInvalidosListandoOsCampos() {
        var resposta = api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("", null, 2, new BigDecimal("-1")))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<String> campos = JsonPath.read(resposta.getBody(), "$.erros[*].campo");
        assertThat(campos).containsExactly("duracaoMinutos", "nome", "preco");
    }

    @Test
    void recusaNomeRepetidoMesmoComMaiusculasDiferentes() {
        cadastrar(new ServicoRequest("Barba", null, 20, new BigDecimal("30")));

        var resposta = api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("BARBA", null, 20, new BigDecimal("30")))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void atualizaServico() {
        UUID id = cadastrar(new ServicoRequest("Barba", null, 20, new BigDecimal("30"))).getBody().id();

        var resposta = api.put().uri("/api/servicos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Barba completa", "Com toalha quente", 40, new BigDecimal("55.90")))
                .retrieve()
                .toEntity(ServicoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().nome()).isEqualTo("Barba completa");
        assertThat(resposta.getBody().duracaoMinutos()).isEqualTo(40);
        assertThat(resposta.getBody().preco()).isEqualByComparingTo("55.90");
    }

    @Test
    void inativaServicoEFiltraPorSituacao() {
        UUID barba = cadastrar(new ServicoRequest("Barba", null, 20, new BigDecimal("30"))).getBody().id();
        cadastrar(new ServicoRequest("Corte", null, 30, new BigDecimal("45")));

        var inativacao = api.patch().uri("/api/servicos/{id}/inativar", barba).retrieve().toBodilessEntity();
        Pagina<ServicoResponse> inativos = listar("/api/servicos?ativo=false");

        assertThat(inativacao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(inativos.content()).extracting(ServicoResponse::nome).containsExactly("Barba");
    }

    @Test
    void buscaPorTrechoDoNomeComPaginacao() {
        cadastrar(new ServicoRequest("Corte masculino", null, 30, new BigDecimal("45")));
        cadastrar(new ServicoRequest("Corte infantil", null, 25, new BigDecimal("35")));
        cadastrar(new ServicoRequest("Barba", null, 20, new BigDecimal("30")));

        Pagina<ServicoResponse> pagina = listar("/api/servicos?nome=CORTE&size=1");

        assertThat(pagina.page().totalElements()).isEqualTo(2);
        assertThat(pagina.content()).extracting(ServicoResponse::nome).containsExactly("Corte infantil");
    }

    @Test
    void recusaOrdenacaoPorCampoInexistente() {
        var resposta = api.get().uri("/api/servicos?sort=senha").retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void retorna404ParaServicoInexistente() {
        var resposta = api.get().uri("/api/servicos/{id}", UUID.randomUUID()).retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getHeaders().getContentType()).isNotNull()
                .matches(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith, "application/problem+json");
    }

    private ResponseEntity<ServicoResponse> cadastrar(ServicoRequest request) {
        return api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ServicoResponse.class);
    }

    private Pagina<ServicoResponse> listar(String uri) {
        return api.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
    }
}
