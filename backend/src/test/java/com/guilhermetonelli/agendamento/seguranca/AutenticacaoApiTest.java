package com.guilhermetonelli.agendamento.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.RelogioDeTeste;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.seguranca.dto.AcessoRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.CadastroRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.TokenResponse;
import com.guilhermetonelli.agendamento.seguranca.dto.UsuarioResponse;
import com.jayway.jsonpath.JsonPath;

class AutenticacaoApiTest extends IntegracaoTest {

    private static final String SENHA = "senha-segura-123";

    private RestClient anonima;

    @BeforeEach
    void criarClienteAnonimo() {
        anonima = apiAnonima();
    }

    @Test
    void cadastroCriaClienteEAbreSessao() {
        var resposta = cadastrar("Carlos@Gmail.com");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().tipo()).isEqualTo("Bearer");
        assertThat(resposta.getBody().expiraEmSegundos()).isEqualTo(900);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");

        UsuarioResponse eu = eu(resposta.getBody());
        assertThat(eu.email()).isEqualTo("carlos@gmail.com");
        assertThat(eu.papel()).isEqualTo(Papel.CLIENTE);
        assertThat(eu.clienteId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuario", String.class)).startsWith("{bcrypt}");
    }

    @Test
    void recusaEmailJaUsadoPorOutraConta() {
        cadastrar("carlos@gmail.com");

        var repetido = anonima.post().uri("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastroRequest("Carlos Lima", "CARLOS@gmail.com", null, SENHA))
                .retrieve().toEntity(String.class);

        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cadastroNaoRevelaSeOEmailJaEClienteDaCasa() {
        cadastrar("carlos@gmail.com");
        api.post().uri("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ClienteRequest("Bruno Lima", "bruno@gmail.com", null))
                .retrieve().toBodilessEntity();

        var comConta = cadastrarCru("carlos@gmail.com");
        var semConta = cadastrarCru("bruno@gmail.com");

        assertThat(comConta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(semConta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detalhe(semConta)).isEqualTo(detalhe(comConta)).doesNotContain("bruno");
    }

    @Test
    void loginComSenhaCorreta() {
        cadastrar("carlos@gmail.com");

        var resposta = entrar("carlos@gmail.com", SENHA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eu(resposta.getBody()).email()).isEqualTo("carlos@gmail.com");
    }

    @Test
    void loginInvalidoNaoRevelaSeOEmailExiste() {
        cadastrar("carlos@gmail.com");

        var senhaErrada = entrarCru("carlos@gmail.com", "senha-errada-123");
        var emailInexistente = entrarCru("ninguem@gmail.com", SENHA);

        assertThat(senhaErrada.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(emailInexistente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(detalhe(senhaErrada)).isEqualTo(detalhe(emailInexistente)).isEqualTo("E-mail ou senha inválidos.");
    }

    @Test
    void senhaAlemDoLimiteDoBcryptNaoDerrubaOLogin() {
        cadastrar("carlos@gmail.com");

        assertThat(entrarCru("carlos@gmail.com", "🔐".repeat(19)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshTrocaOTokenEInvalidaOAnterior() {
        String primeiro = cookie(cadastrar("carlos@gmail.com"));

        var renovado = renovar(primeiro);
        String segundo = cookie(renovado);

        assertThat(renovado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(renovar(segundo).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reusoDeRefreshTokenDerrubaAFamiliaInteira() {
        String roubado = cookie(cadastrar("carlos@gmail.com"));
        String legitimo = cookie(renovar(roubado));

        var ataque = renovar(roubado);

        assertThat(ataque.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(renovar(legitimo).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshExpira() {
        String token = cookie(cadastrar("carlos@gmail.com"));

        relogio.ajustar(RelogioDeTeste.INICIAL.plus(Duration.ofDays(15)));

        assertThat(renovar(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutEncerraASessaoELimpaOCookie() {
        String token = cookie(cadastrar("carlos@gmail.com"));

        var saida = anonima.post().uri("/api/auth/logout").header(HttpHeaders.COOKIE, "refresh_token=" + token)
                .retrieve().toBodilessEntity();

        assertThat(saida.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(saida.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(renovar(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenDeAcessoExpiraEmQuinzeMinutos() {
        TokenResponse token = cadastrar("carlos@gmail.com").getBody();

        relogio.ajustar(relogio.instant().plus(Duration.ofMinutes(15)).plusSeconds(1));

        var resposta = anonima.get().uri("/api/auth/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + token.tokenDeAcesso())
                .retrieve().toEntity(String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void tokenAdulteradoERecusado() {
        String token = cadastrar("carlos@gmail.com").getBody().tokenDeAcesso();
        String[] partes = token.split("\\.");
        String adulterado = partes[0] + "." + partes[1] + "." + new StringBuilder(partes[2]).reverse();

        var resposta = anonima.get().uri("/api/auth/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterado)
                .retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminDefineOAcessoDoProfissional() {
        UUID ana = api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest("Ana Souza", "ana@barbearia.com", null))
                .retrieve().body(ProfissionalResponse.class).id();

        var acesso = api.put().uri("/api/profissionais/{id}/acesso", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AcessoRequest("senha-inicial-123"))
                .retrieve().toBodilessEntity();
        var login = entrar("ana@barbearia.com", "senha-inicial-123");

        assertThat(acesso.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(eu(login.getBody())).satisfies(eu -> {
            assertThat(eu.papel()).isEqualTo(Papel.PROFISSIONAL);
            assertThat(eu.profissionalId()).isEqualTo(ana);
        });
    }

    @Test
    void profissionalInativadoNaoEntraNemRenovaASessao() {
        UUID ana = cadastrarProfissionalComAcesso("ana@barbearia.com", "senha-inicial-123");
        String refresh = cookie(entrar("ana@barbearia.com", "senha-inicial-123"));

        api.patch().uri("/api/profissionais/{id}/inativar", ana).retrieve().toBodilessEntity();

        var login = entrarCru("ana@barbearia.com", "senha-inicial-123");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(detalhe(login)).isEqualTo("E-mail ou senha inválidos.");
        assertThat(renovar(refresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void profissionalReativadoVoltaAEntrar() {
        UUID ana = cadastrarProfissionalComAcesso("ana@barbearia.com", "senha-inicial-123");
        api.patch().uri("/api/profissionais/{id}/inativar", ana).retrieve().toBodilessEntity();
        api.patch().uri("/api/profissionais/{id}/ativar", ana).retrieve().toBodilessEntity();

        assertThat(entrar("ana@barbearia.com", "senha-inicial-123").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trocarASenhaDoProfissionalEncerraAsSessoesAbertas() {
        UUID ana = cadastrarProfissionalComAcesso("ana@barbearia.com", "senha-inicial-123");
        String refresh = cookie(entrar("ana@barbearia.com", "senha-inicial-123"));

        var troca = api.put().uri("/api/profissionais/{id}/acesso", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AcessoRequest("senha-nova-456"))
                .retrieve().toBodilessEntity();

        assertThat(troca.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(renovar(refresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(entrarCru("ana@barbearia.com", "senha-inicial-123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(entrar("ana@barbearia.com", "senha-nova-456").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bloqueiaOLoginDepoisDeCincoTentativasMesmoComASenhaCerta() {
        cadastrar("carlos@gmail.com");
        for (int i = 0; i < 5; i++) {
            assertThat(entrarCru("carlos@gmail.com", "senha-errada-123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        var bloqueado = entrarCru("carlos@gmail.com", SENHA);

        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bloqueado.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
        assertThat(detalhe(bloqueado)).isEqualTo("Muitas tentativas de login. Tente de novo em 15 min.");
    }

    @Test
    void emailInexistenteTambemEhLimitadoComAMesmaResposta() {
        for (int i = 0; i < 5; i++) {
            assertThat(entrarCru("ninguem@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(entrarCru("ninguem@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void oLoginVoltaDepoisDeQuinzeMinutos() {
        cadastrar("carlos@gmail.com");
        for (int i = 0; i < 5; i++) {
            entrarCru("carlos@gmail.com", "senha-errada-123");
        }
        assertThat(entrarCru("carlos@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // O prazo da janela vive no Redis, que não usa o relógio de teste: a chave expira por conta própria.
        redis.expirar(redis.chaves("login:*").getFirst(), Duration.ofMillis(1));

        assertThat(entrarCru("carlos@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginBemSucedidoZeraAContagem() {
        cadastrar("carlos@gmail.com");
        for (int i = 0; i < 4; i++) {
            entrarCru("carlos@gmail.com", "senha-errada-123");
        }
        assertThat(entrarCru("carlos@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int i = 0; i < 4; i++) {
            assertThat(entrarCru("carlos@gmail.com", "senha-errada-123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(entrarCru("carlos@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bloqueioDeUmaContaNaoAtingeAsOutras() {
        cadastrar("carlos@gmail.com");
        cadastrar("bruno@gmail.com");
        for (int i = 0; i < 6; i++) {
            entrarCru("carlos@gmail.com", "senha-errada-123");
        }

        assertThat(entrarCru("carlos@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(entrarCru("bruno@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void maiusculasNoEmailNaoBurlamOLimite() {
        cadastrar("carlos@gmail.com");
        String[] variacoes = {"carlos@gmail.com", "Carlos@gmail.com", "CARLOS@gmail.com", "carlos@GMAIL.com", "CARLOS@GMAIL.COM"};
        for (String email : variacoes) {
            entrarCru(email, "senha-errada-123");
        }

        assertThat(entrarCru("carlos@gmail.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private UUID cadastrarProfissionalComAcesso(String email, String senha) {
        UUID id = api.post().uri("/api/profissionais")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfissionalRequest("Ana Souza", email, null))
                .retrieve().body(ProfissionalResponse.class).id();
        api.put().uri("/api/profissionais/{id}/acesso", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AcessoRequest(senha))
                .retrieve().toBodilessEntity();
        return id;
    }

    private ResponseEntity<TokenResponse> cadastrar(String email) {
        return anonima.post().uri("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastroRequest("Carlos Lima", email, null, SENHA))
                .retrieve().toEntity(TokenResponse.class);
    }

    private ResponseEntity<String> cadastrarCru(String email) {
        return anonima.post().uri("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastroRequest("Outra Pessoa", email, null, SENHA))
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<TokenResponse> entrar(String email, String senha) {
        return anonima.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, senha))
                .retrieve().toEntity(TokenResponse.class);
    }

    private ResponseEntity<String> entrarCru(String email, String senha) {
        return anonima.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, senha))
                .retrieve().toEntity(String.class);
    }

    /** Lido como texto: em caso de erro o corpo é um Problem Details, não um token. */
    private ResponseEntity<String> renovar(String refreshToken) {
        return anonima.post().uri("/api/auth/refresh").header(HttpHeaders.COOKIE, "refresh_token=" + refreshToken)
                .retrieve().toEntity(String.class);
    }

    private UsuarioResponse eu(TokenResponse token) {
        return anonima.get().uri("/api/auth/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + token.tokenDeAcesso())
                .retrieve().body(UsuarioResponse.class);
    }

    private static String cookie(ResponseEntity<?> resposta) {
        String setCookie = resposta.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    private static String detalhe(ResponseEntity<String> resposta) {
        return JsonPath.read(resposta.getBody(), "$.detail");
    }
}
