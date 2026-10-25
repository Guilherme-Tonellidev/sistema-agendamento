-- Sino do barbeiro: uma linha por aviso, com a frase já pronta no horário da barbearia.
-- O texto cabe nome do cliente (120) + serviço (100) + motivo (200) e as palavras fixas.

CREATE TABLE notificacao (
    id              uuid         PRIMARY KEY,
    profissional_id uuid         NOT NULL REFERENCES profissional (id),
    tipo            varchar(20)  NOT NULL,
    texto           varchar(600) NOT NULL,
    -- Horário da reserva, para abrir a Agenda no dia certo.
    inicio          timestamptz  NOT NULL,
    -- Nulo enquanto não lida.
    lida_em         timestamptz,
    versao          bigint       NOT NULL,
    criado_em       timestamptz  NOT NULL,
    atualizado_em   timestamptz  NOT NULL,
    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN ('CRIADO', 'REMARCADO', 'CANCELADO'))
);

-- Serve a lista das mais recentes de cada barbeiro.
CREATE INDEX ix_notificacao_profissional ON notificacao (profissional_id, criado_em DESC);
