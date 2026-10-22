package com.guilhermetonelli.agendamento.seguranca;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/**
 * Hash e conferência de senhas (BCrypt). Quando o usuário não existe, compara com um hash falso para
 * a resposta levar o mesmo tempo: assim o tempo de resposta não revela quais e-mails têm conta.
 */
@Component
class Senhas {

    /** Limite do BCrypt: bytes além disso seriam ignorados, e o Spring recusa a senha. */
    private static final int BYTES_MAXIMOS = 72;

    private final PasswordEncoder encoder;
    private final String hashFalso;

    Senhas(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.hashFalso = encoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    String hash(String senha) {
        if (excedeLimite(senha)) {
            throw new RegraDeNegocioException("A senha é longa demais.");
        }
        return encoder.encode(senha);
    }

    /** {@code hash} nulo (usuário inexistente ou conta só com Google) sempre falha, no mesmo tempo. */
    boolean confere(String senha, String hash) {
        boolean confere = !excedeLimite(senha) && encoder.matches(senha, hash == null ? hashFalso : hash);
        return confere && hash != null;
    }

    boolean precisaAtualizar(String hash) {
        return encoder.upgradeEncoding(hash);
    }

    private static boolean excedeLimite(String senha) {
        return senha.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS;
    }
}
