package com.guilhermetonelli.agendamento.disponibilidade;

import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CalculadoraDeHorariosTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 21);
    private static final Instant ANTES = Instant.parse("2026-10-20T00:00:00Z");
    private static final Duration MEIA_HORA = Duration.ofMinutes(30);

    private final CalculadoraDeHorarios calculadora = new CalculadoraDeHorarios(SAO_PAULO, Duration.ofMinutes(15));

    @Test
    void ofereceInicioACada15MinutosQueCabemNoIntervalo() {
        var livres = calculadora.horariosLivres(QUARTA, List.of(intervalo(WEDNESDAY, "08:00", "09:00")), MEIA_HORA, List.of(), ANTES);

        assertThat(inicios(livres)).containsExactly("11:00", "11:15", "11:30");
        assertThat(livres.getFirst().fim()).isEqualTo(Instant.parse("2026-10-21T11:30:00Z"));
    }

    @Test
    void alinhaOInicioAoProximoMultiploDoIntervalo() {
        var livres = calculadora.horariosLivres(QUARTA, List.of(intervalo(WEDNESDAY, "08:10", "09:00")), MEIA_HORA, List.of(), ANTES);

        assertThat(inicios(livres)).containsExactly("11:15", "11:30");
    }

    @Test
    void consideraTodosOsIntervalosDoDiaEmOrdem() {
        var jornada = List.of(
                intervalo(WEDNESDAY, "13:00", "13:30"),
                intervalo(WEDNESDAY, "08:00", "08:30"),
                intervalo(DayOfWeek.THURSDAY, "08:00", "18:00"));

        var livres = calculadora.horariosLivres(QUARTA, jornada, MEIA_HORA, List.of(), ANTES);

        assertThat(inicios(livres)).containsExactly("11:00", "16:00");
    }

    @Test
    void descartaHorariosQueCruzamPeriodosOcupadosMasAceitaEncostar() {
        var ocupado = new Periodo(Instant.parse("2026-10-21T11:30:00Z"), Instant.parse("2026-10-21T11:45:00Z"));

        var livres = calculadora.horariosLivres(QUARTA, List.of(intervalo(WEDNESDAY, "08:00", "09:30")), MEIA_HORA, List.of(ocupado), ANTES);

        assertThat(inicios(livres)).containsExactly("11:00", "11:45", "12:00");
    }

    @Test
    void ofereceSoHorariosAPartirDoInicioMinimo() {
        Instant inicioMinimo = Instant.parse("2026-10-21T11:15:00Z");

        var livres = calculadora.horariosLivres(
                QUARTA, List.of(intervalo(WEDNESDAY, "08:00", "09:00")), MEIA_HORA, List.of(), inicioMinimo);

        assertThat(inicios(livres)).containsExactly("11:15", "11:30");
    }

    @Test
    void naoOfereceNadaQuandoOServicoNaoCabeNoIntervalo() {
        var livres = calculadora.horariosLivres(QUARTA, List.of(intervalo(WEDNESDAY, "08:00", "08:20")), MEIA_HORA, List.of(), ANTES);

        assertThat(livres).isEmpty();
    }

    @Test
    void contaOTempoRealNaMudancaDeHorarioDeVerao() {
        ZoneId novaYork = ZoneId.of("America/New_York");
        var calculadoraNy = new CalculadoraDeHorarios(novaYork, MEIA_HORA);
        LocalDate inicioDoVerao = LocalDate.of(2026, 3, 8);

        var livres = calculadoraNy.horariosLivres(
                inicioDoVerao,
                List.of(intervalo(DayOfWeek.SUNDAY, "01:00", "04:00")),
                Duration.ofHours(1),
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"));

        // 01:00 às 04:00 locais são só duas horas reais, porque o relógio pula das 02:00 para as 03:00.
        assertThat(livres).extracting(Periodo::inicio).containsExactly(
                Instant.parse("2026-03-08T06:00:00Z"),
                Instant.parse("2026-03-08T06:30:00Z"),
                Instant.parse("2026-03-08T07:00:00Z"));
    }

    @Test
    void reconheceInicioAlinhadoAoIntervaloNoFusoDoNegocio() {
        assertThat(calculadora.alinhado(Instant.parse("2026-10-21T11:15:00Z"))).isTrue();
        assertThat(calculadora.alinhado(Instant.parse("2026-10-21T11:05:00Z"))).isFalse();
        assertThat(calculadora.alinhado(Instant.parse("2026-10-21T11:15:00.000001Z"))).isFalse();
    }

    @Test
    void alinhamentoContaAPartirDaMeiaNoiteLocal() {
        var deHoraEmHora = new CalculadoraDeHorarios(ZoneId.of("Asia/Kolkata"), Duration.ofHours(1));

        // Meia-noite em Calcutá é 18:30 UTC do dia anterior.
        assertThat(deHoraEmHora.alinhado(Instant.parse("2026-10-21T03:30:00Z"))).isTrue();
        assertThat(deHoraEmHora.alinhado(Instant.parse("2026-10-21T03:00:00Z"))).isFalse();
    }

    private static IntervaloDeJornada intervalo(DayOfWeek dia, String inicio, String fim) {
        return new IntervaloDeJornada(UUID.randomUUID(), dia, LocalTime.parse(inicio), LocalTime.parse(fim));
    }

    /** Horas de início em UTC, para as asserções ficarem curtas. */
    private static List<String> inicios(List<Periodo> horarios) {
        return horarios.stream().map(h -> h.inicio().toString().substring(11, 16)).toList();
    }
}
