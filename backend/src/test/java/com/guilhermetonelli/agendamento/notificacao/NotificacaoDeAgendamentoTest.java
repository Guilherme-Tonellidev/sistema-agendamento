package com.guilhermetonelli.agendamento.notificacao;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_9H;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Mailpit.EmailRecebido;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.Aviso;
import com.guilhermetonelli.agendamento.mensageria.Topologia;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;

/**
 * Do pedido pela API à linha em {@code notificacao}, passando pelo RabbitMQ de verdade. Quem não deve gerar
 * aviso é conferido com uma sentinela: uma ação que gera, depois da que não deve. A fila é uma só e tem um
 * consumidor, então a linha da sentinela só aparece depois de a ação anterior ter sido tratada.
 */
class NotificacaoDeAgendamentoTest extends IntegracaoTest {

    @Autowired
    private RabbitTemplate rabbit;

    private Cenario cenario;
    private UUID barbeiro;
    private Cadastro carlos;
    private RestClient comoBarbeiro;
    private RestClient comoCarlos;

    @BeforeEach
    void cadastrar() {
        cenario = new Cenario(api);
        barbeiro = cenario.barbeiro("Rafael Souza");
        carlos = cenario.cliente("Carlos Mendes");
        comoBarbeiro = apiComo(comoProfissional(barbeiro));
        comoCarlos = apiComo(comoCliente(carlos.id()));
    }

    @Test
    void clienteReservaEOBarbeiroRecebeANotificacao() {
        cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        aguardarLinhas(1);

        assertThat(jdbc.queryForMap("SELECT profissional_id, tipo, texto, inicio, lida_em FROM notificacao"))
                .containsEntry("profissional_id", barbeiro)
                .containsEntry("tipo", "CRIADO")
                .containsEntry("texto", "Carlos Mendes reservou Corte para qua 21/10 às 8h00.")
                .containsEntry("inicio", Timestamp.from(Instant.parse(AS_8H)))
                .containsEntry("lida_em", null);
    }

    @Test
    void barbeiroCancelaENaoRecebeNada() {
        UUID agendamento = cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        cenario.cancelar(comoBarbeiro, agendamento, "imprevisto");
        cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_9H);

        aguardarLinhas(2);
        assertThat(tipos()).containsExactly("CRIADO", "CRIADO");
    }

    @Test
    void barbeiroEncaixaENaoRecebeNada() {
        cenario.encaixar(comoBarbeiro, barbeiro, AS_8H, "Cliente de balcão");
        cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_9H);

        aguardarLinhas(1);
        assertThat(textos()).containsExactly("Carlos Mendes reservou Corte para qua 21/10 às 9h00.");
    }

    @Test
    void adminEncaixaEOBarbeiroEAvisado() {
        cenario.encaixar(api, barbeiro, AS_8H, "Cliente de balcão");

        aguardarLinhas(1);

        assertThat(textos()).containsExactly("A administração reservou o Corte de Cliente de balcão para qua 21/10 às 8h00.");
    }

    @Test
    void adminCancelaEOsDoisSaoAvisados() {
        UUID agendamento = cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        cenario.cancelar(api, agendamento, null);

        aguardarLinhas(2);
        assertThat(textos()).contains("A administração cancelou o Corte de Carlos Mendes de qua 21/10 às 8h00.");
        assertThat(mailpit.aguardar(carlos.email(), 2)).extracting(EmailRecebido::assunto)
                .contains("Horário cancelado: Corte, qua 21/10 às 8h00");
    }

    @Test
    void eventoDeProfissionalInexistenteEDescartado() {
        UUID id = UUID.randomUUID();
        Aviso aviso = new Aviso(Papel.CLIENTE, "Fantasma", "fantasma@exemplo.com", "Ninguém", "Corte", BigDecimal.TEN);
        rabbit.convertAndSend(Topologia.EXCHANGE, "agendamento.criado",
                new AgendamentoCriado(id, UUID.randomUUID(), id, Instant.parse(AS_8H), Instant.parse(AS_8H), aviso));

        cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_9H);

        aguardarLinhas(1);
        assertThat(textos()).containsExactly("Carlos Mendes reservou Corte para qua 21/10 às 9h00.");
        assertThat(filas.quantidadeNa("notificacao.profissional.dlq")).isZero();
    }

    @Test
    void mensagemIlegivelEMensagemComTipoForjadoVaoParaADlq() {
        rabbit.send(Topologia.EXCHANGE, "agendamento.criado", mensagem("{ não é json", AgendamentoCriado.class.getName()));
        rabbit.send(Topologia.EXCHANGE, "agendamento.criado", mensagem("{\"email\":\"a@b.c\",\"senha\":\"x\"}", LoginRequest.class.getName()));

        assertThat(filas.aguardarNa("notificacao.profissional.dlq")).isNotNull();
        assertThat(filas.aguardarNa("notificacao.profissional.dlq")).isNotNull();
        assertThat(filas.quantidadeNa(Topologia.FILA_NOTIFICACAO)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacao", Integer.class)).isZero();
    }

    private void aguardarLinhas(int quantidade) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject("SELECT count(*) FROM notificacao", Integer.class) >= quantidade);
    }

    private List<String> tipos() {
        return jdbc.queryForList("SELECT tipo FROM notificacao ORDER BY criado_em, tipo", String.class);
    }

    private List<String> textos() {
        return jdbc.queryForList("SELECT texto FROM notificacao ORDER BY criado_em, texto", String.class);
    }

    private static Message mensagem(String corpo, String tipo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setHeader("__TypeId__", tipo);
        return new Message(corpo.getBytes(UTF_8), propriedades);
    }
}
