package com.guilhermetonelli.agendamento.mensageria;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.seguranca.Papel;

/** Do commit ao RabbitMQ: o que foi gravado sai com a chave certa, o que foi desfeito não sai, e o broker parado não derruba a reserva. */
@ExtendWith(OutputCaptureExtension.class)
class MensageriaTest extends IntegracaoTest {

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private ApplicationEventPublisher publicador;

    @Test
    void reservaPublicaCriadoComAChaveCerta() {
        String fila = filas.filaTemporaria();
        var cenario = new Cenario(api);

        cenario.reservar(api, cenario.barbeiro("Rafael Souza"), cenario.cliente("Carlos Mendes").id(), AS_8H);

        Message mensagem = filas.aguardarNa(fila);
        assertThat(mensagem.getMessageProperties().getReceivedRoutingKey()).isEqualTo("agendamento.criado");
        assertThat(mensagem.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(rabbit.getMessageConverter().fromMessage(mensagem)).isEqualTo(eventos.todos().getFirst());
    }

    @Test
    void remarcarECancelarUsamSuasChaves() {
        String fila = filas.filaTemporaria();
        var cenario = new Cenario(api);
        UUID agendamento = cenario.reservar(api, cenario.barbeiro("Rafael Souza"), cenario.cliente("Carlos Mendes").id(), AS_8H);

        cenario.reagendar(api, agendamento, AS_8H30);
        cenario.cancelar(api, agendamento, "imprevisto");

        assertThat(filas.aguardarNa(fila).getMessageProperties().getReceivedRoutingKey()).isEqualTo("agendamento.criado");
        assertThat(filas.aguardarNa(fila).getMessageProperties().getReceivedRoutingKey()).isEqualTo("agendamento.remarcado");
        assertThat(filas.aguardarNa(fila).getMessageProperties().getReceivedRoutingKey()).isEqualTo("agendamento.cancelado");
    }

    @Test
    void transacaoDesfeitaNaoPublica() {
        String fila = filas.filaTemporaria();
        var cenario = new Cenario(api);
        UUID barbeiro = cenario.barbeiro("Rafael Souza");
        UUID cliente = cenario.cliente("Carlos Mendes").id();
        Aviso aviso = new Aviso(Papel.CLIENTE, "Carlos Mendes", "carlos@exemplo.com", "Rafael Souza", "Corte", BigDecimal.TEN);

        transacao.executeWithoutResult(status -> {
            publicador.publishEvent(new AgendamentoCriado(UUID.randomUUID(), barbeiro, cliente, Instant.now(), Instant.now(), aviso));
            status.setRollbackOnly();
        });
        cenario.reservar(api, barbeiro, cliente, AS_8H);

        assertThat(filas.quantidadeNa(fila)).isEqualTo(1);
    }

    @Test
    void brokerSemRespostaNaoDerrubaAReserva(CapturedOutput saida) {
        var cenario = new Cenario(api);
        UUID barbeiro = cenario.barbeiro("Rafael Souza");
        UUID cliente = cenario.cliente("Carlos Mendes").id();
        long inicio = System.nanoTime();

        filas.comBrokerPausado(() -> {
            cenario.reservar(api, barbeiro, cliente, AS_8H);
            var saude = api.get().uri("/actuator/health").retrieve().toEntity(String.class);
            assertThat(saude.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(saude.getBody()).contains("\"status\":\"UP\"");
        });

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(8));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agendamento", Integer.class)).isEqualTo(1);
        assertThat(saida).contains("Não foi possível publicar AgendamentoCriado");
    }
}
