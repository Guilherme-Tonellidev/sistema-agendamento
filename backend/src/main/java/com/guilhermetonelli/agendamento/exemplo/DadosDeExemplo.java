package com.guilhermetonelli.agendamento.exemplo;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.disponibilidade.JornadaService;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.seguranca.AutenticacaoService;
import com.guilhermetonelli.agendamento.seguranca.dto.CadastroRequest;
import com.guilhermetonelli.agendamento.servico.ServicoService;
import com.guilhermetonelli.agendamento.servico.dto.ServicoFiltro;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;

/**
 * Deixa o ambiente de desenvolvimento pronto para usar: na primeira subida, com o banco ainda sem serviços,
 * cadastra três serviços, dois barbeiros com jornada, o acesso do Rafael e a conta de um cliente (as contas
 * estão no README). Só roda com {@code agendamento.dados-de-exemplo=true}, que apenas o perfil local liga.
 */
@Component
@ConditionalOnBooleanProperty("agendamento.dados-de-exemplo")
class DadosDeExemplo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDeExemplo.class);

    private final ServicoService servicos;
    private final ProfissionalService profissionais;
    private final JornadaService jornadas;
    private final AutenticacaoService autenticacao;

    DadosDeExemplo(
            ServicoService servicos,
            ProfissionalService profissionais,
            JornadaService jornadas,
            AutenticacaoService autenticacao) {
        this.servicos = servicos;
        this.profissionais = profissionais;
        this.jornadas = jornadas;
        this.autenticacao = autenticacao;
    }

    /** Tudo numa transação: se algo falhar, o banco continua sem serviços e a próxima subida tenta de novo. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!servicos.listar(new ServicoFiltro(null, null), Pageable.ofSize(1)).isEmpty()) {
            return;
        }
        UUID corte = servico("Corte", "Tesoura e máquina", 30, 45);
        UUID barba = servico("Barba", "Toalha quente e navalha", 30, 35);
        UUID corteEBarba = servico("Corte + barba", null, 60, 70);

        UUID rafael = barbeiro("Rafael Souza", "rafael@tonelli.local", corte, barba, corteEBarba);
        barbeiro("Bruno Lima", "bruno@tonelli.local", corte, corteEBarba);
        autenticacao.definirAcessoDeProfissional(rafael, "barbeiro-local-123");
        autenticacao.criarContaDeCliente(
                new CadastroRequest("Carlos Mendes", "carlos@tonelli.local", "14998887777", "cliente-local-123"));

        log.info("Dados de exemplo cadastrados: 3 serviços, 2 barbeiros e as contas do README.");
    }

    private UUID servico(String nome, String descricao, int minutos, int preco) {
        return servicos.cadastrar(new ServicoRequest(nome, descricao, minutos, BigDecimal.valueOf(preco))).id();
    }

    private UUID barbeiro(String nome, String email, UUID... atende) {
        UUID id = profissionais.cadastrar(new ProfissionalRequest(nome, email, null)).id();
        profissionais.definirServicos(id, new ServicosDoProfissionalRequest(Set.of(atende)));
        jornadas.definir(id, jornada());
        return id;
    }

    /** De segunda a sexta, das 9h às 12h e das 13h às 20h; no sábado, das 8h às 18h. */
    private static JornadaDto jornada() {
        List<IntervaloDeJornadaDto> intervalos = new ArrayList<>();
        for (DayOfWeek dia : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) {
            intervalos.add(intervalo(dia, 9, 12));
            intervalos.add(intervalo(dia, 13, 20));
        }
        intervalos.add(intervalo(DayOfWeek.SATURDAY, 8, 18));
        return new JornadaDto(intervalos);
    }

    private static IntervaloDeJornadaDto intervalo(DayOfWeek dia, int deHora, int ateHora) {
        return new IntervaloDeJornadaDto(dia, LocalTime.of(deHora, 0), LocalTime.of(ateHora, 0));
    }
}
