package com.guilhermetonelli.agendamento.disponibilidade;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.jayway.jsonpath.JsonPath;

class DisponibilidadeApiTest extends IntegracaoTest {

    private static final String QUARTA = "2026-10-21";

    @Test
    void defineJornadaEDevolveOrdenada() {
        UUID ana = cadastrarProfissional();

        var resposta = definirJornada(ana, List.of(
                intervalo(WEDNESDAY, "13:00", "18:00"),
                intervalo(MONDAY, "13:00", "18:00"),
                intervalo(MONDAY, "08:00", "12:00")));
        JornadaDto consultada = api.get().uri("/api/profissionais/{id}/jornada", ana).retrieve().body(JornadaDto.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consultada).isEqualTo(resposta.getBody());
        assertThat(consultada.intervalos()).containsExactly(
                intervalo(MONDAY, "08:00", "12:00"),
                intervalo(MONDAY, "13:00", "18:00"),
                intervalo(WEDNESDAY, "13:00", "18:00"));
        assertThat(jdbc.queryForList("SELECT dia_semana FROM jornada ORDER BY dia_semana", Integer.class))
                .containsExactly(1, 1, 3);
    }

    @Test
    void novaJornadaSubstituiAAnterior() {
        UUID ana = cadastrarProfissional();
        definirJornada(ana, List.of(intervalo(MONDAY, "08:00", "12:00")));

        var resposta = definirJornada(ana, List.of(intervalo(WEDNESDAY, "09:00", "10:00")));

        assertThat(resposta.getBody().intervalos()).containsExactly(intervalo(WEDNESDAY, "09:00", "10:00"));
    }

    @Test
    void aceitaIntervalosQueSoEncostam() {
        UUID ana = cadastrarProfissional();

        var resposta = definirJornada(ana, List.of(intervalo(MONDAY, "08:00", "12:00"), intervalo(MONDAY, "12:00", "13:00")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void recusaIntervalosSobrepostosNoMesmoDia() {
        UUID ana = cadastrarProfissional();
        definirJornada(ana, List.of(intervalo(MONDAY, "08:00", "12:00")));

        var resposta = api.put().uri("/api/profissionais/{id}/jornada", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JornadaDto(List.of(intervalo(MONDAY, "08:00", "12:00"), intervalo(MONDAY, "11:00", "14:00"))))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) JsonPath.read(resposta.getBody(), "$.detail")).contains("segunda-feira", "08:00–12:00", "11:00–14:00");
        assertThat(jornada(ana)).containsExactly(intervalo(MONDAY, "08:00", "12:00"));
    }

    @Test
    void recusaIntervaloQueTerminaAntesDeComecar() {
        UUID ana = cadastrarProfissional();

        var resposta = api.put().uri("/api/profissionais/{id}/jornada", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JornadaDto(List.of(intervalo(MONDAY, "12:00", "08:00"))))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void recusaDiaDaSemanaInvalido() {
        UUID ana = cadastrarProfissional();

        var resposta = api.put().uri("/api/profissionais/{id}/jornada", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "intervalos": [{ "diaSemana": "SEGUNDA", "inicio": "08:00", "fim": "12:00" }] }
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void retorna404ParaProfissionalInexistente() {
        UUID inexistente = UUID.randomUUID();

        assertThat(api.get().uri("/api/profissionais/{id}/jornada", inexistente).retrieve().toBodilessEntity()
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(definirJornada(inexistente, List.of(intervalo(MONDAY, "08:00", "12:00"))).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusBloqueio(inexistente, """
                { "inicio": "2026-10-21T15:00:00Z", "fim": "2026-10-21T18:00:00Z" }
                """)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.get().uri("/api/profissionais/{id}/bloqueios", inexistente).retrieve().toBodilessEntity()
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void definicoesSimultaneasDaJornadaNaoSeMisturam() throws Exception {
        UUID ana = cadastrarProfissional();
        var segunda = List.of(intervalo(MONDAY, "08:00", "12:00"), intervalo(MONDAY, "13:00", "18:00"));
        var quarta = List.of(intervalo(WEDNESDAY, "08:00", "12:00"), intervalo(WEDNESDAY, "13:00", "18:00"));
        definirJornada(ana, segunda);

        for (int rodada = 0; rodada < 10; rodada++) {
            var largada = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var respostas = Stream.of(segunda, quarta)
                        .map(jornada -> executor.submit(() -> {
                            largada.await();
                            return definirJornada(ana, jornada).getStatusCode();
                        }))
                        .toList();
                largada.countDown();
                for (var resposta : respostas) {
                    assertThat(resposta.get()).isEqualTo(HttpStatus.OK);
                }
            }
            assertThat(List.of(segunda, quarta)).contains(jornada(ana));
        }
    }

    @Test
    void criaListaPorPeriodoERemoveBloqueios() {
        UUID ana = cadastrarProfissional();
        var ferias = bloquear(ana, "2026-11-02T03:00:00Z", "2026-11-07T03:00:00Z", "  Férias ");
        bloquear(ana, "2026-10-21T15:00:00Z", "2026-10-21T18:00:00Z", null);

        List<BloqueioResponse> novembro = listarBloqueios(ana, "?de=2026-11-01&ate=2026-11-30");
        var remocao = api.delete().uri("/api/profissionais/{id}/bloqueios/{b}", ana, ferias.getBody().id())
                .retrieve().toBodilessEntity();

        assertThat(ferias.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ferias.getBody().motivo()).isEqualTo("Férias");
        assertThat(novembro).extracting(BloqueioResponse::motivo).containsExactly("Férias");
        assertThat(remocao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listarBloqueios(ana, "")).extracting(BloqueioResponse::inicio)
                .containsExactly(Instant.parse("2026-10-21T15:00:00Z"));
    }

    @Test
    void filtroDeDatasUsaOFusoDoNegocio() {
        UUID ana = cadastrarProfissional();
        // 22:00 de terça em São Paulo, que já é quarta em UTC.
        bloquear(ana, "2026-10-21T01:00:00Z", "2026-10-21T02:00:00Z", "Terça à noite");

        assertThat(listarBloqueios(ana, "?de=2026-10-20&ate=2026-10-20")).hasSize(1);
        assertThat(listarBloqueios(ana, "?de=2026-10-21&ate=2026-10-21")).isEmpty();
    }

    @Test
    void listagemIncluiBloqueiosQueCruzamOPeriodo() {
        UUID ana = cadastrarProfissional();
        bloquear(ana, "2026-10-30T03:00:00Z", "2026-11-03T03:00:00Z", "Feriado prolongado");
        bloquear(ana, "2026-10-29T03:00:00Z", "2026-10-30T03:00:00Z", "Antes");

        assertThat(listarBloqueios(ana, "?de=2026-11-01&ate=2026-11-30")).extracting(BloqueioResponse::motivo)
                .containsExactly("Feriado prolongado");
    }

    @Test
    void recusaFiltroDeBloqueiosInvertidoOuForaDaFaixa() {
        UUID ana = cadastrarProfissional();

        assertThat(statusListagemDeBloqueios(ana, "2026-10-22", "2026-10-21")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(statusListagemDeBloqueios(ana, "", "+999999999-12-31")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(statusListagemDeBloqueios(ana, "-5000-01-01", "")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(statusListagemDeBloqueios(ana, "1900-01-01", "9999-12-31")).isEqualTo(HttpStatus.OK);
    }

    @Test
    void recusaBloqueioForaDaFaixaOuMenorQueAPrecisaoDoBanco() {
        UUID ana = cadastrarProfissional();

        assertThat(statusBloqueio(ana, """
                { "inicio": "+300000-01-01T00:00:00Z", "fim": "+300000-01-02T00:00:00Z" }
                """)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(statusBloqueio(ana, """
                { "inicio": "2026-10-21T15:00:00.0000001Z", "fim": "2026-10-21T15:00:00.0000002Z" }
                """)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(listarBloqueios(ana, "")).isEmpty();
    }

    @Test
    void recusaBloqueioQueTerminaAntesDeComecar() {
        UUID ana = cadastrarProfissional();

        var resposta = api.post().uri("/api/profissionais/{id}/bloqueios", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloqueioRequest(Instant.parse("2026-10-21T18:00:00Z"), Instant.parse("2026-10-21T15:00:00Z"), null))
                .retrieve()
                .toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void naoRemoveBloqueioDeOutroProfissional() {
        UUID ana = cadastrarProfissional();
        UUID bruno = cadastrarProfissional("Bruno Dias", "bruno@barbearia.com");
        UUID bloqueio = bloquear(ana, "2026-10-21T15:00:00Z", "2026-10-21T18:00:00Z", null).getBody().id();

        var resposta = api.delete().uri("/api/profissionais/{id}/bloqueios/{b}", bruno, bloqueio)
                .retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(listarBloqueios(ana, "")).hasSize(1);
    }

    @Test
    void calculaHorariosLivresNoFusoDoNegocio() {
        var cenario = cenarioComJornada("08:00", "09:00");

        var resposta = api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data={d}", cenario.profissional(), cenario.servico(), QUARTA)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<HorarioLivreResponse>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsExactly(
                horario("2026-10-21T11:00:00Z", "2026-10-21T11:30:00Z"),
                horario("2026-10-21T11:15:00Z", "2026-10-21T11:45:00Z"),
                horario("2026-10-21T11:30:00Z", "2026-10-21T12:00:00Z"));
    }

    @Test
    void horariosLivresDescontamBloqueiosEOPassado() {
        var cenario = cenarioComJornada("08:00", "10:00");
        bloquear(cenario.profissional(), "2026-10-21T12:00:00Z", "2026-10-21T13:00:00Z", "Médico");
        relogio.ajustar(Instant.parse("2026-10-21T11:10:00Z"));

        assertThat(horariosLivres(cenario, QUARTA)).extracting(HorarioLivreResponse::inicio).containsExactly(
                Instant.parse("2026-10-21T11:15:00Z"),
                Instant.parse("2026-10-21T11:30:00Z"));
    }

    @Test
    void diaPassadoNaoTemHorarios() {
        var cenario = cenarioComJornada("08:00", "09:00");

        assertThat(horariosLivres(cenario, "2026-10-14")).isEmpty();
        assertThat(horariosLivres(cenario, "-5000-01-01")).isEmpty();
    }

    @Test
    void diaSemJornadaNaoTemHorarios() {
        var cenario = cenarioComJornada("08:00", "09:00");

        assertThat(horariosLivres(cenario, "2026-10-22")).isEmpty();
    }

    @Test
    void recusaConsultaAlemDaJanelaDeReserva() {
        var cenario = cenarioComJornada("08:00", "09:00");

        assertThat(statusHorariosLivres(cenario.profissional(), cenario.servico(), "2027-01-18")).isEqualTo(HttpStatus.OK);
        assertThat(statusHorariosLivres(cenario.profissional(), cenario.servico(), "2027-01-19"))
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void recusaProfissionalInativoServicoInativoOuNaoAtendido() {
        var cenario = cenarioComJornada("08:00", "09:00");
        UUID outroServico = cadastrarServico("Barba");

        assertThat(statusHorariosLivres(cenario.profissional(), outroServico, QUARTA)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        api.patch().uri("/api/servicos/{id}/inativar", cenario.servico()).retrieve().toBodilessEntity();
        assertThat(statusHorariosLivres(cenario.profissional(), cenario.servico(), QUARTA)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        api.patch().uri("/api/servicos/{id}/ativar", cenario.servico()).retrieve().toBodilessEntity();
        api.patch().uri("/api/profissionais/{id}/inativar", cenario.profissional()).retrieve().toBodilessEntity();
        assertThat(statusHorariosLivres(cenario.profissional(), cenario.servico(), QUARTA)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void validaParametrosDosHorariosLivres() {
        var cenario = cenarioComJornada("08:00", "09:00");

        assertThat(statusHorariosLivres(cenario.profissional(), UUID.randomUUID(), QUARTA)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusHorariosLivres(UUID.randomUUID(), cenario.servico(), QUARTA)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusHorariosLivres(cenario.profissional(), cenario.servico(), "21/10/2026")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.get().uri("/api/profissionais/{id}/horarios-livres?data={d}", cenario.profissional(), QUARTA)
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private record Cenario(UUID profissional, UUID servico) {}

    /** Profissional que atende um serviço de 30 minutos às quartas, no intervalo informado. */
    private Cenario cenarioComJornada(String inicio, String fim) {
        UUID profissional = cadastrarProfissional();
        UUID servico = cadastrarServico("Corte");
        api.put().uri("/api/profissionais/{id}/servicos", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicosDoProfissionalRequest(Set.of(servico)))
                .retrieve()
                .toBodilessEntity();
        definirJornada(profissional, List.of(intervalo(WEDNESDAY, inicio, fim)));
        return new Cenario(profissional, servico);
    }

    private List<HorarioLivreResponse> horariosLivres(Cenario cenario, String data) {
        return api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data={d}", cenario.profissional(), cenario.servico(), data)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private HttpStatusCode statusHorariosLivres(UUID profissional, UUID servico, String data) {
        return api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data={d}", profissional, servico, data)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    private ResponseEntity<JornadaDto> definirJornada(UUID profissional, List<IntervaloDeJornadaDto> intervalos) {
        return api.put().uri("/api/profissionais/{id}/jornada", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JornadaDto(intervalos))
                .retrieve()
                .toEntity(JornadaDto.class);
    }

    private List<IntervaloDeJornadaDto> jornada(UUID profissional) {
        return api.get().uri("/api/profissionais/{id}/jornada", profissional).retrieve().body(JornadaDto.class).intervalos();
    }

    private ResponseEntity<BloqueioResponse> bloquear(UUID profissional, String inicio, String fim, String motivo) {
        return api.post().uri("/api/profissionais/{id}/bloqueios", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloqueioRequest(Instant.parse(inicio), Instant.parse(fim), motivo))
                .retrieve()
                .toEntity(BloqueioResponse.class);
    }

    private HttpStatusCode statusBloqueio(UUID profissional, String json) {
        return api.post().uri("/api/profissionais/{id}/bloqueios", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    private HttpStatusCode statusListagemDeBloqueios(UUID profissional, String de, String ate) {
        return api.get().uri("/api/profissionais/{id}/bloqueios?de={de}&ate={ate}", profissional, de, ate)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    private List<BloqueioResponse> listarBloqueios(UUID profissional, String filtro) {
        return api.get().uri("/api/profissionais/" + profissional + "/bloqueios" + filtro)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private UUID cadastrarProfissional() {
        return cadastrarProfissional("Ana Souza", "ana@barbearia.com");
    }

    private UUID cadastrarProfissional(String nome, String email) {
        return api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest(nome, email, null))
                .retrieve()
                .body(ProfissionalResponse.class)
                .id();
    }

    private UUID cadastrarServico(String nome) {
        return api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest(nome, null, 30, new BigDecimal("40")))
                .retrieve()
                .body(ServicoResponse.class)
                .id();
    }

    private static IntervaloDeJornadaDto intervalo(DayOfWeek dia, String inicio, String fim) {
        return new IntervaloDeJornadaDto(dia, LocalTime.parse(inicio), LocalTime.parse(fim));
    }

    private static HorarioLivreResponse horario(String inicio, String fim) {
        return new HorarioLivreResponse(Instant.parse(inicio), Instant.parse(fim));
    }
}
