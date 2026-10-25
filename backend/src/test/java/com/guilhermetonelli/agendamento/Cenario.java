package com.guilhermetonelli.agendamento;

import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.CancelamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.EncaixeRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.jayway.jsonpath.JsonPath;

/**
 * O que os testes de aviso repetem: um serviço de 30 minutos (R$ 45,00), barbeiros com jornada na quarta,
 * 21/10/2026, das 8h às 12h em São Paulo, e clientes com e-mail único por teste (um e-mail atrasado de
 * outro teste não se confunde com o deste). Cadastra como administrador; as ações recebem quem as faz.
 */
public class Cenario {

    public static final String AS_8H = "2026-10-21T11:00:00Z";
    public static final String AS_8H30 = "2026-10-21T11:30:00Z";
    public static final String AS_9H = "2026-10-21T12:00:00Z";

    public record Cadastro(UUID id, String email) {}

    private final RestClient admin;
    private final UUID corte;

    public Cenario(RestClient admin) {
        this.admin = admin;
        this.corte = admin.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Corte", null, 30, new BigDecimal("45.00")))
                .retrieve()
                .body(ServicoResponse.class)
                .id();
    }

    public UUID barbeiro(String nome) {
        UUID id = admin.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest(nome, emailUnico(nome), null))
                .retrieve()
                .body(ProfissionalResponse.class)
                .id();
        admin.put().uri("/api/profissionais/{id}/servicos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicosDoProfissionalRequest(Set.of(corte)))
                .retrieve()
                .toBodilessEntity();
        admin.put().uri("/api/profissionais/{id}/jornada", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JornadaDto(List.of(new IntervaloDeJornadaDto(WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)))))
                .retrieve()
                .toBodilessEntity();
        return id;
    }

    public Cadastro cliente(String nome) {
        String email = emailUnico(nome);
        UUID id = admin.post().uri("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ClienteRequest(nome, email, null))
                .retrieve()
                .body(ClienteResponse.class)
                .id();
        return new Cadastro(id, email);
    }

    /** Reserva como {@code quem} e devolve o id do agendamento. */
    public UUID reservar(RestClient quem, UUID barbeiro, UUID cliente, String inicio) {
        ResponseEntity<String> resposta = quem.post().uri("/api/agendamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(barbeiro, corte, cliente, Instant.parse(inicio)))
                .retrieve()
                .toEntity(String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(resposta.getBody(), "$.id"));
    }

    public void encaixar(RestClient quem, UUID barbeiro, String inicio, String nomeDoCliente) {
        var resposta = quem.post().uri("/api/agendamentos/encaixe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EncaixeRequest(barbeiro, corte, Instant.parse(inicio), nomeDoCliente, null))
                .retrieve()
                .toBodilessEntity();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    public void reagendar(RestClient quem, UUID agendamento, String inicio) {
        var resposta = quem.patch().uri("/api/agendamentos/{id}/reagendar", agendamento)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReagendamentoRequest(Instant.parse(inicio)))
                .retrieve()
                .toBodilessEntity();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    public void cancelar(RestClient quem, UUID agendamento, String motivo) {
        var resposta = quem.patch().uri("/api/agendamentos/{id}/cancelar", agendamento)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelamentoRequest(motivo))
                .retrieve()
                .toBodilessEntity();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static String emailUnico(String nome) {
        return nome.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
    }
}
