package com.guilhermetonelli.agendamento.agendamento;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;

/** Quando cada reserva pede o lembrete: o horário gravado em {@code lembrar_em} no momento de reservar ou remarcar. */
class LembreteDoAgendamentoTest extends IntegracaoTest {

    private Cenario cenario;
    private UUID barbeiro;
    private Cadastro carlos;

    @Test
    void reservaComDiasDeAntecedenciaLembraUmDiaAntes() {
        cadastrarEm("2026-10-18T12:00:00Z");

        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        assertThat(instante("lembrar_em", id)).isEqualTo(Instant.parse("2026-10-20T11:00:00Z"));
        assertThat(instante("lembrete_enviado_em", id)).isNull();
    }

    @Test
    void reservaComMenosDeUmDiaLembraUmaHoraAntes() {
        cadastrarEm("2026-10-20T12:00:00Z");

        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        assertThat(instante("lembrar_em", id)).isEqualTo(Instant.parse("2026-10-21T10:00:00Z"));
    }

    @Test
    void encaixeNaoTemLembrete() {
        cadastrarEm("2026-10-18T12:00:00Z");

        cenario.encaixar(api, barbeiro, AS_8H, "Seu Zé");

        UUID id = jdbc.queryForObject("SELECT id FROM agendamento", UUID.class);
        assertThat(instante("lembrar_em", id)).isNull();
    }

    @Test
    void remarcarRecalculaOLembreteEZeraOEnvio() {
        cadastrarEm("2026-10-18T12:00:00Z");
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        jdbc.update("UPDATE agendamento SET lembrete_enviado_em = now() WHERE id = ?", id);

        cenario.reagendar(api, id, AS_8H30);

        assertThat(instante("lembrar_em", id)).isEqualTo(Instant.parse("2026-10-20T11:30:00Z"));
        assertThat(instante("lembrete_enviado_em", id)).isNull();
    }

    /** O relógio vai primeiro: os tokens do cadastro são emitidos no horário dele. */
    private void cadastrarEm(String agora) {
        relogio.ajustar(Instant.parse(agora));
        cenario = new Cenario(api);
        barbeiro = cenario.barbeiro("Rafael Souza");
        carlos = cenario.cliente("Carlos Mendes");
    }

    private Instant instante(String coluna, UUID id) {
        OffsetDateTime valor = jdbc.queryForObject("SELECT " + coluna + " FROM agendamento WHERE id = ?", OffsetDateTime.class, id);
        return valor == null ? null : valor.toInstant();
    }
}
