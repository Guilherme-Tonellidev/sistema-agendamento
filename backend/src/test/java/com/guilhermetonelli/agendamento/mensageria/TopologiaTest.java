package com.guilhermetonelli.agendamento.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.agendamento.evento.LembreteDeAgendamento;

class TopologiaTest {

    @Test
    void lembreteUsaAChaveAgendamentoLembrete() {
        UUID id = UUID.randomUUID();
        var aviso = new Aviso(null, "Carlos Mendes", "carlos@exemplo.com", "Rafael Souza", "Corte", new BigDecimal("45.00"));

        String chave = Topologia.chaveDe(new LembreteDeAgendamento(id, id, id, Instant.parse("2026-10-24T17:30:00Z"), aviso));

        assertThat(chave).isEqualTo("agendamento.lembrete");
    }
}
