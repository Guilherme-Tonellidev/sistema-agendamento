package com.guilhermetonelli.agendamento.agendamento;

import static com.guilhermetonelli.agendamento.agendamento.AgendamentoSpecifications.ativosDoCliente;
import static com.guilhermetonelli.agendamento.agendamento.AgendamentoSpecifications.ativosDoProfissional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.postgresql.util.PSQLException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoFiltro;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.agendamento.dto.EncaixeRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.cliente.Cliente;
import com.guilhermetonelli.agendamento.cliente.ClienteService;
import com.guilhermetonelli.agendamento.disponibilidade.DisponibilidadeService;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.servico.ServicoService;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;
import com.guilhermetonelli.agendamento.shared.domain.FaixaDeDatas;
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
public class AgendamentoService {

    private static final String PROFISSIONAL_OCUPADO =
            "Horário indisponível: o profissional já tem um agendamento nesse horário.";
    private static final String CLIENTE_OCUPADO =
            "Horário indisponível: o cliente já tem um agendamento nesse horário.";
    private static final String HORARIO_DISPUTADO =
            "Horário indisponível: outra reserva foi feita para esse horário ao mesmo tempo.";

    private static final String VIOLACAO_DE_EXCLUSAO = "23P01";

    /** Constraints de exclusão da tabela, com a mensagem de cada uma. */
    private static final Map<String, String> CONFLITOS_NO_BANCO = Map.of(
            "ex_agendamento_profissional", PROFISSIONAL_OCUPADO,
            "ex_agendamento_cliente", CLIENTE_OCUPADO);

    private final AgendamentoRepository repository;
    private final AgendamentoMapper mapper;
    private final ProfissionalService profissionalService;
    private final ServicoService servicoService;
    private final ClienteService clienteService;
    private final DisponibilidadeService disponibilidadeService;
    private final ApplicationEventPublisher eventos;
    private final AgendamentoProperties properties;
    private final Clock clock;

    AgendamentoService(
            AgendamentoRepository repository,
            AgendamentoMapper mapper,
            ProfissionalService profissionalService,
            ServicoService servicoService,
            ClienteService clienteService,
            DisponibilidadeService disponibilidadeService,
            ApplicationEventPublisher eventos,
            AgendamentoProperties properties,
            Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.profissionalService = profissionalService;
        this.servicoService = servicoService;
        this.clienteService = clienteService;
        this.disponibilidadeService = disponibilidadeService;
        this.eventos = eventos;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AgendamentoResponse reservar(AgendamentoRequest request, UsuarioLogado eu) {
        if (!eu.participaDe(request.profissionalId(), request.clienteId())) {
            throw new AccessDeniedException("Clientes reservam só para si; profissionais, só na própria agenda.");
        }
        Profissional profissional = profissionalService.obterComTrava(request.profissionalId());
        Cliente cliente = clienteService.obter(request.clienteId());
        return marcar(
                profissional, request.servicoId(), cliente, request.inicio(), properties.antecedenciaMinimaReserva());
    }

    /**
     * Quem chegou sem reserva entra na agenda cadastrado só com o nome. Sem antecedência mínima: ela protege o
     * profissional de reservas em cima da hora, e aqui é ele mesmo (ou o administrador) quem marca.
     */
    @Transactional
    public AgendamentoResponse encaixar(EncaixeRequest request, UsuarioLogado eu) {
        eu.garantirAgendaDe(request.profissionalId());
        Profissional profissional = profissionalService.obterComTrava(request.profissionalId());
        Cliente cliente = clienteService.cadastrarSemConta(request.nomeDoCliente(), request.telefone());
        return marcar(profissional, request.servicoId(), cliente, request.inicio(), Duration.ZERO);
    }

    public Page<AgendamentoResponse> listar(AgendamentoFiltro pedido, Pageable pageable, UsuarioLogado eu) {
        AgendamentoFiltro filtro = pedido.visivelPara(eu);
        FaixaDeDatas faixa = FaixaDeDatas.entre(filtro.de(), filtro.ate(), properties.fusoHorario());
        // O id desempata agendamentos com o mesmo início, para nenhum item repetir ou sumir entre as páginas.
        var estavel = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
        return repository.findAll(AgendamentoSpecifications.de(filtro, faixa), estavel).map(mapper::paraResponse);
    }

    public AgendamentoResponse buscar(UUID id, UsuarioLogado eu) {
        return mapper.paraResponse(obter(id, eu));
    }

    @Transactional
    public AgendamentoResponse cancelar(UUID id, String motivo, UsuarioLogado eu) {
        Agendamento agendamento = obter(id, eu);
        agendamento.cancelar(motivo, Instant.now(clock), antecedenciaExigida(eu));
        repository.flush();
        eventos.publishEvent(new AgendamentoCancelado(
                id,
                profissionalId(agendamento),
                clienteId(agendamento),
                agendamento.getInicio(),
                agendamento.getMotivoCancelamento()));
        return mapper.paraResponse(agendamento);
    }

    /** Mesmas regras de uma reserva nova, com o mesmo profissional e serviço; o horário antigo não conflita. */
    @Transactional
    public AgendamentoResponse reagendar(UUID id, ReagendamentoRequest request, UsuarioLogado eu) {
        Agendamento agendamento = obter(id, eu);
        profissionalService.obterComTrava(profissionalId(agendamento));
        Instant agora = Instant.now(clock);
        Duration antecedencia = antecedenciaExigida(eu);
        agendamento.garantirQuePodeReagendar(agora, antecedencia);

        Periodo horario = disponibilidadeService.garantirHorarioNaAgenda(
                agendamento.getProfissional(),
                agendamento.getServico(),
                request.inicio(),
                properties.antecedenciaMinimaReserva());
        garantirSemConflito(
                profissionalId(agendamento), clienteId(agendamento), horario, AgendamentoSpecifications.exceto(id));

        Instant inicioAnterior = agendamento.getInicio();
        agendamento.reagendar(horario, agora, antecedencia);
        salvar(agendamento);
        eventos.publishEvent(new AgendamentoReagendado(
                id, profissionalId(agendamento), clienteId(agendamento), inicioAnterior, horario.inicio(), horario.fim()));
        return mapper.paraResponse(agendamento);
    }

    @Transactional
    public AgendamentoResponse concluir(UUID id, UsuarioLogado eu) {
        Agendamento agendamento = obterParaEncerrar(id, eu);
        agendamento.concluir(Instant.now(clock));
        return mapper.paraResponse(agendamento);
    }

    @Transactional
    public AgendamentoResponse registrarFalta(UUID id, UsuarioLogado eu) {
        Agendamento agendamento = obterParaEncerrar(id, eu);
        agendamento.registrarFalta(Instant.now(clock));
        return mapper.paraResponse(agendamento);
    }

    /**
     * Quem chama já pegou a trava no profissional: ela serializa reservas, bloqueios e mudanças de jornada da
     * mesma agenda, então a checagem de disponibilidade e a gravação acontecem sem ninguém mudar a agenda no meio.
     */
    private AgendamentoResponse marcar(
            Profissional profissional, UUID servicoId, Cliente cliente, Instant inicio, Duration antecedencia) {
        Servico servico = servicoService.obter(servicoId);
        Periodo horario = disponibilidadeService.garantirHorarioNaAgenda(profissional, servico, inicio, antecedencia);
        garantirSemConflito(profissional.getId(), cliente.getId(), horario, Specification.unrestricted());

        Agendamento agendamento = salvar(new Agendamento(profissional, servico, cliente, horario));
        eventos.publishEvent(new AgendamentoCriado(
                agendamento.getId(), profissional.getId(), cliente.getId(), horario.inicio(), horario.fim()));
        return mapper.paraResponse(agendamento);
    }

    private void garantirSemConflito(
            UUID profissionalId, UUID clienteId, Periodo horario, Specification<Agendamento> outros) {
        if (repository.exists(ativosDoProfissional(profissionalId, horario).and(outros))) {
            throw new ConflitoException(PROFISSIONAL_OCUPADO);
        }
        if (repository.exists(ativosDoCliente(clienteId, horario).and(outros))) {
            throw new ConflitoException(CLIENTE_OCUPADO);
        }
    }

    /**
     * A checagem prévia dá a mensagem amigável; quando duas reservas chegam ao mesmo tempo, quem decide
     * são as constraints de exclusão, conferidas aqui no flush. Como o PostgreSQL grava a linha antes de
     * procurar conflitos, duas gravações simultâneas podem esperar uma pela outra, e o banco aborta uma
     * delas por deadlock: também é um conflito de horário.
     */
    private Agendamento salvar(Agendamento agendamento) {
        try {
            return repository.saveAndFlush(agendamento);
        } catch (DataIntegrityViolationException ex) {
            throw conflitoNoBanco(ex);
        } catch (PessimisticLockingFailureException ex) {
            throw new ConflitoException(HORARIO_DISPUTADO);
        }
    }

    /** Usa o nome estruturado da constraint, que não depende do idioma das mensagens do servidor. */
    private static RuntimeException conflitoNoBanco(DataIntegrityViolationException ex) {
        if (ex.getMostSpecificCause() instanceof PSQLException sql
                && VIOLACAO_DE_EXCLUSAO.equals(sql.getSQLState())
                && sql.getServerErrorMessage() != null) {
            String mensagem = CONFLITOS_NO_BANCO.get(sql.getServerErrorMessage().getConstraint());
            if (mensagem != null) {
                return new ConflitoException(mensagem);
            }
        }
        return ex;
    }

    /** Quem não participa do agendamento recebe 404, e não 403: não descobre nem que ele existe. */
    private Agendamento obter(UUID id, UsuarioLogado eu) {
        return repository.findDetalhadoById(id)
                .filter(agendamento -> eu.participaDe(profissionalId(agendamento), clienteId(agendamento)))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento", id));
    }

    /** O prazo protege a agenda do profissional; ele mesmo e o administrador podem mudar até o início. */
    private Duration antecedenciaExigida(UsuarioLogado eu) {
        return eu.cliente() ? properties.antecedenciaMinimaCancelamento() : Duration.ZERO;
    }

    private Agendamento obterParaEncerrar(UUID id, UsuarioLogado eu) {
        Agendamento agendamento = obter(id, eu);
        eu.garantirAgendaDe(profissionalId(agendamento));
        return agendamento;
    }

    private static UUID profissionalId(Agendamento agendamento) {
        return agendamento.getProfissional().getId();
    }

    private static UUID clienteId(Agendamento agendamento) {
        return agendamento.getCliente().getId();
    }
}
