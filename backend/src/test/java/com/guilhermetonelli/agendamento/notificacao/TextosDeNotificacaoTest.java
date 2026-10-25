package com.guilhermetonelli.agendamento.notificacao;

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

/** A frase que o barbeiro lê no sino: quem fez o quê, em que horário da barbearia. */
class TextosDeNotificacaoTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    /** Sábado, 24/10/2026, 14h30 em São Paulo. */
    private static final Instant SABADO = Instant.parse("2026-10-24T17:30:00Z");
    /** Domingo, 25/10/2026, 10h00 em São Paulo. */
    private static final Instant DOMINGO = Instant.parse("2026-10-25T13:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Test
    void clienteReservou() {
        assertThat(texto(new AgendamentoCriado(ID, ID, ID, SABADO, SABADO, aviso(Papel.CLIENTE))))
                .isEqualTo("Carlos Mendes reservou Corte para sáb 24/10 às 14h30.");
    }

    @Test
    void clienteRemarcou() {
        assertThat(texto(new AgendamentoReagendado(ID, ID, ID, SABADO, DOMINGO, DOMINGO, aviso(Papel.CLIENTE))))
                .isEqualTo("Carlos Mendes remarcou Corte de sáb 24/10 às 14h30 para dom 25/10 às 10h00.");
    }

    @Test
    void clienteCancelouComMotivo() {
        assertThat(texto(new AgendamentoCancelado(ID, ID, ID, SABADO, "imprevisto", aviso(Papel.CLIENTE))))
                .isEqualTo("Carlos Mendes cancelou Corte de sáb 24/10 às 14h30. Motivo: imprevisto.");
    }

    @Test
    void motivoQueJaTerminaEmPontuacaoNaoGanhaOutroPonto() {
        assertThat(texto(new AgendamentoCancelado(ID, ID, ID, SABADO, "Preciso viajar.", aviso(Papel.CLIENTE))))
                .endsWith("Motivo: Preciso viajar.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void cancelamentoSemMotivoNaoMencionaMotivo(String motivo) {
        assertThat(texto(new AgendamentoCancelado(ID, ID, ID, SABADO, motivo, aviso(Papel.CLIENTE))))
                .isEqualTo("Carlos Mendes cancelou Corte de sáb 24/10 às 14h30.");
    }

    @Test
    void quandoQuemFezFoiOAdministradorOSujeitoEAAdministracao() {
        assertThat(texto(new AgendamentoCriado(ID, ID, ID, SABADO, SABADO, aviso(Papel.ADMIN))))
                .isEqualTo("A administração reservou o Corte de Carlos Mendes para sáb 24/10 às 14h30.");
        assertThat(texto(new AgendamentoReagendado(ID, ID, ID, SABADO, DOMINGO, DOMINGO, aviso(Papel.ADMIN))))
                .isEqualTo("A administração remarcou o Corte de Carlos Mendes de sáb 24/10 às 14h30 para dom 25/10 às 10h00.");
        assertThat(texto(new AgendamentoCancelado(ID, ID, ID, SABADO, "imprevisto", aviso(Papel.ADMIN))))
                .isEqualTo("A administração cancelou o Corte de Carlos Mendes de sáb 24/10 às 14h30. Motivo: imprevisto.");
    }

    @Test
    void horarioPertoDaMeiaNoiteEscritoNoDiaLocal() {
        Instant tarde = Instant.parse("2026-10-25T02:30:00Z");

        assertThat(texto(new AgendamentoCriado(ID, ID, ID, tarde, tarde, aviso(Papel.CLIENTE)))).endsWith("para sáb 24/10 às 23h30.");
    }

    private static String texto(com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento evento) {
        return TextosDeNotificacao.de(evento, SAO_PAULO);
    }

    private static Aviso aviso(Papel autor) {
        return new Aviso(autor, "Carlos Mendes", "carlos@exemplo.com", "Rafael Souza", "Corte", new BigDecimal("45.00"));
    }
}
