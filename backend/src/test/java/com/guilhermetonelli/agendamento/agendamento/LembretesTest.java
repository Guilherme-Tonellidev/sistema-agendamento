package com.guilhermetonelli.agendamento.agendamento;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static com.guilhermetonelli.agendamento.Cenario.AS_8H30;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Mailpit.EmailRecebido;

/**
 * A varredura de cada minuto, chamada direto com o relógio de teste e sem o Quartz no meio. O lembrete de
 * {@code AS_8H} (quarta, 21/10, 11:00Z) reservado em 18/10 vence 24 h antes, em 20/10 às 11:00Z.
 */
class LembretesTest extends IntegracaoTest {

    private static final Instant RESERVA = Instant.parse("2026-10-18T12:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-10-20T11:00:00Z");
    private static final String LEMBRETE_DAS_8H = "Lembrete de horário: Corte, qua 21/10 às 8h00";

    @Autowired
    private Lembretes lembretes;

    @Autowired
    private AgendamentoRepository repository;

    private Cenario cenario;
    private UUID barbeiro;
    private Cadastro carlos;

    @BeforeEach
    void cadastrar() {
        relogio.ajustar(RESERVA);
        cenario = new Cenario(api);
        barbeiro = cenario.barbeiro("Rafael Souza");
        carlos = cenario.cliente("Carlos Mendes");
    }

    @Test
    void naoEnviaAntesDaHora() {
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        relogio.ajustar(VENCE.minusSeconds(1));

        assertThat(lembretes.enviarVencidos()).isZero();
        assertThat(enviadoEm(id)).isNull();
    }

    @Test
    void enviaNaHoraEMarcaOEnvio() {
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        relogio.ajustar(VENCE);

        assertThat(lembretes.enviarVencidos()).isEqualTo(1);
        assertThat(enviadoEm(id)).isEqualTo(VENCE);
        assertThat(mailpit.aguardar(carlos.email(), 2)).extracting(EmailRecebido::assunto).contains(LEMBRETE_DAS_8H);
        assertThat(lembretes.enviarVencidos()).isZero();
    }

    @Test
    void lembreteAtrasadoAindaSaiAntesDoInicio() {
        cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        relogio.ajustar(Instant.parse("2026-10-21T10:00:00Z"));

        assertThat(lembretes.enviarVencidos()).isEqualTo(1);
    }

    @Test
    void reservaTardiaRecebeSoUmLembreteUmaHoraAntes() {
        relogio.reiniciar();
        cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        relogio.ajustar(Instant.parse("2026-10-21T09:59:59Z"));
        assertThat(lembretes.enviarVencidos()).isZero();

        relogio.ajustar(Instant.parse("2026-10-21T10:00:00Z"));
        assertThat(lembretes.enviarVencidos()).isEqualTo(1);
    }

    @Test
    void cancelarImpedeOLembrete() {
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        cenario.cancelar(api, id, null);

        relogio.ajustar(VENCE);

        assertThat(lembretes.enviarVencidos()).isZero();
    }

    @Test
    void remarcarDepoisDoEnvioGeraNovoLembrete() {
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        relogio.ajustar(VENCE);
        assertThat(lembretes.enviarVencidos()).isEqualTo(1);
        // Sem esperar o e-mail das 8h, a remarcação pode gravar antes de o consumidor conferir o lembrete e ele é descartado.
        mailpit.aguardar(carlos.email(), 2);

        cenario.reagendar(api, id, AS_8H30);
        relogio.ajustar(Instant.parse("2026-10-20T11:30:00Z"));

        assertThat(lembretes.enviarVencidos()).isEqualTo(1);
        assertThat(mailpit.aguardar(carlos.email(), 4)).extracting(EmailRecebido::assunto)
                .filteredOn(assunto -> assunto.startsWith("Lembrete de horário"))
                .containsExactlyInAnyOrder(LEMBRETE_DAS_8H, "Lembrete de horário: Corte, qua 21/10 às 8h30");
    }

    @Test
    void clienteQueTrocouDeEmailRecebeNoNovo() {
        cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        String novo = "novo." + UUID.randomUUID() + "@exemplo.com";
        jdbc.update("UPDATE cliente SET email = ? WHERE id = ?", novo, carlos.id());

        relogio.ajustar(VENCE);

        assertThat(lembretes.enviarVencidos()).isEqualTo(1);
        assertThat(mailpit.aguardar(novo, 1)).extracting(EmailRecebido::assunto).containsExactly(LEMBRETE_DAS_8H);
    }

    @Test
    void descartaOQueJaPassou() {
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);

        relogio.ajustar(Instant.parse("2026-10-21T11:00:00Z"));

        assertThat(lembretes.enviarVencidos()).isZero();
        assertThat(repository.findById(id).orElseThrow().getLembrarEm()).isNull();
    }

    @Test
    void umaFalhaNoBrokerNaoMarcaEORetomaDepois() {
        UUID id = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        relogio.ajustar(VENCE);

        filas.comBrokerPausado(() -> {
            assertThat(lembretes.enviarVencidos()).isZero();
            assertThat(enviadoEm(id)).isNull();
        });

        await().atMost(Duration.ofSeconds(15)).until(() -> lembretes.enviarVencidos() > 0);
        assertThat(enviadoEm(id)).isNotNull();
    }

    @Test
    void lotePequenoDeixaORestoParaDepois() {
        UUID primeira = cenario.reservar(api, barbeiro, carlos.id(), AS_8H);
        UUID segunda = cenario.reservar(api, barbeiro, carlos.id(), AS_8H30);
        relogio.ajustar(Instant.parse("2026-10-20T11:30:00Z"));

        assertThat(lembretes.enviarVencidos(1)).isEqualTo(1);
        assertThat(enviadoEm(primeira)).isNotNull();
        assertThat(enviadoEm(segunda)).isNull();

        assertThat(lembretes.enviarVencidos(1)).isEqualTo(1);
        assertThat(enviadoEm(segunda)).isNotNull();

        assertThat(lembretes.enviarVencidos(1)).isZero();
    }

    private Instant enviadoEm(UUID id) {
        return repository.findById(id).orElseThrow().getLembreteEnviadoEm();
    }
}
