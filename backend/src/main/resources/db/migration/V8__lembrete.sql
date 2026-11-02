-- Lembrete por e-mail ao cliente. lembrar_em é quando ele deve sair (nulo: não há lembrete a mandar);
-- lembrete_enviado_em é quando o broker confirmou a mensagem. O job do Quartz varre o que está vencido.

ALTER TABLE agendamento
    ADD COLUMN lembrar_em          timestamptz,
    ADD COLUMN lembrete_enviado_em timestamptz;

-- Só o que ainda falta enviar: a varredura de cada minuto lê poucas linhas, por maior que seja o histórico.
CREATE INDEX ix_agendamento_lembrete ON agendamento (lembrar_em)
    WHERE status = 'CONFIRMADO' AND lembrete_enviado_em IS NULL AND lembrar_em IS NOT NULL;
