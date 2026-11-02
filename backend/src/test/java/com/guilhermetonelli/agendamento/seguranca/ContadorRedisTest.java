package com.guilhermetonelli.agendamento.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ContadorRedisTest {

    /** Última contagem da janela: o Redis acima do máximo, com menos de 1 ms para o prazo acabar. */
    @Test
    void bloqueadoNuncaDevolveZero() {
        var redis = new StringRedisTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(RedisScript<T> script, List<String> chaves, Object... argumentos) {
                return (T) List.of((long) LimiteDeTentativas.MAXIMO + 1, 0L);
            }
        };

        assertThat(new ContadorRedis(redis).registrar("qualquer")).isPositive();
    }

    @Test
    void dentroDoLimitePassaComZero() {
        var redis = new StringRedisTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(RedisScript<T> script, List<String> chaves, Object... argumentos) {
                return (T) List.of((long) LimiteDeTentativas.MAXIMO, 900_000L);
            }
        };

        assertThat(new ContadorRedis(redis).registrar("qualquer")).isEqualTo(Duration.ZERO);
    }
}
