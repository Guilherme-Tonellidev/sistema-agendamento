-- Quem chega sem reserva é encaixado pelo profissional só com o nome (e, se houver, o telefone):
-- esse cliente não tem e-mail nem conta de acesso. Para quem tem e-mail, ele continua único.
ALTER TABLE cliente ALTER COLUMN email DROP NOT NULL;
