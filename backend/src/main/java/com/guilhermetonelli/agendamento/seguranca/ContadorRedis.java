package com.guilhermetonelli.agendamento.seguranca;

import static com.guilhermetonelli.agendamento.seguranca.LimiteDeTentativas.JANELA;
import static com.guilhermetonelli.agendamento.seguranca.LimiteDeTentativas.MAXIMO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Conta as tentativas de login no Redis: a contagem vale entre as instâncias da API e sobrevive a reinícios.
 * A janela é fixa, aberta pela primeira tentativa. Falhas do Redis sobem como exceção para o
 * {@link LimiteDeTentativas}, que passa a contar na memória.
 */
@Component
class ContadorRedis {

    private static final String PREFIXO = "login:";

    /**
     * Soma a tentativa e garante o prazo num passo só do Redis, então duas requisições em paralelo nunca leem a mesma
     * contagem. Devolve {contagem, milissegundos até a janela fechar}; o prazo só é renovado se a chave estiver sem
     * ele, para nenhuma conta ficar bloqueada para sempre.
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SOMAR_TENTATIVA = new DefaultRedisScript<>("""
            local n = redis.call("INCR", KEYS[1])
            if n == 1 then redis.call("PEXPIRE", KEYS[1], ARGV[1]) end
            local t = redis.call("PTTL", KEYS[1])
            if t < 0 then redis.call("PEXPIRE", KEYS[1], ARGV[1]) t = tonumber(ARGV[1]) end
            return {n, t}
            """, List.class);

    private final StringRedisTemplate redis;

    ContadorRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** {@link Duration#ZERO} se a tentativa passa; senão, quanto falta para a janela fechar (sempre maior que zero). */
    Duration registrar(String chave) {
        List<?> resultado = redis.execute(SOMAR_TENTATIVA, List.of(chaveNoRedis(chave)), String.valueOf(JANELA.toMillis()));
        long tentativas = ((Number) resultado.get(0)).longValue();
        long falta = ((Number) resultado.get(1)).longValue();
        return tentativas <= MAXIMO ? Duration.ZERO : Duration.ofMillis(Math.max(1, falta));
    }

    void limpar(String chave) {
        redis.delete(chaveNoRedis(chave));
    }

    /** Apaga as contagens de todos os e-mails. Usado pelos testes. */
    void reiniciar() {
        try (Cursor<String> chaves = redis.scan(ScanOptions.scanOptions().match(PREFIXO + "*").build())) {
            chaves.forEachRemaining(redis::delete);
        }
    }

    /** O Redis guarda só o hash: nenhum e-mail fica à vista no servidor. */
    private static String chaveNoRedis(String chave) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(chave.getBytes(StandardCharsets.UTF_8));
            return PREFIXO + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }
}
