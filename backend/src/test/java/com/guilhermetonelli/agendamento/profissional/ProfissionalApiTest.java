package com.guilhermetonelli.agendamento.profissional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Pagina;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResumoResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResumoResponse;

class ProfissionalApiTest extends IntegracaoTest {

    @Test
    void cadastraProfissionalComEmailNormalizado() {
        var resposta = cadastrar(new ProfissionalRequest("Ana Souza", "  Ana@Barbearia.COM ", "14999998888"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().email()).isEqualTo("ana@barbearia.com");
        assertThat(resposta.getBody().ativo()).isTrue();
        assertThat(resposta.getBody().servicos()).isEmpty();
    }

    @Test
    void recusaEmailJaCadastrado() {
        cadastrar(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null));

        var resposta = api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest("Ana Lima", "ANA@barbearia.com", null))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recusaTelefoneEmFormatoInvalido() {
        var resposta = api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", "(14) 99999-8888"))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void defineServicosAtendidosEmOrdemAlfabetica() {
        UUID ana = cadastrar(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null)).getBody().id();
        UUID corte = cadastrarServico("Corte");
        UUID barba = cadastrarServico("Barba");

        var resposta = definirServicos(ana, Set.of(corte, barba)).toEntity(ProfissionalResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().servicos()).extracting(ServicoResumoResponse::nome).containsExactly("Barba", "Corte");
    }

    @Test
    void recusaVincularServicoInativo() {
        UUID ana = cadastrar(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null)).getBody().id();
        UUID barba = cadastrarServico("Barba");
        api.patch().uri("/api/servicos/{id}/inativar", barba).retrieve().toBodilessEntity();

        var resposta = definirServicos(ana, Set.of(barba)).toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void recusaVincularServicoInexistente() {
        UUID ana = cadastrar(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null)).getBody().id();

        var resposta = definirServicos(ana, Set.of(UUID.randomUUID())).toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void filtraProfissionaisPorServicoAtendido() {
        UUID ana = cadastrar(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null)).getBody().id();
        cadastrar(new ProfissionalRequest("Bruno Dias", "bruno@barbearia.com", null));
        UUID barba = cadastrarServico("Barba");
        definirServicos(ana, Set.of(barba)).toBodilessEntity();

        Pagina<ProfissionalResumoResponse> pagina = api.get()
                .uri("/api/profissionais?servicoId={id}", barba)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        assertThat(pagina.content()).extracting(ProfissionalResumoResponse::nome).containsExactly("Ana Souza");
    }

    @Test
    void atualizaDadosMantendoOsServicos() {
        UUID ana = cadastrar(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null)).getBody().id();
        definirServicos(ana, Set.of(cadastrarServico("Barba"))).toBodilessEntity();

        var resposta = api.put().uri("/api/profissionais/{id}", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest("Ana Souza Lima", "ana.lima@barbearia.com", "14999998888"))
                .retrieve()
                .toEntity(ProfissionalResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().nome()).isEqualTo("Ana Souza Lima");
        assertThat(resposta.getBody().email()).isEqualTo("ana.lima@barbearia.com");
        assertThat(resposta.getBody().servicos()).hasSize(1);
    }

    private ResponseEntity<ProfissionalResponse> cadastrar(ProfissionalRequest request) {
        return api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ProfissionalResponse.class);
    }

    private UUID cadastrarServico(String nome) {
        return api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest(nome, null, 30, new BigDecimal("40")))
                .retrieve()
                .body(ServicoResponse.class)
                .id();
    }

    private RestClient.ResponseSpec definirServicos(UUID profissional, Set<UUID> servicos) {
        return api.put().uri("/api/profissionais/{id}/servicos", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicosDoProfissionalRequest(servicos))
                .retrieve();
    }
}
