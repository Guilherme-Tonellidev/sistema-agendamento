import { EventoDoHistorico } from '../../core/api/modelos';
import { fraseDoEvento } from './frase-do-evento';

const ANA = { nome: 'Ana', papel: 'ADMIN' as const };
const SISTEMA = { nome: 'Sistema', papel: null };

function evento(mudanca: Partial<EventoDoHistorico>): EventoDoHistorico {
  return {
    quando: '2026-10-20T15:00:00Z',
    autor: ANA,
    tipo: 'CRIADO',
    de: null,
    para: null,
    motivo: null,
    ...mudanca,
  };
}

describe('fraseDoEvento', () => {
  it('diz quem criou', () => {
    expect(fraseDoEvento(evento({ tipo: 'CRIADO' }))).toBe('Criado por Ana');
  });

  it('remarcado mostra de onde para onde, no fuso da barbearia', () => {
    const remarcado = evento({
      tipo: 'REMARCADO',
      de: '2026-10-21T11:00:00Z',
      para: '2026-10-21T11:30:00Z',
    });

    expect(fraseDoEvento(remarcado)).toBe('Remarcado de 21/10 08:00 para 21/10 08:30 por Ana');
  });

  it('cancelado mostra o motivo quando houver', () => {
    expect(fraseDoEvento(evento({ tipo: 'CANCELADO', motivo: 'Imprevisto' }))).toBe(
      'Cancelado por Ana: Imprevisto',
    );
    expect(fraseDoEvento(evento({ tipo: 'CANCELADO' }))).toBe('Cancelado por Ana');
  });

  it('diz quem concluiu e quem registrou a falta', () => {
    expect(fraseDoEvento(evento({ tipo: 'CONCLUIDO' }))).toBe('Concluído por Ana');
    expect(fraseDoEvento(evento({ tipo: 'NAO_COMPARECEU' }))).toBe('Falta registrada por Ana');
  });

  it('tipo novo que a tela ainda não conhece aparece pelo nome', () => {
    const futuro = evento({ tipo: 'REABERTO' as EventoDoHistorico['tipo'] });

    expect(fraseDoEvento(futuro)).toBe('REABERTO por Ana');
  });

  it('sem autor, foi o sistema', () => {
    expect(fraseDoEvento(evento({ autor: SISTEMA }))).toBe('Criado pelo sistema');
    expect(fraseDoEvento(evento({ autor: SISTEMA, tipo: 'CANCELADO', motivo: 'Imprevisto' }))).toBe(
      'Cancelado pelo sistema: Imprevisto',
    );
  });
});
