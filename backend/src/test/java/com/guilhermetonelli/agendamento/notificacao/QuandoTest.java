package com.guilhermetonelli.agendamento.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class QuandoTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void diaDaSemanaAbreviadoSemPontoEHoraComH() {
        assertThat(Quando.formatar(Instant.parse("2026-10-24T17:30:00Z"), SAO_PAULO)).isEqualTo("sáb 24/10 às 14h30");
    }

    @Test
    void horaSemZeroAEsquerdaEMinutoComDoisDigitos() {
        assertThat(Quando.formatar(Instant.parse("2026-10-21T11:00:00Z"), SAO_PAULO)).isEqualTo("qua 21/10 às 8h00");
    }

    @Test
    void usaOFusoPedidoEPassaDeDia() {
        Instant instante = Instant.parse("2026-10-25T02:30:00Z");

        assertThat(Quando.formatar(instante, SAO_PAULO)).isEqualTo("sáb 24/10 às 23h30");
        assertThat(Quando.formatar(instante, ZoneId.of("UTC"))).isEqualTo("dom 25/10 às 2h30");
    }
}
