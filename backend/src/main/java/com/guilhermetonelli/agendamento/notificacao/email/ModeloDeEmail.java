package com.guilhermetonelli.agendamento.notificacao.email;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.notificacao.Quando;

/** Monta o e-mail ao cliente a partir do evento: sem template, só texto e HTML escritos aqui. */
public final class ModeloDeEmail {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private ModeloDeEmail() {}

    public static Email montar(EventoDeAgendamento evento, ZoneId fuso) {
        Aviso aviso = evento.aviso();
        return switch (evento) {
            case AgendamentoCriado e -> email(aviso, "Horário confirmado", "Seu horário está confirmado.", e.inicio(), fuso, Map.of());
            case AgendamentoReagendado e -> email(aviso, "Horário remarcado", "Seu horário foi remarcado.", e.inicio(), fuso,
                    Map.of("Horário anterior", Quando.formatar(e.inicioAnterior(), fuso)));
            case AgendamentoCancelado e -> email(aviso, "Horário cancelado", "Seu horário foi cancelado.", e.inicio(), fuso,
                    e.motivo() == null || e.motivo().isBlank() ? Map.of() : Map.of("Motivo", e.motivo().strip()));
        };
    }

    private static Email email(Aviso aviso, String titulo, String frase, Instant inicio, ZoneId fuso, Map<String, String> extras) {
        String quando = Quando.formatar(inicio, fuso);
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("Serviço", aviso.servico());
        detalhes.put("Barbeiro", aviso.profissional());
        detalhes.put("Quando", quando);
        detalhes.putAll(extras);
        detalhes.put("Valor", "R$ " + String.format(PT_BR, "%,.2f", aviso.preco()));

        // Quebra de linha no assunto abriria a porta para injetar cabeçalhos (Bcc:) no e-mail.
        String assunto = "%s: %s, %s".formatted(titulo, aviso.servico(), quando).replaceAll("\\s+", " ").strip();
        String texto = """
                Olá, %s!

                %s

                %s

                Barbearia Tonelli
                """.formatted(aviso.cliente(), frase, linhas(detalhes, "%s: %s", UnaryOperator.identity()));
        String html = """
                <p>Olá, <strong>%s</strong>!</p>
                <p>%s</p>
                <ul>
                %s
                </ul>
                <p>Barbearia Tonelli</p>
                """.formatted(escapar(aviso.cliente()), frase, linhas(detalhes, "<li><strong>%s:</strong> %s</li>", ModeloDeEmail::escapar));
        return new Email(aviso.emailDoCliente(), assunto, texto, html);
    }

    private static String linhas(Map<String, String> detalhes, String formato, UnaryOperator<String> tratar) {
        return detalhes.entrySet().stream()
                .map(d -> formato.formatted(d.getKey(), tratar.apply(d.getValue())))
                .collect(Collectors.joining("\n"));
    }

    private static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
