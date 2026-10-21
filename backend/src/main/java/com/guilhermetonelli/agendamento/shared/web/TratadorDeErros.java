package com.guilhermetonelli.agendamento.shared.web;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

/**
 * Converte exceções em respostas no formato Problem Details (RFC 9457), o mesmo para toda a API.
 *
 * <p>As exceções do domínio não sabem nada de HTTP; a escolha do status acontece só aqui.
 */
@RestControllerAdvice
class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ProblemDetail conflito(ConflitoException ex) {
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
        ex.getDetalhes().forEach(problema::setProperty);
        return problema;
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    ProblemDetail regraDeNegocio(RegraDeNegocioException ex) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio violada", ex.getMessage());
    }

    /** Última barreira: o banco recusou o dado (por exemplo, duas requisições com o mesmo e-mail ao mesmo tempo). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade no banco", ex);
        return problema(HttpStatus.CONFLICT, "Conflito", "Os dados conflitam com um registro já existente.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail edicaoConcorrente(ObjectOptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "Edição concorrente",
                "O registro foi alterado por outra requisição. Recarregue os dados e tente de novo.");
    }

    /** Ordenação por um campo que não existe, como {@code ?sort=senha}. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail ordenacaoInvalida(PropertyReferenceException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
                "Não é possível ordenar pelo campo '" + ex.getPropertyName() + "'.");
    }

    /** Dependendo da versão do Spring Data, a ordenação inválida chega embrulhada nesta exceção. */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ProblemDetail usoInvalidoDoBanco(InvalidDataAccessApiUsageException ex) {
        if (ex.getCause() instanceof PropertyReferenceException propriedade) {
            return ordenacaoInvalida(propriedade);
        }
        return erroInesperado(ex);
    }

    /** Qualquer erro não previsto vira 500 com uma mensagem genérica; os detalhes ficam só no log. */
    @ExceptionHandler(Exception.class)
    ProblemDetail erroInesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente em instantes.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<ErroDeCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroDeCampo(erro.getField(), erro.getDefaultMessage()))
                .sorted(Comparator.comparing(ErroDeCampo::campo))
                .toList();

        ProblemDetail corpo = ex.getBody();
        corpo.setTitle("Dados inválidos");
        corpo.setDetail("Um ou mais campos estão inválidos.");
        corpo.setProperty("erros", erros);
        return handleExceptionInternal(ex, corpo, headers, status, request);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
