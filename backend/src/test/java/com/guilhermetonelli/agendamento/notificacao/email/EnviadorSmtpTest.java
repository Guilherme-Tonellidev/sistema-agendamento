package com.guilhermetonelli.agendamento.notificacao.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/** Monta a mensagem MIME de verdade e a captura no lugar de abrir a conexão SMTP. */
class EnviadorSmtpTest {

    private final List<MimeMessage> capturadas = new ArrayList<>();
    private final JavaMailSenderImpl sender = new JavaMailSenderImpl() {
        @Override
        protected void doSend(MimeMessage[] mensagens, Object[] originais) {
            capturadas.addAll(List.of(mensagens));
        }
    };

    @Test
    void enviaTextoEHtmlNoMesmoEmailComRemetenteDestinoEAssunto() throws Exception {
        new EnviadorSmtp(sender, "Barbearia Tonelli <nao-responder@exemplo.com>")
                .enviar(new Email("carlos@exemplo.com", "Horário confirmado: Corte, sáb 24/10 às 14h30", "Olá, Carlos!", "<p>Olá, <strong>Carlos</strong>!</p>"));

        MimeMessage mensagem = capturadas.getFirst();
        mensagem.saveChanges(); // o Transport faz isto antes de enviar; é quando os cabeçalhos de tipo são preenchidos
        assertThat(mensagem.getFrom()[0]).hasToString("Barbearia Tonelli <nao-responder@exemplo.com>");
        assertThat(mensagem.getRecipients(Message.RecipientType.TO)[0]).hasToString("carlos@exemplo.com");
        assertThat(mensagem.getSubject()).isEqualTo("Horário confirmado: Corte, sáb 24/10 às 14h30");
        List<String> partes = new ArrayList<>();
        coletar(mensagem, partes);
        assertThat(partes).containsExactlyInAnyOrder("text/plain: Olá, Carlos!", "text/html: <p>Olá, <strong>Carlos</strong>!</p>");
    }

    private static void coletar(Part parte, List<String> saida) throws Exception {
        if (parte.getContent() instanceof MimeMultipart varias) {
            for (int i = 0; i < varias.getCount(); i++) {
                coletar(varias.getBodyPart(i), saida);
            }
        } else {
            saida.add(parte.getContentType().split(";")[0] + ": " + parte.getContent());
        }
    }
}
