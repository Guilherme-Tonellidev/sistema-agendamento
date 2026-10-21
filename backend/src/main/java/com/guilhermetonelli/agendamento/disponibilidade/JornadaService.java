package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

@Service
@Transactional(readOnly = true)
public class JornadaService {

    private static final Comparator<IntervaloDeJornada> ORDEM = Comparator
            .comparing(IntervaloDeJornada::getDiaSemana)
            .thenComparing(IntervaloDeJornada::getInicio);

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final IntervaloDeJornadaRepository repository;
    private final DisponibilidadeMapper mapper;
    private final ProfissionalService profissionalService;

    JornadaService(
            IntervaloDeJornadaRepository repository,
            DisponibilidadeMapper mapper,
            ProfissionalService profissionalService) {
        this.repository = repository;
        this.mapper = mapper;
        this.profissionalService = profissionalService;
    }

    public JornadaDto buscar(UUID profissionalId) {
        profissionalService.garantirQueExiste(profissionalId);
        return paraDto(doProfissional(profissionalId));
    }

    @Transactional
    public JornadaDto definir(UUID profissionalId, JornadaDto jornada) {
        profissionalService.obterComTrava(profissionalId);
        List<IntervaloDeJornada> intervalos = jornada.intervalos().stream()
                .map(i -> new IntervaloDeJornada(profissionalId, i.diaSemana(), i.inicio(), i.fim()))
                .sorted(ORDEM)
                .toList();
        garantirSemSobreposicao(intervalos);

        repository.removerDoProfissional(profissionalId);
        return paraDto(repository.saveAll(intervalos));
    }

    List<IntervaloDeJornada> doProfissional(UUID profissionalId) {
        return repository.findByProfissionalIdOrderByDiaSemanaAscInicioAsc(profissionalId);
    }

    /** Com a lista ordenada, basta comparar cada intervalo com o anterior. */
    private static void garantirSemSobreposicao(List<IntervaloDeJornada> ordenados) {
        for (int i = 1; i < ordenados.size(); i++) {
            IntervaloDeJornada anterior = ordenados.get(i - 1);
            IntervaloDeJornada atual = ordenados.get(i);
            if (atual.sobrepoe(anterior)) {
                throw new RegraDeNegocioException("Intervalos sobrepostos na %s: %s e %s.".formatted(
                        atual.getDiaSemana().getDisplayName(TextStyle.FULL, PT_BR),
                        descrever(anterior),
                        descrever(atual)));
            }
        }
    }

    private static String descrever(IntervaloDeJornada intervalo) {
        return intervalo.getInicio() + "–" + intervalo.getFim();
    }

    private JornadaDto paraDto(List<IntervaloDeJornada> intervalos) {
        return new JornadaDto(intervalos.stream().map(mapper::paraDto).toList());
    }
}
