package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.servico.ServicoService;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/** Reúne jornada, bloqueios e agendamentos do profissional e delega o cálculo à {@link CalculadoraDeHorarios}. */
@Service
@Transactional(readOnly = true)
public class DisponibilidadeService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProfissionalService profissionalService;
    private final ServicoService servicoService;
    private final JornadaService jornadaService;
    private final BloqueioService bloqueioService;
    private final AgendamentosAtivos agendamentosAtivos;
    private final DisponibilidadeMapper mapper;
    private final AgendamentoProperties properties;
    private final Clock clock;
    private final CalculadoraDeHorarios calculadora;

    DisponibilidadeService(
            ProfissionalService profissionalService,
            ServicoService servicoService,
            JornadaService jornadaService,
            BloqueioService bloqueioService,
            AgendamentosAtivos agendamentosAtivos,
            DisponibilidadeMapper mapper,
            AgendamentoProperties properties,
            Clock clock) {
        this.profissionalService = profissionalService;
        this.servicoService = servicoService;
        this.jornadaService = jornadaService;
        this.bloqueioService = bloqueioService;
        this.agendamentosAtivos = agendamentosAtivos;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.calculadora = new CalculadoraDeHorarios(properties.fusoHorario(), properties.intervaloEntreHorarios());
    }

    public List<HorarioLivreResponse> horariosLivres(UUID profissionalId, UUID servicoId, LocalDate data) {
        Profissional profissional = profissionalService.obterComServicos(profissionalId);
        Servico servico = servicoService.obter(servicoId);
        garantirQuePodeAtender(profissional, servico);

        Instant agora = Instant.now(clock);
        LocalDate hoje = hoje(agora);
        if (data.isBefore(hoje)) {
            return List.of();
        }
        garantirDentroDaJanela(data, hoje);

        Periodo dia = Periodo.doDia(data, properties.fusoHorario());
        List<Periodo> ocupados = Stream.concat(
                        bloqueioService.bloqueados(profissionalId, dia).stream(),
                        agendamentosAtivos.doProfissional(profissionalId, dia).stream().map(AgendamentoAtivo::horario))
                .toList();
        return livres(profissional, servico, data, ocupados, inicioMinimo(agora)).stream()
                .map(mapper::paraResponse)
                .toList();
    }

    /**
     * Garante que o horário que começa em {@code inicio} apareceria nos horários livres, desconsiderando os
     * agendamentos (quem reserva confere os conflitos, inclusive os do cliente). Devolve o horário completo.
     */
    public Periodo garantirHorarioNaAgenda(Profissional profissional, Servico servico, Instant inicio) {
        garantirQuePodeAtender(profissional, servico);
        Instant agora = Instant.now(clock);
        if (inicio.isBefore(inicioMinimo(agora))) {
            throw new RegraDeNegocioException(inicio.isAfter(agora)
                    ? "Reservas precisam de pelo menos %s de antecedência."
                            .formatted(Textos.duracao(properties.antecedenciaMinimaReserva()))
                    : "O horário escolhido já passou.");
        }
        LocalDate data = LocalDate.ofInstant(inicio, properties.fusoHorario());
        garantirDentroDaJanela(data, hoje(agora));
        if (!calculadora.alinhado(inicio)) {
            throw new RegraDeNegocioException("Os horários começam a cada %d minutos."
                    .formatted(properties.intervaloEntreHorarios().toMinutes()));
        }

        Periodo horario = new Periodo(inicio, inicio.plus(duracao(servico)));
        List<Periodo> bloqueados = bloqueioService.bloqueados(profissional.getId(), Periodo.doDia(data, properties.fusoHorario()));
        if (!livres(profissional, servico, data, bloqueados, inicio).contains(horario)) {
            throw new RegraDeNegocioException(
                    "O horário está fora da jornada de " + profissional.getNome() + " ou num período bloqueado.");
        }
        return horario;
    }

    private List<Periodo> livres(
            Profissional profissional, Servico servico, LocalDate data, List<Periodo> ocupados, Instant inicioMinimo) {
        return calculadora.horariosLivres(
                data, jornadaService.doProfissional(profissional.getId()), duracao(servico), ocupados, inicioMinimo);
    }

    private Instant inicioMinimo(Instant agora) {
        return agora.plus(properties.antecedenciaMinimaReserva());
    }

    private static Duration duracao(Servico servico) {
        return Duration.ofMinutes(servico.getDuracaoMinutos());
    }

    private LocalDate hoje(Instant agora) {
        return LocalDate.ofInstant(agora, properties.fusoHorario());
    }

    private static void garantirQuePodeAtender(Profissional profissional, Servico servico) {
        if (!profissional.isAtivo()) {
            throw new RegraDeNegocioException("O profissional " + profissional.getNome() + " está inativo.");
        }
        if (!servico.isAtivo()) {
            throw new RegraDeNegocioException("O serviço " + servico.getNome() + " não está disponível.");
        }
        if (!profissional.atende(servico)) {
            throw new RegraDeNegocioException(
                    "O profissional " + profissional.getNome() + " não atende o serviço " + servico.getNome() + ".");
        }
    }

    private void garantirDentroDaJanela(LocalDate data, LocalDate hoje) {
        LocalDate limite = hoje.plus(properties.janelaDeReserva());
        if (data.isAfter(limite)) {
            throw new RegraDeNegocioException("A agenda só está aberta até " + limite.format(DATA) + ".");
        }
    }
}
