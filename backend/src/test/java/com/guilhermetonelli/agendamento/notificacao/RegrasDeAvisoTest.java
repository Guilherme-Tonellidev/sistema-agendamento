package com.guilhermetonelli.agendamento.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.agendamento.evento.EventoDeAgendamento;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/** Quem agiu já sabe; a reserva é a exceção para o cliente, que recebe o comprovante. */
class RegrasDeAvisoTest {

    private static final Instant INICIO = Instant.parse("2026-10-24T17:30:00Z");

    @ParameterizedTest(name = "{0} feito por {1}, com e-mail {2}: avisa cliente {3}, barbeiro {4}")
    @CsvSource({
        "CRIADO,CLIENTE,true,true,true",
        "CRIADO,PROFISSIONAL,true,true,false",
        "CRIADO,PROFISSIONAL,false,false,false",
        "CRIADO,ADMIN,true,true,true",
        "REMARCADO,CLIENTE,true,false,true",
        "REMARCADO,PROFISSIONAL,true,true,false",
        "REMARCADO,ADMIN,true,true,true",
        "CANCELADO,CLIENTE,true,false,true",
        "CANCELADO,PROFISSIONAL,true,true,false",
        "CANCELADO,PROFISSIONAL,false,false,false",
        "CANCELADO,ADMIN,true,true,true",
        "CANCELADO,ADMIN,false,false,true"
    })
    void avisaQuemNaoAgiu(String tipo, Papel autor, boolean comEmail, boolean avisaCliente, boolean avisaProfissional) {
        Aviso aviso = new Aviso(
                autor, "Carlos Mendes", comEmail ? "carlos@exemplo.com" : null, "Rafael Souza", "Corte", new BigDecimal("45.00"));
        EventoDeAgendamento evento = evento(tipo, aviso);

        assertThat(RegrasDeAviso.avisaCliente(evento)).isEqualTo(avisaCliente);
        assertThat(RegrasDeAviso.avisaProfissional(evento)).isEqualTo(avisaProfissional);
    }

    private static EventoDeAgendamento evento(String tipo, Aviso aviso) {
        UUID id = UUID.randomUUID();
        Instant fim = INICIO.plusSeconds(1800);
        return switch (tipo) {
            case "CRIADO" -> new AgendamentoCriado(id, id, id, INICIO, fim, aviso);
            case "REMARCADO" -> new AgendamentoReagendado(id, id, id, INICIO.minusSeconds(3600), INICIO, fim, aviso);
            case "CANCELADO" -> new AgendamentoCancelado(id, id, id, INICIO, "imprevisto", aviso);
            default -> throw new IllegalArgumentException(tipo);
        };
    }
}
