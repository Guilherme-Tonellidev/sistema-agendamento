package com.guilhermetonelli.agendamento;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** Apoio dos testes ao Redis: limpar entre testes, ler e escrever chaves e simular o servidor travado. */
public class RedisDeTeste {

    private final StringRedisTemplate redis;
    private final GenericContainer<?> servidor;

    RedisDeTeste(StringRedisTemplate redis, @Qualifier("redisContainer") GenericContainer<?> servidor) {
        this.redis = redis;
        this.servidor = servidor;
    }

    /** Apaga todas as chaves. */
    public void limpar() {
        redis.execute((RedisCallback<Void>) conexao -> {
            conexao.serverCommands().flushAll();
            return null;
        });
    }

    /** Chaves que casam com o padrão ({@code *} vale para qualquer trecho), em ordem alfabética. */
    public List<String> chaves(String padrao) {
        return redis.keys(padrao).stream().sorted().toList();
    }

    public String valor(String chave) {
        return redis.opsForValue().get(chave);
    }

    /** Grava o valor sem prazo de validade. */
    public void definir(String chave, String valor) {
        redis.opsForValue().set(chave, valor);
    }

    public void apagar(String chave) {
        redis.delete(chave);
    }

    /** Milissegundos que faltam para a chave expirar: -1 sem prazo, -2 se a chave não existe. */
    public Long pttl(String chave) {
        return redis.getExpire(chave, TimeUnit.MILLISECONDS);
    }

    public void expirar(String chave, Duration prazo) {
        redis.expire(chave, prazo);
    }

    /** Congela o contêiner do Redis durante a ação, como um servidor que aceita a conexão e para de responder. */
    public void comRedisPausado(Runnable acao) {
        var docker = servidor.getDockerClient();
        docker.pauseContainerCmd(servidor.getContainerId()).exec();
        try {
            acao.run();
        } finally {
            docker.unpauseContainerCmd(servidor.getContainerId()).exec();
        }
    }
}
