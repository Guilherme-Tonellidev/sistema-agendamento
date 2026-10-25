package com.guilhermetonelli.agendamento.notificacao.email;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

final class EnviadorSmtp implements EnviadorDeEmail {

    private final JavaMailSender sender;
    private final String remetente;

    EnviadorSmtp(JavaMailSender sender, String remetente) {
        this.sender = sender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(Email email) {
        sender.send(mensagem -> {
            MimeMessageHelper ajuda = new MimeMessageHelper(mensagem, true, "UTF-8");
            ajuda.setFrom(remetente);
            ajuda.setTo(email.para());
            ajuda.setSubject(email.assunto());
            ajuda.setText(email.texto(), email.html());
        });
    }
}
