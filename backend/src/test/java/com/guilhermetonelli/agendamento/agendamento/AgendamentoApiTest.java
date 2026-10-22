package com.guilhermetonelli.agendamento.agendamento;

import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.Pagina;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.agendamento.dto.CancelamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.jayway.jsonpath.JsonPath;

/** Jornada de quarta, 21/10/2026, das 08:00 às 12:00 em São Paulo (11:00 às 15:00 UTC); serviço de 30 minutos. */
class AgendamentoApiTest extends IntegracaoTest {

    private static final String AS_8H = "2026-10-21T11:00:00Z";
    private static final String AS_8H15 = "2026-10-21T11:15:00Z";
    private static final String AS_8H30 = "2026-10-21T11:30:00Z";
    private static final String AS_9H = "2026-10-21T12:00:00Z";

    @Autowired
    private EntityManagerFactory emf;

    private UUID corte;
    private UUID ana;
    private UUID bruno;
    private UUID carlos;
    private UUID diana;

    @BeforeEach
    void cadastrarCenario() {
        corte = cadastrarServico("Corte");
        ana = cadastrarProfissionalComJornada("Ana Souza", "ana@barbearia.com");
        bruno = cadastrarProfissionalComJornada("Bruno Dias", "bruno@barbearia.com");
        carlos = cadastrarCliente("Carlos Lima", "carlos@gmail.com");
        diana = cadastrarCliente("Diana Reis", "diana@gmail.com");
    }

    @Test
    void reservaUmHorarioLivre() {
        var resposta = reservar(ana, carlos, AS_8H);
        AgendamentoResponse agendamento = buscar(id(resposta));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/api/agendamentos/" + agendamento.id());
        assertThat(agendamento.inicio()).isEqualTo(Instant.parse(AS_8H));
        assertThat(agendamento.fim()).isEqualTo(Instant.parse(AS_8H30));
        assertThat(agendamento.status()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(agendamento.preco()).isEqualByComparingTo("45.00");
        assertThat(agendamento.profissional().nome()).isEqualTo("Ana Souza");
        assertThat(agendamento.servico().nome()).isEqualTo("Corte");
        assertThat(agendamento.cliente().nome()).isEqualTo("Carlos Lima");
        assertThat(eventos.todos()).containsExactly(new AgendamentoCriado(
                agendamento.id(), ana, carlos, Instant.parse(AS_8H), Instant.parse(AS_8H30)));
    }

    @Test
    void horariosReservadosSaemDosHorariosLivres() {
        reservar(ana, carlos, AS_8H15);

        assertThat(horariosLivres(ana)).extracting(HorarioLivreResponse::inicio)
                .doesNotContain(Instant.parse(AS_8H), Instant.parse(AS_8H15), Instant.parse(AS_8H30))
                .first().isEqualTo(Instant.parse("2026-10-21T11:45:00Z"));
        assertThat(horariosLivres(bruno)).extracting(HorarioLivreResponse::inicio).contains(Instant.parse(AS_8H15));
    }

    @Test
    void guardaOPrecoDoMomentoDaReserva() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));

        api.put().uri("/api/servicos/{id}", corte)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Corte", null, 30, new BigDecimal("60.00")))
                .retrieve()
                .toBodilessEntity();

        assertThat(buscar(agendamento).preco()).isEqualByComparingTo("45.00");
    }

    @Test
    void recusaHorariosQueNaoEstariamNaListaDeLivres() {
        bloquear(ana, AS_9H, "2026-10-21T13:00:00Z");

        assertThat(reservar(ana, carlos, "2026-10-21T11:05:00Z")).satisfies(regraDeNegocio("a cada 15 minutos"));
        assertThat(reservar(ana, carlos, "2026-10-21T10:30:00Z")).satisfies(regraDeNegocio("fora da jornada"));
        assertThat(reservar(ana, carlos, "2026-10-21T14:45:00Z")).satisfies(regraDeNegocio("fora da jornada"));
        assertThat(reservar(ana, carlos, "2026-10-21T12:45:00Z")).satisfies(regraDeNegocio("bloqueado"));
        assertThat(reservar(ana, carlos, "2026-10-21T11:00:00.5Z")).satisfies(regraDeNegocio("a cada 15 minutos"));
        assertThat(reservar(ana, carlos, "2027-01-20T11:00:00Z")).satisfies(regraDeNegocio("só está aberta até"));
        assertThat(reservar(ana, carlos, "+300000-01-01T11:00:00Z")).satisfies(regraDeNegocio("só está aberta até"));

        relogio.ajustar(Instant.parse(AS_8H));
        assertThat(reservar(ana, carlos, AS_8H)).satisfies(regraDeNegocio("já passou"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agendamento", Integer.class)).isZero();
    }

    @Test
    void exigeAntecedenciaMinimaParaReservar() {
        relogio.ajustar(Instant.parse("2026-10-21T09:45:00Z"));

        assertThat(reservar(ana, carlos, AS_8H)).satisfies(regraDeNegocio("pelo menos 1h30 de antecedência"));
        assertThat(reservar(ana, carlos, AS_8H15).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void recusaBloqueioSobreReservaEListaOsAfetados() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));

        var recusado = bloquearComResposta(ana, "2026-10-21T10:00:00Z", AS_9H);
        cancelar(agendamento, null);
        var aceito = bloquearComResposta(ana, "2026-10-21T10:00:00Z", AS_9H);

        assertThat(recusado).satisfies(conflito("O período tem 1 agendamento"));
        assertThat(JsonPath.<List<String>>read(recusado.getBody(), "$.agendamentos[*].id"))
                .containsExactly(agendamento.toString());
        assertThat(aceito.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reservaEBloqueioSimultaneosNuncaConvivem() throws Exception {
        List<ResponseEntity<String>> respostas = emParalelo(List.of(
                () -> reservar(ana, carlos, AS_8H),
                () -> bloquearComResposta(ana, AS_8H, AS_9H)));

        assertThat(respostas).filteredOn(r -> r.getStatusCode().is2xxSuccessful()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agendamento", Integer.class)
                + jdbc.queryForObject("SELECT count(*) FROM bloqueio", Integer.class)).isEqualTo(1);
    }

    @Test
    void inativarProfissionalMantemOsAgendamentosJaMarcados() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));

        var inativacao = api.patch().uri("/api/profissionais/{id}/inativar", ana).retrieve().toBodilessEntity();

        assertThat(inativacao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(buscar(agendamento).status()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(reservar(ana, diana, AS_9H)).satisfies(regraDeNegocio("inativo"));
        assertThat(cancelar(agendamento, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void recusaProfissionalInativoServicoInativoOuNaoAtendido() {
        UUID barba = cadastrarServico("Barba");

        assertThat(reservar(ana, barba, carlos, AS_8H)).satisfies(regraDeNegocio("não atende"));

        api.patch().uri("/api/servicos/{id}/inativar", corte).retrieve().toBodilessEntity();
        assertThat(reservar(ana, carlos, AS_8H)).satisfies(regraDeNegocio("não está disponível"));

        api.patch().uri("/api/servicos/{id}/ativar", corte).retrieve().toBodilessEntity();
        api.patch().uri("/api/profissionais/{id}/inativar", ana).retrieve().toBodilessEntity();
        assertThat(reservar(ana, carlos, AS_8H)).satisfies(regraDeNegocio("inativo"));
    }

    @Test
    void validaOsDadosDaReserva() {
        assertThat(reservar(UUID.randomUUID(), corte, carlos, AS_8H).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reservar(ana, UUID.randomUUID(), carlos, AS_8H).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reservar(ana, corte, UUID.randomUUID(), AS_8H).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var semCampos = api.post().uri("/api/agendamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);

        assertThat(semCampos.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JsonPath.<List<String>>read(semCampos.getBody(), "$.erros[*].campo"))
                .containsExactly("clienteId", "inicio", "profissionalId", "servicoId");
    }

    @Test
    void ninguemFicaEmDoisLugaresAoMesmoTempo() {
        reservar(ana, carlos, AS_8H);

        assertThat(reservar(ana, diana, AS_8H15)).satisfies(conflito("o profissional já tem um agendamento"));
        assertThat(reservar(bruno, carlos, AS_8H15)).satisfies(conflito("o cliente já tem um agendamento"));
        assertThat(reservar(ana, diana, AS_8H30).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reservasSimultaneasComOMesmoProfissionalSoUmaVence() throws Exception {
        List<UUID> clientes = IntStream.range(0, 10)
                .mapToObj(i -> cadastrarCliente("Cliente " + i, "cliente" + i + "@gmail.com"))
                .toList();

        List<ResponseEntity<String>> respostas = emParalelo(clientes.stream()
                .map(cliente -> (Tarefa) () -> reservar(ana, cliente, AS_8H))
                .toList());

        assertThat(respostas).extracting(ResponseEntity::getStatusCode)
                .containsOnly(HttpStatus.CREATED, HttpStatus.CONFLICT)
                .filteredOn(HttpStatus.CREATED::equals).hasSize(1);
        assertThat(respostas).filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .allSatisfy(conflito("Horário indisponível"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agendamento", Integer.class)).isEqualTo(1);
    }

    @Test
    void reservasSimultaneasDoMesmoClienteSoUmaVence() throws Exception {
        List<UUID> profissionais = IntStream.range(0, 5)
                .mapToObj(i -> cadastrarProfissionalComJornada("Profissional " + i, "profissional" + i + "@barbearia.com"))
                .toList();

        List<ResponseEntity<String>> respostas = emParalelo(profissionais.stream()
                .map(profissional -> (Tarefa) () -> reservar(profissional, carlos, AS_8H))
                .toList());

        assertThat(respostas).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
        assertThat(respostas).filteredOn(r -> r.getStatusCode() != HttpStatus.CREATED)
                .hasSize(4)
                .allSatisfy(conflito("Horário indisponível"));
    }

    @Test
    void listaComFiltrosEmOrdemCronologica() {
        UUID das9 = id(reservar(ana, carlos, AS_9H));
        UUID das8 = id(reservar(ana, diana, AS_8H));
        UUID doBruno = id(reservar(bruno, carlos, AS_8H30));
        UUID daOutraSemana = id(reservar(ana, carlos, "2026-10-28T11:00:00Z"));
        cancelar(das9, "Imprevisto");

        assertThat(listar("")).containsExactly(das8, doBruno, das9, daOutraSemana);
        assertThat(listar("?profissionalId=" + ana)).containsExactly(das8, das9, daOutraSemana);
        assertThat(listar("?clienteId=" + carlos + "&status=CONFIRMADO")).containsExactly(doBruno, daOutraSemana);
        assertThat(listar("?de=2026-10-21&ate=2026-10-21")).containsExactly(das8, doBruno, das9);
        assertThat(listar("?de=2026-10-22")).containsExactly(daOutraSemana);
        assertThat(statusDaListagem("?de=2026-10-22&ate=2026-10-21")).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(statusDaListagem("?status=PENDENTE")).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void paginasNaoRepetemAgendamentosComOMesmoInicio() {
        List<UUID> agendamentos = List.of(id(reservar(ana, carlos, AS_8H)), id(reservar(bruno, diana, AS_8H)));

        List<UUID> paginas = new ArrayList<>(listar("?size=1&page=0"));
        paginas.addAll(listar("?size=1&page=1"));

        assertThat(paginas).containsExactlyInAnyOrderElementsOf(agendamentos);
    }

    @Test
    void listagemTrazOsResumosNaMesmaConsulta() {
        reservar(ana, carlos, AS_8H);
        reservar(bruno, diana, AS_8H);
        reservar(ana, diana, AS_9H);
        reservar(bruno, carlos, AS_9H);
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        estatisticas.setStatisticsEnabled(true);
        try {
            assertThat(listar("")).hasSize(4);
            assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            estatisticas.setStatisticsEnabled(false);
        }
    }

    @Test
    void buscaAgendamentoInexistente() {
        var resposta = api.get().uri("/api/agendamentos/{id}", UUID.randomUUID()).retrieve().toBodilessEntity();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelaEOHorarioVoltaAFicarLivre() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));
        eventos.limpar();

        var resposta = cancelar(agendamento, "  Imprevisto ");
        AgendamentoResponse cancelado = buscar(agendamento);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelado.status()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(cancelado.canceladoEm()).isEqualTo(relogio.instant());
        assertThat(cancelado.motivoCancelamento()).isEqualTo("Imprevisto");
        assertThat(eventos.todos()).containsExactly(
                new AgendamentoCancelado(agendamento, ana, carlos, Instant.parse(AS_8H), "Imprevisto"));
        assertThat(horariosLivres(ana)).extracting(HorarioLivreResponse::inicio).contains(Instant.parse(AS_8H));
        assertThat(reservar(ana, diana, AS_8H).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cancelar(agendamento, null)).satisfies(regraDeNegocio("status CANCELADO"));
    }

    @Test
    void cancelaSemCorpo() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));

        var resposta = api.patch().uri("/api/agendamentos/{id}/cancelar", agendamento).retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(buscar(agendamento).status()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(buscar(agendamento).motivoCancelamento()).isNull();
    }

    @Test
    void clienteCancelaComAntecedenciaMinima() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));
        api = apiComo(comoCliente(carlos));

        relogio.ajustar(Instant.parse("2026-10-21T09:01:00Z"));
        var tarde = cancelar(agendamento, null);

        relogio.ajustar(Instant.parse("2026-10-21T09:00:00Z"));
        var noLimite = cancelar(agendamento, null);

        assertThat(tarde).satisfies(regraDeNegocio("pelo menos 2h de antecedência"));
        assertThat(noLimite.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void clienteReagendaComAntecedenciaMinima() {
        UUID agendamento = id(reservar(ana, carlos, AS_9H));
        api = apiComo(comoCliente(carlos));

        relogio.ajustar(Instant.parse("2026-10-21T10:01:00Z"));
        var tarde = reagendar(agendamento, "2026-10-21T13:00:00Z");

        relogio.ajustar(Instant.parse("2026-10-21T10:00:00Z"));
        var noLimite = reagendar(agendamento, "2026-10-21T13:00:00Z");

        assertThat(tarde).satisfies(regraDeNegocio("O reagendamento precisa ser feito com pelo menos 2h de antecedência"));
        assertThat(noLimite.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void soOClienteTemPrazoParaReagendar() {
        UUID agendamento = id(reservar(ana, carlos, AS_9H));

        relogio.ajustar(Instant.parse("2026-10-21T11:59:00Z"));

        assertThat(reagendar(agendamento, "2026-10-21T13:30:00Z").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reagendaComADuracaoAtualEOPrecoCombinado() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));
        api.put().uri("/api/servicos/{id}", corte)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Corte", null, 45, new BigDecimal("60.00")))
                .retrieve()
                .toBodilessEntity();
        eventos.limpar();

        var resposta = reagendar(agendamento, AS_9H);
        AgendamentoResponse reagendado = buscar(agendamento);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reagendado.inicio()).isEqualTo(Instant.parse(AS_9H));
        assertThat(reagendado.fim()).isEqualTo(Instant.parse("2026-10-21T12:45:00Z"));
        assertThat(reagendado.preco()).isEqualByComparingTo("45.00");
        assertThat(eventos.todos()).containsExactly(new AgendamentoReagendado(
                agendamento, ana, carlos, Instant.parse(AS_8H), Instant.parse(AS_9H), Instant.parse("2026-10-21T12:45:00Z")));
        assertThat(horariosLivres(ana)).extracting(HorarioLivreResponse::inicio).contains(Instant.parse(AS_8H));
    }

    @Test
    void reagendamentoNaoConflitaComOProprioHorario() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));

        assertThat(reagendar(agendamento, AS_8H15).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(buscar(agendamento).inicio()).isEqualTo(Instant.parse(AS_8H15));
    }

    @Test
    void reagendamentoSegueAsRegrasDeUmaReservaNova() {
        UUID agendamento = id(reservar(ana, carlos, AS_8H));
        reservar(ana, diana, AS_9H);
        reservar(bruno, carlos, "2026-10-21T13:00:00Z");

        assertThat(reagendar(agendamento, "2026-10-21T12:15:00Z")).satisfies(conflito("o profissional já tem"));
        assertThat(reagendar(agendamento, "2026-10-21T13:00:00Z")).satisfies(conflito("o cliente já tem"));
        assertThat(reagendar(agendamento, "2026-10-21T10:00:00Z")).satisfies(regraDeNegocio("fora da jornada"));
        assertThat(buscar(agendamento).inicio()).isEqualTo(Instant.parse(AS_8H));
    }

    @Test
    void naoReagendaCanceladoNemJaIniciado() {
        UUID cancelado = id(reservar(ana, carlos, AS_8H));
        cancelar(cancelado, null);
        UUID iniciado = id(reservar(ana, diana, AS_9H));

        assertThat(reagendar(cancelado, AS_8H30)).satisfies(regraDeNegocio("status CANCELADO"));

        relogio.ajustar(Instant.parse(AS_9H));
        assertThat(reagendar(iniciado, "2026-10-21T13:00:00Z")).satisfies(regraDeNegocio("já começou"));
    }

    @Test
    void concluiOuRegistraFaltaSoDepoisDoInicio() {
        UUID atendido = id(reservar(ana, carlos, AS_8H));
        UUID faltou = id(reservar(ana, diana, AS_9H));

        assertThat(encerrar(atendido, "concluir")).satisfies(regraDeNegocio("depois do início"));

        relogio.ajustar(Instant.parse(AS_9H));
        assertThat(encerrar(atendido, "concluir").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(encerrar(faltou, "nao-compareceu").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(buscar(atendido).status()).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(buscar(faltou).status()).isEqualTo(StatusAgendamento.NAO_COMPARECEU);
        assertThat(encerrar(atendido, "nao-compareceu")).satisfies(regraDeNegocio("status CONCLUIDO"));
        assertThat(cancelar(faltou, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void acoesEmAgendamentoInexistenteRetornam404() {
        UUID inexistente = UUID.randomUUID();

        assertThat(cancelar(inexistente, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reagendar(inexistente, AS_8H).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(encerrar(inexistente, "concluir").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private interface Tarefa {
        ResponseEntity<String> executar() throws Exception;
    }

    /** Dispara todas as tarefas ao mesmo tempo e espera as respostas. */
    private static List<ResponseEntity<String>> emParalelo(List<Tarefa> tarefas) throws Exception {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tarefas.size())) {
            var futuros = tarefas.stream()
                    .map(tarefa -> executor.submit(() -> {
                        largada.await();
                        return tarefa.executar();
                    }))
                    .toList();
            largada.countDown();
            var respostas = new ArrayList<ResponseEntity<String>>();
            for (var futuro : futuros) {
                respostas.add(futuro.get());
            }
            return respostas;
        }
    }

    private static Consumer<ResponseEntity<String>> regraDeNegocio(String trecho) {
        return erro(HttpStatus.UNPROCESSABLE_CONTENT, trecho);
    }

    private static Consumer<ResponseEntity<String>> conflito(String trecho) {
        return erro(HttpStatus.CONFLICT, trecho);
    }

    private static Consumer<ResponseEntity<String>> erro(HttpStatus status, String trecho) {
        return resposta -> {
            assertThat(resposta.getStatusCode()).isEqualTo(status);
            assertThat(JsonPath.<String>read(resposta.getBody(), "$.detail")).contains(trecho);
        };
    }

    private ResponseEntity<String> reservar(UUID profissional, UUID cliente, String inicio) {
        return reservar(profissional, corte, cliente, inicio);
    }

    private ResponseEntity<String> reservar(UUID profissional, UUID servico, UUID cliente, String inicio) {
        return api.post().uri("/api/agendamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AgendamentoRequest(profissional, servico, cliente, Instant.parse(inicio)))
                .retrieve()
                .toEntity(String.class);
    }

    private static UUID id(ResponseEntity<String> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(resposta.getBody(), "$.id"));
    }

    private AgendamentoResponse buscar(UUID id) {
        return api.get().uri("/api/agendamentos/{id}", id).retrieve().body(AgendamentoResponse.class);
    }

    private List<UUID> listar(String filtro) {
        Pagina<AgendamentoResponse> pagina = api.get().uri("/api/agendamentos" + filtro)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return pagina.content().stream().map(AgendamentoResponse::id).toList();
    }

    private HttpStatusCode statusDaListagem(String filtro) {
        return api.get().uri("/api/agendamentos" + filtro).retrieve().toBodilessEntity().getStatusCode();
    }

    private ResponseEntity<String> cancelar(UUID id, String motivo) {
        return api.patch().uri("/api/agendamentos/{id}/cancelar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelamentoRequest(motivo))
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> reagendar(UUID id, String inicio) {
        return api.patch().uri("/api/agendamentos/{id}/reagendar", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReagendamentoRequest(Instant.parse(inicio)))
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> encerrar(UUID id, String acao) {
        return api.patch().uri("/api/agendamentos/{id}/" + acao, id).retrieve().toEntity(String.class);
    }

    private List<HorarioLivreResponse> horariosLivres(UUID profissional) {
        return api.get()
                .uri("/api/profissionais/{id}/horarios-livres?servicoId={s}&data=2026-10-21", profissional, corte)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private void bloquear(UUID profissional, String inicio, String fim) {
        assertThat(bloquearComResposta(profissional, inicio, fim).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> bloquearComResposta(UUID profissional, String inicio, String fim) {
        return api.post().uri("/api/profissionais/{id}/bloqueios", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloqueioRequest(Instant.parse(inicio), Instant.parse(fim), null))
                .retrieve()
                .toEntity(String.class);
    }

    private UUID cadastrarServico(String nome) {
        return api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest(nome, null, 30, new BigDecimal("45.00")))
                .retrieve()
                .body(ServicoResponse.class)
                .id();
    }

    private UUID cadastrarProfissionalComJornada(String nome, String email) {
        UUID profissional = api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest(nome, email, null))
                .retrieve()
                .body(ProfissionalResponse.class)
                .id();
        api.put().uri("/api/profissionais/{id}/servicos", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicosDoProfissionalRequest(Set.of(corte)))
                .retrieve()
                .toBodilessEntity();
        api.put().uri("/api/profissionais/{id}/jornada", profissional)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JornadaDto(List.of(new IntervaloDeJornadaDto(WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)))))
                .retrieve()
                .toBodilessEntity();
        return profissional;
    }

    private UUID cadastrarCliente(String nome, String email) {
        return api.post().uri("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ClienteRequest(nome, email, null))
                .retrieve()
                .body(ClienteResponse.class)
                .id();
    }
}
