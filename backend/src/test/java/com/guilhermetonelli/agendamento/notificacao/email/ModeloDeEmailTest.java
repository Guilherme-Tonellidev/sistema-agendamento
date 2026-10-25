package com.guilhermetonelli.agendamento.notificacao.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/** Os modelos são funções puras: evento e fuso entram, assunto, texto e HTML saem. */
class ModeloDeEmailTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    /** Sábado, 24/10/2026, 14h30 em São Paulo. */
    private static final Instant SABADO = Instant.parse("2026-10-24T17:30:00Z");
    private static final Instant QUARTA = Instant.parse("2026-10-21T11:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Test
    void reservaTemAssuntoDeConfirmacaoEVaiParaOEmailDoCliente() {
        Email email = montar(criado(aviso("Carlos Mendes", "Corte", "Rafael Souza", "45.00")));

        assertThat(email.para()).isEqualTo("carlos@exemplo.com");
        assertThat(email.assunto()).isEqualTo("Horário confirmado: Corte, sáb 24/10 às 14h30");
        assertThat(email.texto()).contains("Carlos Mendes", "Corte", "Rafael Souza", "sáb 24/10 às 14h30");
        assertThat(email.html()).contains("Carlos Mendes", "Corte", "Rafael Souza", "sáb 24/10 às 14h30");
    }

    @Test
    void remarcacaoMostraOHorarioNovoEOAnterior() {
        var evento = new AgendamentoReagendado(ID, ID, ID, QUARTA, SABADO, SABADO.plusSeconds(1800), aviso("Carlos", "Corte", "Rafael", "45.00"));

        Email email = montar(evento);

        assertThat(email.assunto()).isEqualTo("Horário remarcado: Corte, sáb 24/10 às 14h30");
        assertThat(email.texto()).contains("qua 21/10 às 8h00");
        assertThat(email.html()).contains("qua 21/10 às 8h00");
    }

    @Test
    void cancelamentoTemSeuAssunto() {
        Email email = montar(new AgendamentoCancelado(ID, ID, ID, SABADO, null, aviso("Carlos", "Corte", "Rafael", "45.00")));

        assertThat(email.assunto()).isEqualTo("Horário cancelado: Corte, sáb 24/10 às 14h30");
    }

    @Test
    void valorSaiEmReaisNoTextoENoHtml() {
        Email email = montar(criado(aviso("Carlos", "Corte", "Rafael", "45.00")));
        Email caro = montar(criado(aviso("Carlos", "Pacote", "Rafael", "1234.5")));

        assertThat(email.texto()).contains("R$ 45,00");
        assertThat(email.html()).contains("R$ 45,00");
        assertThat(caro.texto()).contains("R$ 1.234,50");
    }

    @Test
    void horarioSaiNoFusoDaBarbearia() {
        var evento = new AgendamentoCriado(ID, ID, ID, Instant.parse("2026-10-25T02:30:00Z"), SABADO, aviso("Carlos", "Corte", "Rafael", "45.00"));

        assertThat(montar(evento).assunto()).endsWith("sáb 24/10 às 23h30");
    }

    @Test
    void nomesSaemEscapadosNoHtmlEIntactosNoTexto() {
        String cliente = "Ana <script>alert(1)</script> & \"Cia\"";
        Email email = montar(criado(aviso(cliente, "Corte", "Rafael <b>", "45.00")));

        assertThat(email.texto()).contains(cliente).contains("Rafael <b>");
        assertThat(email.html())
                .contains("Ana &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Cia&quot;", "Rafael &lt;b&gt;")
                .doesNotContain("<script>", "<b>");
    }

    @Test
    void quebraDeLinhaNoAssuntoNaoPassa() {
        Email email = montar(criado(aviso("Ana\r\nBcc: x@y", "Corte\r\nBcc: x@y", "Rafael", "45.00")));

        assertThat(email.assunto()).doesNotContain("\r", "\n").isEqualTo("Horário confirmado: Corte Bcc: x@y, sáb 24/10 às 14h30");
    }

    @Test
    void cancelamentoComMotivoMostraOMotivoEscapadoNoHtml() {
        var evento = new AgendamentoCancelado(ID, ID, ID, SABADO, "imprevisto <i>", aviso("Carlos", "Corte", "Rafael", "45.00"));

        Email email = montar(evento);

        assertThat(email.texto()).contains("Motivo: imprevisto <i>");
        assertThat(email.html()).contains("Motivo").contains("imprevisto &lt;i&gt;").doesNotContain("<i>");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void cancelamentoSemMotivoNaoMencionaMotivo(String motivo) {
        Email email = montar(new AgendamentoCancelado(ID, ID, ID, SABADO, motivo, aviso("Carlos", "Corte", "Rafael", "45.00")));

        assertThat(email.texto()).doesNotContain("Motivo");
        assertThat(email.html()).doesNotContain("Motivo");
    }

    private static Email montar(com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento evento) {
        return ModeloDeEmail.montar(evento, SAO_PAULO);
    }

    private static AgendamentoCriado criado(Aviso aviso) {
        return new AgendamentoCriado(ID, ID, ID, SABADO, SABADO.plusSeconds(1800), aviso);
    }

    private static Aviso aviso(String cliente, String servico, String profissional, String preco) {
        return new Aviso(Papel.CLIENTE, cliente, "carlos@exemplo.com", profissional, servico, new BigDecimal(preco));
    }
}
