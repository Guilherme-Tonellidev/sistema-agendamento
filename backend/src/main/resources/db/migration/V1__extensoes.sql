-- btree_gist permite combinar igualdade (profissional) com sobreposição de intervalos (horário)
-- na mesma constraint de exclusão. É a base da regra "um profissional não pode ter dois
-- agendamentos ao mesmo tempo", que entra na etapa 3.
CREATE EXTENSION IF NOT EXISTS btree_gist;
