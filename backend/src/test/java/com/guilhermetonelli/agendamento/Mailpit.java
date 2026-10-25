package com.guilhermetonelli.agendamento;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/** Apoio dos testes ao Mailpit (o servidor de e-mail de mentira), pela API REST dele. */
public class Mailpit {

    public record EmailRecebido(String remetente, String assunto, String texto, String html) {}

    private final RestClient http;

    Mailpit(@Value("${mailpit.url}") String url) {
        this.http = RestClient.builder().baseUrl(url).build();
    }

    public void limpar() {
        http.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
    }

    /** Quantos e-mails a caixa tem, de qualquer destinatário. */
    public int quantidade() {
        return http.get().uri("/api/v1/messages?limit=1").retrieve().body(JsonNode.class).path("total").asInt();
    }

    /** Os e-mails já recebidos por este destinatário. */
    public List<EmailRecebido> para(String destinatario) {
        JsonNode busca = http.get()
                .uri(uri -> uri.path("/api/v1/search").queryParam("query", "to:" + destinatario).build())
                .retrieve()
                .body(JsonNode.class);
        return busca.path("messages").valueStream().map(m -> ler(m.path("ID").asString())).toList();
    }

    /** Espera até 10 s por pelo menos essa quantidade de e-mails para o destinatário. */
    public List<EmailRecebido> aguardar(String destinatario, int quantidade) {
        await().atMost(Duration.ofSeconds(10)).until(() -> para(destinatario).size() >= quantidade);
        return para(destinatario);
    }

    private EmailRecebido ler(String id) {
        JsonNode m = http.get().uri("/api/v1/message/{id}", id).retrieve().body(JsonNode.class);
        return new EmailRecebido(
                m.path("From").path("Address").asString(),
                m.path("Subject").asString(),
                m.path("Text").asString(),
                m.path("HTML").asString());
    }
}
