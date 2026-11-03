package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;
import com.guilhermetonelli.agendamento.shared.redis.FalhasDoRedis;

/**
 * Guarda no Redis os horários livres de um profissional num dia, para a tela de reserva não recalcular a cada consulta.
 *
 * <p>A chave da entrada leva uma versão do profissional. Toda mudança na agenda troca a versão
 * ({@link AgendaAlterada}), e as entradas antigas deixam de ser lidas e expiram sozinhas. A versão é um token
 * aleatório: se o Redis a perder, a próxima nunca repete uma antiga que ainda tenha entradas guardadas.
 *
 * <p>É só aceleração. Qualquer falha do Redis vira consulta ao banco, e quem reserva nunca passa por aqui: a reserva
 * confere a agenda de novo, no banco.
 */
@Component
class CacheDeHorarios {

    private static final Duration VALIDADE_DA_VERSAO = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final Duration validade;
    private final FalhasDoRedis falhas;

    CacheDeHorarios(StringRedisTemplate redis, AgendamentoProperties propriedades, FalhasDoRedis falhas) {
        this.redis = redis;
        this.validade = propriedades.cacheHorarios().ttl();
        this.falhas = falhas;
    }

    /**
     * Os horários do cache ou, num miss, numa falha do Redis ou num valor guardado que não se lê, o que {@code calculo}
     * devolver (que é então guardado).
     */
    List<Periodo> livres(UUID profissionalId, long duracaoEmMinutos, LocalDate data, Supplier<List<Periodo>> calculo) {
        String chave = null;
        if (falhas.podeTentar()) {
            try {
                chave = chaveDaEntrada(profissionalId, duracaoEmMinutos, data);
                String guardado = redis.opsForValue().get(chave);
                falhas.respondeu();
                if (guardado != null) {
                    return ler(guardado);
                }
            } catch (RuntimeException e) {
                falhas.registrar("leitura do cache de horários", e);
            }
        }
        List<Periodo> calculados = calculo.get();
        if (chave != null) {
            guardar(chave, calculados);
        }
        return calculados;
    }

    /** Descarta o que está guardado deste profissional. Falha do Redis só vira aviso no log. */
    void invalidar(UUID profissionalId) {
        if (!falhas.podeTentar()) {
            return;
        }
        try {
            redis.opsForValue().set(chaveDaVersao(profissionalId), UUID.randomUUID().toString(), VALIDADE_DA_VERSAO);
            falhas.respondeu();
        } catch (RuntimeException e) {
            falhas.registrar("invalidação do cache de horários", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void aoAlterarAgenda(AgendaAlterada evento) {
        invalidar(evento.profissionalId());
    }

    private String chaveDaEntrada(UUID profissionalId, long duracaoEmMinutos, LocalDate data) {
        return "horarios:%s:%s:%d:%s".formatted(profissionalId, versao(profissionalId), duracaoEmMinutos, data);
    }

    /** A versão atual; sem ela (nunca criada, expirada ou perdida), cria uma nova, que nenhuma entrada guardada conhece. */
    private String versao(UUID profissionalId) {
        String chave = chaveDaVersao(profissionalId);
        String atual = redis.opsForValue().get(chave);
        if (atual != null) {
            return atual;
        }
        String nova = UUID.randomUUID().toString();
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(chave, nova, VALIDADE_DA_VERSAO))) {
            return nova;
        }
        String concorrente = redis.opsForValue().get(chave);
        return concorrente != null ? concorrente : nova;
    }

    private void guardar(String chave, List<Periodo> horarios) {
        try {
            redis.opsForValue().set(chave, escrever(horarios), validade);
        } catch (RuntimeException e) {
            falhas.registrar("gravação do cache de horários", e);
        }
    }

    static String chaveDaVersao(UUID profissionalId) {
        return "agenda:" + profissionalId + ":versao";
    }

    /** {@code inicio,fim;inicio,fim}, em segundos desde 1970. Dia sem horários é o texto vazio. */
    static String escrever(List<Periodo> horarios) {
        return horarios.stream()
                .map(horario -> horario.inicio().getEpochSecond() + "," + horario.fim().getEpochSecond())
                .collect(Collectors.joining(";"));
    }

    /** O contrário de {@link #escrever}; texto fora desse formato lança {@link IllegalArgumentException}. */
    static List<Periodo> ler(String texto) {
        if (texto.isEmpty()) {
            return List.of();
        }
        try {
            return Arrays.stream(texto.split(";", -1)).map(CacheDeHorarios::lerPeriodo).toList();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Horário fora do intervalo aceito: " + texto, e);
        }
    }

    private static Periodo lerPeriodo(String texto) {
        String[] pontas = texto.split(",", -1);
        if (pontas.length != 2) {
            throw new IllegalArgumentException("Período inválido: " + texto);
        }
        return new Periodo(Instant.ofEpochSecond(Long.parseLong(pontas[0])), Instant.ofEpochSecond(Long.parseLong(pontas[1])));
    }
}
