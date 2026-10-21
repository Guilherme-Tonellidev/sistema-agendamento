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
     * nenhum período ocupado e começam depois de {@code agora}. Os inícios são múltiplos do passo
     * contados a partir da meia-noite local.
     */
    List<Periodo> horariosLivres(
            LocalDate data, List<IntervaloDeJornada> jornada, Duration duracao, List<Periodo> ocupados, Instant agora) {

        Instant meiaNoite = data.atStartOfDay(fuso).toInstant();
        return jornada.stream()
                .filter(intervalo -> intervalo.getDiaSemana() == data.getDayOfWeek())
                .map(intervalo -> intervalo.naData(data, fuso))
                .sorted(Comparator.comparing(Periodo::inicio))
                .flatMap(turno -> candidatos(meiaNoite, turno, duracao))
                .filter(horario -> horario.inicio().isAfter(agora))
                .filter(horario -> ocupados.stream().noneMatch(horario::sobrepoe))
                .toList();
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
