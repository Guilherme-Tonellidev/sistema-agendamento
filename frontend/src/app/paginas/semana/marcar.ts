import { HorarioLivre } from '../../core/api/modelos';

/**
 * O horário a pré-selecionar para o clique na grade: o que começa no instante clicado; senão, o
 * primeiro depois dele; se o clique foi depois de todos, o primeiro do dia.
 */
export function escolherHorario(
  livres: readonly HorarioLivre[],
  desejado: Date,
): HorarioLivre | null {
  const pedido = desejado.getTime();
  return (
    livres.find(({ inicio }) => Date.parse(inicio) === pedido) ??
    livres.find(({ inicio }) => Date.parse(inicio) > pedido) ??
    livres[0] ??
    null
  );
}
