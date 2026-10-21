-- Reservas. As constraints de exclusão garantem no próprio banco que nem o profissional nem o cliente
-- tenham dois agendamentos ativos ao mesmo tempo, mesmo com requisições simultâneas.

CREATE TABLE agendamento (
    id                  uuid           PRIMARY KEY,
    profissional_id     uuid           NOT NULL REFERENCES profissional (id),
    servico_id          uuid           NOT NULL REFERENCES servico (id),
    cliente_id          uuid           NOT NULL REFERENCES cliente (id),
    inicio              timestamptz    NOT NULL,
    fim                 timestamptz    NOT NULL,
    -- Preço do serviço no momento da reserva; reajustes posteriores não alteram o que foi combinado.
    preco               numeric(10, 2) NOT NULL,
    status              varchar(20)    NOT NULL,
    cancelado_em        timestamptz,
    motivo_cancelamento varchar(200),
    versao              bigint         NOT NULL,
    criado_em           timestamptz    NOT NULL,
    atualizado_em       timestamptz    NOT NULL,
    CONSTRAINT ck_agendamento_periodo CHECK (inicio < fim),
    CONSTRAINT ck_agendamento_preco CHECK (preco >= 0),
    CONSTRAINT ck_agendamento_status CHECK (status IN ('CONFIRMADO', 'CANCELADO', 'CONCLUIDO', 'NAO_COMPARECEU')),
    CONSTRAINT ck_agendamento_cancelamento CHECK ((status = 'CANCELADO') = (cancelado_em IS NOT NULL)),
    CONSTRAINT ex_agendamento_profissional EXCLUDE USING gist (
        profissional_id WITH =, tstzrange(inicio, fim, '[)') WITH &&) WHERE (status <> 'CANCELADO'),
    CONSTRAINT ex_agendamento_cliente EXCLUDE USING gist (
        cliente_id WITH =, tstzrange(inicio, fim, '[)') WITH &&) WHERE (status <> 'CANCELADO')
);

-- Os índices das constraints só cobrem agendamentos ativos; estes cobrem as listagens com histórico.
CREATE INDEX ix_agendamento_profissional ON agendamento (profissional_id, inicio);
CREATE INDEX ix_agendamento_cliente ON agendamento (cliente_id, inicio);
CREATE INDEX ix_agendamento_inicio ON agendamento (inicio);
CREATE INDEX ix_agendamento_servico ON agendamento (servico_id);
