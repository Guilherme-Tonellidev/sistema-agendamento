package com.guilhermetonelli.agendamento.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.cliente.ClienteService;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.seguranca.dto.CadastroRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.UsuarioResponse;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.NaoAutenticadoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
public class AutenticacaoService {

    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";
    private static final String SESSAO_INVALIDA = "Sessão expirada ou inválida. Faça login novamente.";

    private final SecureRandom aleatorio = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final Senhas senhas;
    private final EmissorDeTokens emissor;
    private final ClienteService clienteService;
    private final ProfissionalService profissionalService;
    private final SegurancaProperties properties;
    private final Clock clock;

    AutenticacaoService(
            UsuarioRepository usuarios,
            SessaoRepository sessoes,
            Senhas senhas,
            EmissorDeTokens emissor,
            ClienteService clienteService,
            ProfissionalService profissionalService,
            SegurancaProperties properties,
            Clock clock) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.senhas = senhas;
        this.emissor = emissor;
        this.clienteService = clienteService;
        this.profissionalService = profissionalService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Autocadastro de cliente, que já sai com a sessão aberta. */
    @Transactional
    SessaoAberta cadastrar(CadastroRequest request) {
        return abrirSessao(criarContaDeCliente(request), UUID.randomUUID());
    }

    /** Cria o cadastro e a conta do cliente. O e-mail não pode pertencer a outra conta nem a outro cadastro. */
    @Transactional
    public Usuario criarContaDeCliente(CadastroRequest request) {
        String email = Textos.normalizarEmail(request.email());
        garantirEmailLivre(email);
        String senhaHash = senhas.hash(request.senha());
        UUID clienteId = clienteService.cadastrar(new ClienteRequest(request.nome(), email, request.telefone())).id();
        return usuarios.save(Usuario.cliente(email, senhaHash, clienteId));
    }

    @Transactional
    SessaoAberta entrar(LoginRequest request) {
        Usuario usuario = usuarios.findByEmail(Textos.normalizarEmail(request.email())).orElse(null);
        String hash = usuario == null ? null : usuario.getSenhaHash();
        if (!senhas.confere(request.senha(), hash) || !usuario.isAtivo()) {
            throw new NaoAutenticadoException(CREDENCIAIS_INVALIDAS);
        }
        if (senhas.precisaAtualizar(hash)) {
            usuario.definirSenha(senhas.hash(request.senha()));
        }
        return abrirSessao(usuario, UUID.randomUUID());
    }

    /**
     * Troca o refresh token por um novo (rotação). Um token já trocado sendo usado de novo significa que
     * alguém o copiou: a família inteira é revogada, derrubando também quem estava com o token legítimo.
     */
    @Transactional(noRollbackFor = NaoAutenticadoException.class)
    SessaoAberta renovar(String refreshToken) {
        Instant agora = Instant.now(clock);
        Sessao sessao = sessoes.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new NaoAutenticadoException(SESSAO_INVALIDA));

        if (sessao.reutilizada()) {
            sessoes.revogarFamilia(sessao.getFamilia(), agora);
            throw new NaoAutenticadoException(SESSAO_INVALIDA);
        }
        if (!sessao.valida(agora) || !sessao.getUsuario().isAtivo()) {
            throw new NaoAutenticadoException(SESSAO_INVALIDA);
        }
        sessao.marcarUsada(agora);
        return abrirSessao(sessao.getUsuario(), sessao.getFamilia());
    }

    /** Encerra a sessão do dispositivo. Token desconhecido não é erro: o resultado é o mesmo. */
    @Transactional
    void sair(String refreshToken) {
        sessoes.findByTokenHash(hash(refreshToken))
                .ifPresent(sessao -> sessoes.revogarFamilia(sessao.getFamilia(), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    UsuarioResponse eu(UsuarioLogado eu) {
        Usuario usuario = usuarios.findById(eu.id()).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", eu.id()));
        return new UsuarioResponse(
                usuario.getId(), usuario.getEmail(), usuario.getPapel(), usuario.getProfissionalId(), usuario.getClienteId());
    }

    /** Cria ou troca a senha de acesso de um profissional; o login é o e-mail do cadastro dele. */
    @Transactional
    public void definirAcessoDeProfissional(UUID profissionalId, String senha) {
        String senhaHash = senhas.hash(senha);
        usuarios.findByProfissionalId(profissionalId).ifPresentOrElse(
                usuario -> usuario.definirSenha(senhaHash),
                () -> {
                    String email = profissionalService.obterComServicos(profissionalId).getEmail();
                    garantirEmailLivre(email);
                    usuarios.save(Usuario.profissional(email, senhaHash, profissionalId));
                });
    }

    @Transactional
    void criarAdminSeNaoExistir(String email, String senha) {
        String normalizado = Textos.normalizarEmail(email);
        if (!usuarios.existsByEmail(normalizado)) {
            usuarios.save(Usuario.admin(normalizado, senhas.hash(senha)));
        }
    }

    private void garantirEmailLivre(String email) {
        if (usuarios.existsByEmail(email)) {
            throw new ConflitoException("Já existe uma conta com este e-mail.");
        }
    }

    private SessaoAberta abrirSessao(Usuario usuario, UUID familia) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant agora = Instant.now(clock);
        sessoes.save(new Sessao(usuario, familia, hash(refreshToken), agora, agora.plus(properties.validadeDaSessao())));
        return new SessaoAberta(emissor.emitir(usuario.comoLogado()), refreshToken);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }
}
