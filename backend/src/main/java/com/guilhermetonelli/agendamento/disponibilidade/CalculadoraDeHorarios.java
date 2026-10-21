package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Cálculo puro dos horários livres de um dia, sem Spring nem banco. */
final class CalculadoraDeHorarios {

    private final ZoneId fuso;
    private final long passoEmSegundos;

    CalculadoraDeHorarios(ZoneId fuso, Duration passo) {
        this.fuso = fuso;
        this.passoEmSegundos = passo.toSeconds();
    }

    /**
     * Horários com a duração pedida que cabem inteiros num intervalo da jornada do dia, não cruzam
     * nenhum período ocupado e começam a partir de {@code inicioMinimo}. Os inícios são múltiplos do
     * passo contados a partir da meia-noite local.
     */
    List<Periodo> horariosLivres(
            LocalDate data, List<IntervaloDeJornada> jornada, Duration duracao, List<Periodo> ocupados, Instant inicioMinimo) {

        Instant meiaNoite = data.atStartOfDay(fuso).toInstant();
        return jornada.stream()
                .filter(intervalo -> intervalo.getDiaSemana() == data.getDayOfWeek())
                .map(intervalo -> intervalo.naData(data, fuso))
                .sorted(Comparator.comparing(Periodo::inicio))
                .flatMap(turno -> candidatos(meiaNoite, turno, duracao))
                .filter(horario -> !horario.inicio().isBefore(inicioMinimo))
                .filter(horario -> ocupados.stream().noneMatch(horario::sobrepoe))
                .toList();
    }

    /** Se o instante cai num múltiplo exato do passo, contado a partir da meia-noite local do seu dia. */
    boolean alinhado(Instant inicio) {
        Instant meiaNoite = LocalDate.ofInstant(inicio, fuso).atStartOfDay(fuso).toInstant();
        Duration decorrido = Duration.between(meiaNoite, inicio);
        return decorrido.getNano() == 0 && decorrido.toSeconds() % passoEmSegundos == 0;
    }

    private Stream<Periodo> candidatos(Instant meiaNoite, Periodo turno, Duration duracao) {
        return Stream.iterate(
                        alinhar(meiaNoite, turno.inicio()),
                        inicio -> !inicio.plus(duracao).isAfter(turno.fim()),
                        inicio -> inicio.plusSeconds(passoEmSegundos))
                .map(inicio -> new Periodo(inicio, inicio.plus(duracao)));
    }

    private Instant alinhar(Instant origem, Instant instante) {
        long decorridos = Duration.between(origem, instante).toSeconds();
        return origem.plusSeconds(Math.ceilDiv(decorridos, passoEmSegundos) * passoEmSegundos);
    }
}
