package com.guilhermetonelli.agendamento.seguranca;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.TentativasDemaisException;
import com.guilhermetonelli.agendamento.shared.redis.FalhasDoRedis;

/**
 * Limita as tentativas de login: no máximo {@value #MAXIMO} por janela de 15 minutos para cada par
 * e-mail + endereço de origem. Sem isso, a senha de qualquer conta podia ser adivinhada a tiros.
 *
 * <p>A tentativa é contada <em>antes</em> de a senha ser conferida, e de forma atômica: requisições em
 * paralelo não passam todas pela checagem antes de alguma ser registrada. Um login bem-sucedido zera a contagem.
 *
 * <p>A contagem fica no Redis, então vale entre as instâncias da API e sobrevive a reinícios. Com o Redis fora do
 * ar, o login segue e cada instância conta por conta própria na memória: o limite continua valendo, só que por
 * instância. Atrás de um proxy reverso o endereço de origem é o do proxy, a não ser que o servidor esteja
 * configurado para confiar nos cabeçalhos {@code X-Forwarded-For} dele.
 */
@Component
public class LimiteDeTentativas {

    static final int MAXIMO = 5;
    static final Duration JANELA = Duration.ofMinutes(15);

    private static final String OPERACAO = "limite de login";

    private final ContadorRedis redis;
    private final ContadorEmMemoria memoria;
    private final FalhasDoRedis falhas;

    LimiteDeTentativas(ContadorRedis redis, ContadorEmMemoria memoria, FalhasDoRedis falhas) {
        this.redis = redis;
        this.memoria = memoria;
        this.falhas = falhas;
    }

    /** Um IPv6 vale pelo seu bloco /64 (veja {@link Origem}), para trocar de endereço não zerar a contagem. */
    static String chave(String email, String origem) {
        return Textos.normalizarEmail(email) + "|" + Origem.agrupar(origem);
    }

    /** Conta mais uma tentativa ou, se o limite da janela já foi alcançado, lança {@link TentativasDemaisException}. */
    void registrarTentativa(String chave) {
        if (!falhas.podeTentar()) {
            memoria.registrarTentativa(chave);
            return;
        }
        Duration falta;
        try {
            falta = redis.registrar(chave);
            falhas.respondeu();
        } catch (RuntimeException e) {
            falhas.registrar(OPERACAO, e);
            memoria.registrarTentativa(chave);
            return;
        }
        if (!falta.isZero()) {
            throw bloqueio(falta);
        }
    }

    /** Login bem-sucedido: recomeça a contagem desta chave. */
    void limpar(String chave) {
        memoria.limpar(chave);
        if (!falhas.podeTentar()) {
            return;
        }
        try {
            redis.limpar(chave);
            falhas.respondeu();
        } catch (RuntimeException e) {
            falhas.registrar(OPERACAO, e);
        }
    }

    /** Esquece tudo, inclusive as falhas do Redis. Usado pelos testes, que compartilham a mesma instância da aplicação. */
    public void reiniciar() {
        memoria.reiniciar();
        redis.reiniciar();
        falhas.respondeu();
    }

    static TentativasDemaisException bloqueio(Duration falta) {
        long minutos = Math.max(1, (falta.toSeconds() + 59) / 60);
        return new TentativasDemaisException(
                "Muitas tentativas de login. Tente de novo em " + Textos.duracao(Duration.ofMinutes(minutos)) + ".", falta);
    }
}
