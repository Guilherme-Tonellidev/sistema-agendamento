import { EventoDoHistorico, TipoDeEvento } from '../../core/api/modelos';
import { diaEHora } from '../../core/datas';

const ACAO: Record<TipoDeEvento, string> = {
  CRIADO: 'Criado',
  REMARCADO: 'Remarcado',
  CANCELADO: 'Cancelado',
  CONCLUIDO: 'Concluído',
  NAO_COMPARECEU: 'Falta registrada',
};

/**
 * Ex.: "Remarcado de 21/10 08:00 para 21/10 08:30 por Ana". Fica fora do componente para a
 * spec rodar sem Angular.
 */
export function fraseDoEvento({ tipo, autor, de, para, motivo }: EventoDoHistorico): string {
  const mudanca =
    tipo === 'REMARCADO' && de && para ? ` de ${diaEHora(de)} para ${diaEHora(para)}` : '';
  // Sem papel é o próprio sistema (nome "Sistema").
  const quem = autor.papel === null ? 'pelo sistema' : `por ${autor.nome}`;
  const porque = tipo === 'CANCELADO' && motivo ? `: ${motivo}` : '';
  // Tipo que a API passar a mandar antes da tela conhecê-lo sai pelo nome, não como "undefined".
  return `${ACAO[tipo] ?? tipo}${mudanca} ${quem}${porque}`;
}
