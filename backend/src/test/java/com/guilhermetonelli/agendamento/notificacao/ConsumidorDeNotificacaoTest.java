package com.guilhermetonelli.agendamento.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/**
 * O consumidor sem o RabbitMQ no meio: o evento é entregue direto ao método, e o que vale é a linha no banco.
 * O caminho completo, pela fila, está em {@link NotificacaoDeAgendamentoTest}.
 */
class ConsumidorDeNotificacaoTest extends IntegracaoTest {

    private static final Instant SABADO = Instant.parse("2026-10-24T17:30:00Z");
    private static final Instant DOMINGO = Instant.parse("2026-10-25T13:00:00Z");

    @Autowired
    private ConsumidorDeNotificacao consumidor;

    private UUID barbeiro;

    @BeforeEach
    void cadastrar() {
        barbeiro = new Cenario(api).barbeiro("Rafael Souza");
    }

    @Test
    void cadaTipoGravaUmaLinhaComTextoEInicioDoEvento() {
        UUID id = UUID.randomUUID();
        Aviso aviso = aviso(Papel.CLIENTE);

        consumidor.tratar(new AgendamentoCriado(id, barbeiro, id, SABADO, SABADO, aviso));
        consumidor.tratar(new AgendamentoReagendado(id, barbeiro, id, SABADO, DOMINGO, DOMINGO, aviso));
        consumidor.tratar(new AgendamentoCancelado(id, barbeiro, id, DOMINGO, "imprevisto", aviso));

        assertThat(linhas()).extracting(l -> l.get("tipo"), l -> l.get("texto"))
                .containsExactlyInAnyOrder(
                        tuple("CRIADO", "Carlos Mendes reservou Corte para sáb 24/10 às 14h30."),
                        tuple("REMARCADO", "Carlos Mendes remarcou Corte de sáb 24/10 às 14h30 para dom 25/10 às 10h00."),
                        tuple("CANCELADO", "Carlos Mendes cancelou Corte de dom 25/10 às 10h00. Motivo: imprevisto."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacao WHERE lida_em IS NULL AND inicio IN (?, ?)", Integer.class,
                Timestamp.from(SABADO), Timestamp.from(DOMINGO))).isEqualTo(3);
    }

    @Test
    void quemFezFoiOAdministradorEATextoDizIsso() {
        UUID id = UUID.randomUUID();

        consumidor.tratar(new AgendamentoCancelado(id, barbeiro, id, SABADO, null, aviso(Papel.ADMIN)));

        assertThat(linhas()).singleElement()
                .satisfies(l -> assertThat(l.get("texto")).isEqualTo("A administração cancelou o Corte de Carlos Mendes de sáb 24/10 às 14h30."));
    }

    @Test
    void barbeiroQueAgiuNaoGeraLinha() {
        UUID id = UUID.randomUUID();

        consumidor.tratar(new AgendamentoCancelado(id, barbeiro, id, SABADO, null, aviso(Papel.PROFISSIONAL)));

        assertThat(linhas()).isEmpty();
    }

    @Test
    void profissionalInexistenteEDescartadoSemExcecao() {
        UUID id = UUID.randomUUID();

        assertThatCode(() -> consumidor.tratar(new AgendamentoCriado(id, UUID.randomUUID(), id, SABADO, SABADO, aviso(Papel.CLIENTE))))
                .doesNotThrowAnyException();

        assertThat(linhas()).isEmpty();
    }

    private List<Map<String, Object>> linhas() {
        return jdbc.queryForList("SELECT tipo, texto FROM notificacao");
    }

    private static Aviso aviso(Papel autor) {
        return new Aviso(autor, "Carlos Mendes", "carlos@exemplo.com", "Rafael Souza", "Corte", new BigDecimal("45.00"));
    }
}
