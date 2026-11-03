package com.guilhermetonelli.agendamento.disponibilidade;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;

/**
 * A consulta de horários livres lê do cache, e cada escrita na agenda a mantém fiel. O barbeiro atende na quarta,
 * 21/10/2026, das 8h às 12h; o corte dura 30 minutos e os horários saem de 15 em 15.
 */
class HorariosEmCacheTest extends IntegracaoTest {

    private static final Instant AS_8H_INSTANTE = Instant.parse(AS_8H);

    private Cenario cenario;
    private UUID barbeiro;
    private UUID cliente;

    @BeforeEach
    void montar() {
        cenario = new Cenario(api);
        barbeiro = cenario.barbeiro("Rafael Souza");
        cliente = cenario.cliente("Carlos Mendes").id();
    }

    @Test
    void aSegundaLeituraVemDoCache() {
        List<Instant> primeira = livres();

        jdbc.update("DELETE FROM jornada");
        assertThat(livres()).isNotEmpty().isEqualTo(primeira);

        definirJornada(LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThat(livres()).containsExactly(
                Instant.parse("2026-10-21T12:00:00Z"), Instant.parse("2026-10-21T12:15:00Z"), Instant.parse("2026-10-21T12:30:00Z"));
    }

    @Test
    void reservarRemoveOHorarioECancelarDevolve() {
        assertThat(livres()).contains(AS_8H_INSTANTE);

        UUID reserva = cenario.reservar(api, barbeiro, cliente, AS_8H);
        assertThat(livres()).doesNotContain(AS_8H_INSTANTE);

        cenario.cancelar(api, reserva, null);
        assertThat(livres()).contains(AS_8H_INSTANTE);
    }

    @Test
    void bloquearERemoverBloqueioAparecemNaLeitura() {
        livres();

        UUID bloqueio = bloquear("2026-10-21T11:00:00Z", "2026-10-21T12:00:00Z").id();
        assertThat(livres())
                .doesNotContain(AS_8H_INSTANTE, Instant.parse("2026-10-21T11:15:00Z"), Instant.parse("2026-10-21T11:30:00Z"),
                        Instant.parse("2026-10-21T11:45:00Z"))
                .contains(Instant.parse("2026-10-21T12:00:00Z"));

        var remocao = api.delete().uri("/api/profissionais/{id}/bloqueios/{b}", barbeiro, bloqueio).retrieve().toBodilessEntity();
        assertThat(remocao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(livres()).contains(AS_8H_INSTANTE);
    }

    @Test
    void oFiltroDeAntecedenciaAcompanhaORelogio() {
        relogio.ajustar(Instant.parse("2026-10-21T11:05:00Z"));

        assertThat(livres().getFirst()).isEqualTo(Instant.parse("2026-10-21T12:45:00Z"));
        assertThat(paraEncaixe().getFirst()).isEqualTo(Instant.parse("2026-10-21T11:15:00Z"));
        assertThat(redis.chaves("horarios:*")).hasSize(1);
    }

    @Test
    void duracoesIguaisCompartilhamEntradaEDiferentesNao() {
        UUID barba = criarServico("Barba", 30);
        UUID completo = criarServico("Corte e barba", 45);
        atender(cenario.corte(), barba, completo);

        livres(cenario.corte());
        livres(barba);
        assertThat(redis.chaves("horarios:*")).hasSize(1);

        livres(completo);
        assertThat(redis.chaves("horarios:*")).hasSize(2);
    }

    @Test
    void mudarADuracaoDoServicoMudaAChave() {
        Instant ultimoDe30 = Instant.parse("2026-10-21T14:30:00Z");
        Instant ultimoDe45 = Instant.parse("2026-10-21T14:15:00Z");
        assertThat(livres()).contains(ultimoDe30);
        String versao = redis.valor(CacheDeHorarios.chaveDaVersao(barbeiro));

        var atualizacao = api.put().uri("/api/servicos/{id}", cenario.corte())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Corte", null, 45, new BigDecimal("45.00")))
                .retrieve()
                .toBodilessEntity();

        assertThat(atualizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(livres()).contains(ultimoDe45).doesNotContain(ultimoDe30);
        assertThat(redis.valor(CacheDeHorarios.chaveDaVersao(barbeiro))).isEqualTo(versao);
    }

    @Test
    void reservaNaoConfiaNoCacheNaJornada() {
        assertThat(livres()).contains(AS_8H_INSTANTE);
        jdbc.update("DELETE FROM jornada");

        assertThat(reservar(cliente, AS_8H)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void reservaNaoConfiaNoCacheNosAgendamentos() {
        UUID reserva = cenario.reservar(api, barbeiro, cliente, AS_8H);
        cenario.cancelar(api, reserva, null);
        assertThat(livres()).contains(AS_8H_INSTANTE);
        jdbc.update("UPDATE agendamento SET status = 'CONFIRMADO', cancelado_em = NULL, motivo_cancelamento = NULL WHERE id = ?", reserva);

        assertThat(reservar(cenario.cliente("Diana Alves").id(), AS_8H)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void profissionalInativadoSeguePeloBanco() {
        livres();
        jdbc.update("UPDATE profissional SET ativo = false WHERE id = ?", barbeiro);

        assertThat(statusDaConsulta("2026-10-21")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void semRedisOsHorariosVemDoBanco() {
        List<Instant> esperados = livres();
        List<Duration> demoras = new ArrayList<>();

        redis.comRedisPausado(() -> {
            demoras.add(medir(() -> assertThat(livres()).isEqualTo(esperados)));
            demoras.add(medir(() -> assertThat(bloquear("2026-10-21T15:00:00Z", "2026-10-21T16:00:00Z")).isNotNull()));
        });

        assertThat(demoras).hasSize(2).allSatisfy(demora -> assertThat(demora).isLessThan(Duration.ofSeconds(1)));
    }

    @Test
    void versaoPerdidaNaoServeEntradaAntiga() {
        redis.limpar();
        assertThat(livres()).contains(AS_8H_INSTANTE);
        definirJornada(LocalTime.of(9, 0), LocalTime.of(10, 0));

        redis.apagar(CacheDeHorarios.chaveDaVersao(barbeiro));

        assertThat(livres()).containsExactly(
                Instant.parse("2026-10-21T12:00:00Z"), Instant.parse("2026-10-21T12:15:00Z"), Instant.parse("2026-10-21T12:30:00Z"));
    }

    @Test
    void valorCorrompidoNaoDerrubaAConsulta() {
        List<Instant> esperados = livres();
        redis.definir(redis.chaves("horarios:*").getFirst(), "lixo");

        assertThat(statusDaConsulta("2026-10-21")).isEqualTo(HttpStatus.OK);
        assertThat(livres()).isEqualTo(esperados);
    }

    @Test
    void dataForaDaJanelaSegueRecusada() {
        assertThat(statusDaConsulta("2027-12-31")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        String versao = redis.valor(CacheDeHorarios.chaveDaVersao(barbeiro));
        redis.definir("horarios:%s:%s:30:2027-12-31".formatted(barbeiro, versao), "");

        assertThat(statusDaConsulta("2027-12-31")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void dataPassadaNaoUsaOCache() {
        var resposta = api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data=2026-10-19", barbeiro, cenario.corte())
                .retrieve()
                .body(new ParameterizedTypeReference<List<HorarioLivreResponse>>() {});

        assertThat(resposta).isEmpty();
        assertThat(redis.chaves("horarios:*")).isEmpty();
    }

    private List<Instant> livres() {
        return livres(cenario.corte());
    }

    private List<Instant> livres(UUID servico) {
        return api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data=2026-10-21", barbeiro, servico)
                .retrieve()
                .body(new ParameterizedTypeReference<List<HorarioLivreResponse>>() {})
                .stream()
                .map(HorarioLivreResponse::inicio)
                .toList();
    }

    private List<Instant> paraEncaixe() {
        return api.get()
                .uri("/api/profissionais/{id}/horarios-livres/encaixe?servicoId={s}&data=2026-10-21", barbeiro, cenario.corte())
                .retrieve()
                .body(new ParameterizedTypeReference<List<HorarioLivreResponse>>() {})
                .stream()
                .map(HorarioLivreResponse::inicio)
                .toList();
    }

    private HttpStatusCode statusDaConsulta(String data) {
        return api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data={d}", barbeiro, cenario.corte(), data)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    private HttpStatusCode reservar(UUID quem, String inicio) {
        return api.post().uri("/api/agendamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(barbeiro, cenario.corte(), quem, Instant.parse(inicio)))
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    private BloqueioResponse bloquear(String inicio, String fim) {
        var resposta = api.post().uri("/api/profissionais/{id}/bloqueios", barbeiro)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloqueioRequest(Instant.parse(inicio), Instant.parse(fim), null))
                .retrieve()
                .toEntity(BloqueioResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private void definirJornada(LocalTime inicio, LocalTime fim) {
        var resposta = api.put().uri("/api/profissionais/{id}/jornada", barbeiro)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JornadaDto(List.of(new IntervaloDeJornadaDto(WEDNESDAY, inicio, fim))))
                .retrieve()
                .toBodilessEntity();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID criarServico(String nome, int minutos) {
        return api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest(nome, null, minutos, new BigDecimal("40.00")))
                .retrieve()
                .body(ServicoResponse.class)
                .id();
    }

    private void atender(UUID... servicos) {
        api.put().uri("/api/profissionais/{id}/servicos", barbeiro)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicosDoProfissionalRequest(Set.of(servicos)))
                .retrieve()
                .toBodilessEntity();
    }
}
