package com.guilhermetonelli.agendamento.notificacao;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static com.guilhermetonelli.agendamento.Cenario.AS_9H;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.guilhermetonelli.agendamento.mensageria.Topologia;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;

/**
 * Do pedido pela API ao e-mail na caixa do Mailpit, passando pelo RabbitMQ de verdade. Cada teste usa um
 * cliente com e-mail próprio, e quem não deve receber nada é conferido com uma sentinela: uma ação que avisa,
 * depois da que não deve avisar. A fila é uma só e tem um consumidor, então o e-mail da sentinela só chega
 * depois de a ação anterior ter sido tratada.
 */
class EmailDeAgendamentoTest extends IntegracaoTest {

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
    void clienteReservaERecebeComprovante() {
        cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        EmailRecebido email = mailpit.aguardar(carlos.email(), 1).getFirst();

        assertThat(email.assunto()).isEqualTo("Horário confirmado: Corte, qua 21/10 às 8h00");
        assertThat(email.remetente()).isEqualTo("nao-responder@localhost");
        assertThat(email.texto()).contains("Carlos Mendes", "Rafael Souza", "R$ 45,00");
        assertThat(email.html()).contains("<strong>Carlos Mendes</strong>", "Rafael Souza", "R$ 45,00");
    }

    @Test
    void barbeiroCancelaEOClienteRecebeComMotivo() {
        UUID agendamento = cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        cenario.cancelar(comoBarbeiro, agendamento, "imprevisto");

        assertThat(mailpit.aguardar(carlos.email(), 2))
                .filteredOn(email -> email.assunto().startsWith("Horário cancelado"))
                .singleElement()
                .satisfies(email -> assertThat(email.texto()).contains("Motivo: imprevisto"));
    }

    @Test
    void adminRemarcaECancelaEOClienteRecebeOsDois() {
        UUID agendamento = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        cenario.reagendar(api, agendamento, AS_8H30);
        cenario.cancelar(api, agendamento, null);

        assertThat(mailpit.aguardar(carlos.email(), 3)).extracting(EmailRecebido::assunto)
                .containsExactlyInAnyOrder(
                        "Horário confirmado: Corte, qua 21/10 às 8h00",
                        "Horário remarcado: Corte, qua 21/10 às 8h30",
                        "Horário cancelado: Corte, qua 21/10 às 8h30");
    }

    @Test
    void clienteQueRemarcaECancelaNaoRecebeEmailDisso() {
        UUID agendamento = cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        cenario.reagendar(comoCarlos, agendamento, AS_8H30);
        cenario.cancelar(comoCarlos, agendamento, null);
        cenario.reservar(comoBarbeiro, barbeiro, carlos.id(), AS_9H);

        assertThat(mailpit.aguardar(carlos.email(), 2)).extracting(EmailRecebido::assunto)
                .containsExactlyInAnyOrder(
                        "Horário confirmado: Corte, qua 21/10 às 8h00",
                        "Horário confirmado: Corte, qua 21/10 às 9h00");
    }

    @Test
    void encaixeNaoEnviaEmail() {
        cenario.encaixar(comoBarbeiro, barbeiro, AS_8H, "Cliente de balcão");
        cenario.reservar(comoBarbeiro, barbeiro, carlos.id(), AS_9H);

        mailpit.aguardar(carlos.email(), 1);

        assertThat(mailpit.quantidade()).isEqualTo(1);
    }

    @Test
    void falhaNoEnvioVaiParaADlqDepoisDasTentativas() {
        enviador.falharSempre(true);

        cenario.reservar(comoCarlos, barbeiro, carlos.id(), AS_8H);

        Message morta = filas.aguardarNa("email.cliente.dlq");
        assertThat(rabbit.getMessageConverter().fromMessage(morta)).isEqualTo(eventos.todos().getFirst());
        assertThat(mailpit.para(carlos.email())).isEmpty();
        assertThat(filas.quantidadeNa(Topologia.FILA_EMAIL)).isZero();
    }

    @Test
    void mensagemIlegivelEMensagemComTipoForjadoVaoParaADlq() {
        rabbit.send(Topologia.EXCHANGE, "agendamento.criado", mensagem("{ não é json", AgendamentoCriado.class.getName()));
        rabbit.send(Topologia.EXCHANGE, "agendamento.criado", mensagem("{\"email\":\"a@b.c\",\"senha\":\"x\"}", LoginRequest.class.getName()));

        assertThat(filas.aguardarNa("email.cliente.dlq")).isNotNull();
        assertThat(filas.aguardarNa("email.cliente.dlq")).isNotNull();
        assertThat(filas.quantidadeNa(Topologia.FILA_EMAIL)).isZero();
        assertThat(mailpit.quantidade()).isZero();
    }

    private static Message mensagem(String corpo, String tipo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setHeader("__TypeId__", tipo);
        return new Message(corpo.getBytes(UTF_8), propriedades);
    }
}
