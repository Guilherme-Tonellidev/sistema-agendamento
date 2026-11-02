package com.guilhermetonelli.agendamento.agendamento;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.agendamento.evento.LembreteDeAgendamento;

/**
 * A conferência que o consumidor faz quando a mensagem de lembrete finalmente chega: ela pode ter esperado na fila
 * ou na DLQ, então só vale para um horário que ainda não começou, confirmado e igual ao da mensagem.
 */
class LembreteAindaValeTest extends IntegracaoTest {

    @Autowired
    private Lembretes lembretes;

    private Cenario cenario;
    private UUID barbeiro;
    private Cadastro carlos;
    private UUID agendamento;

    @BeforeEach
    void reservar() {
        cenario = new Cenario(api);
        barbeiro = cenario.barbeiro("Rafael Souza");
        carlos = cenario.cliente("Carlos Mendes");
        agendamento = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
    }

    @Test
    void valeEnquantoOHorarioNaoComecouEContinuaConfirmado() {
        assertThat(lembretes.aindaVale(lembrete(AS_8H))).isTrue();
    }

    @Test
    void naoValeDepoisQueOHorarioComecou() {
        relogio.ajustar(Instant.parse(AS_8H));

        assertThat(lembretes.aindaVale(lembrete(AS_8H))).isFalse();
    }

    @Test
    void naoValeSeFoiCancelado() {
        cenario.cancelar(api, agendamento, null);

        assertThat(lembretes.aindaVale(lembrete(AS_8H))).isFalse();
    }

    @Test
    void naoValeSeFoiRemarcado() {
        cenario.reagendar(api, agendamento, AS_8H30);

        assertThat(lembretes.aindaVale(lembrete(AS_8H))).isFalse();
    }

    private LembreteDeAgendamento lembrete(String inicio) {
        var aviso = new Aviso(null, "Carlos Mendes", carlos.email(), "Rafael Souza", "Corte", new BigDecimal("45.00"));
        return new LembreteDeAgendamento(agendamento, barbeiro, carlos.id(), Instant.parse(inicio), aviso);
    }
}
