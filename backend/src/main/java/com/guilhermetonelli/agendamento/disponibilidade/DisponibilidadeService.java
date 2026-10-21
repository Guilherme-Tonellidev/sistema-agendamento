package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.servico.ServicoService;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/** Reúne jornada e bloqueios do profissional e delega o cálculo à {@link CalculadoraDeHorarios}. */
@Service
@Transactional(readOnly = true)
public class DisponibilidadeService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProfissionalService profissionalService;
    private final ServicoService servicoService;
    private final JornadaService jornadaService;
    private final BloqueioService bloqueioService;
    private final DisponibilidadeMapper mapper;
    private final AgendamentoProperties properties;
    private final Clock clock;
    private final CalculadoraDeHorarios calculadora;

    DisponibilidadeService(
            ProfissionalService profissionalService,
            ServicoService servicoService,
            JornadaService jornadaService,
            BloqueioService bloqueioService,
            DisponibilidadeMapper mapper,
            AgendamentoProperties properties,
            Clock clock) {
        this.profissionalService = profissionalService;
        this.servicoService = servicoService;
        this.jornadaService = jornadaService;
        this.bloqueioService = bloqueioService;
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
        LocalDate hoje = LocalDate.ofInstant(agora, properties.fusoHorario());
        if (data.isBefore(hoje)) {
            return List.of();
        }
        garantirDentroDaJanela(data, hoje);

        List<Periodo> livres = calculadora.horariosLivres(
                data,
                jornadaService.doProfissional(profissionalId),
                Duration.ofMinutes(servico.getDuracaoMinutos()),
                bloqueioService.bloqueados(profissionalId, Periodo.doDia(data, properties.fusoHorario())),
                agora);
        return livres.stream().map(mapper::paraResponse).toList();
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
            throw new RegraDeNegocioException("Só é possível consultar horários até " + limite.format(DATA) + ".");
        }
    }
}
