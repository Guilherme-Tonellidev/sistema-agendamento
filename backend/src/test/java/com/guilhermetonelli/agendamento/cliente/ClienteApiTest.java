package com.guilhermetonelli.agendamento.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Pagina;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;

class ClienteApiTest extends IntegracaoTest {

    @Test
    void cadastraEBuscaCliente() {
        UUID id = cadastrar(new ClienteRequest("Carlos Lima", "Carlos@Gmail.com", "14988887777")).getBody().id();

        var resposta = api.get().uri("/api/clientes/{id}", id).retrieve().toEntity(ClienteResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo(new ClienteResponse(id, "Carlos Lima", "carlos@gmail.com", "14988887777"));
    }

    @Test
    void recusaEmailJaCadastrado() {
        cadastrar(new ClienteRequest("Carlos Lima", "carlos@gmail.com", null));

        var resposta = api.post().uri("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ClienteRequest("Carlos Souza", "carlos@gmail.com", null))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void permiteAtualizarMantendoOProprioEmail() {
        UUID id = cadastrar(new ClienteRequest("Carlos Lima", "carlos@gmail.com", null)).getBody().id();

        var resposta = api.put().uri("/api/clientes/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ClienteRequest("Carlos Lima Junior", "carlos@gmail.com", null))
                .retrieve()
                .toEntity(ClienteResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().nome()).isEqualTo("Carlos Lima Junior");
    }

    @Test
    void buscaTratandoCuringasComoTextoComum() {
        cadastrar(new ClienteRequest("Carlos 100% Lima", "carlos@gmail.com", null));
        cadastrar(new ClienteRequest("Carla Dias", "carla@gmail.com", null));

        Pagina<ClienteResponse> pagina = api.get()
                .uri("/api/clientes?nome={termo}", "%")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        assertThat(pagina.content()).extracting(ClienteResponse::nome).containsExactly("Carlos 100% Lima");
    }

    private ResponseEntity<ClienteResponse> cadastrar(ClienteRequest request) {
        return api.post().uri("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ClienteResponse.class);
    }
}
