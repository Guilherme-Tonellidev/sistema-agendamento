import { Agendamento, Pagina, Profissional, StatusAgendamento } from '../../core/api/modelos';
import { PALETA, corDoBarbeiro, paraEvento, semanaDe, todasAsPaginas } from './eventos';

/** Luminância relativa de uma cor #rrggbb, como define o WCAG. */
function luminancia(cor: string): number {
  const [r, g, b] = [1, 3, 5]
    .map((i) => parseInt(cor.slice(i, i + 2), 16) / 255)
    .map((canal) => (canal <= 0.03928 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

const contrasteComBranco = (cor: string) => 1.05 / (luminancia(cor) + 0.05);

const equipe = (quantos: number): Profissional[] =>
  Array.from({ length: quantos }, (_, i) => ({ id: `p${i}`, nome: `Barbeiro ${i}`, ativo: true }));

function agendamento(status: StatusAgendamento = 'CONFIRMADO'): Agendamento {
  return {
    id: 'a1',
    inicio: '2026-10-21T11:00:00Z',
    fim: '2026-10-21T11:30:00Z',
    preco: 45,
    status,
    canceladoEm: null,
    motivoCancelamento: null,
    profissional: { id: 'p1', nome: 'Barbeiro 1', ativo: true },
    servico: { id: 's1', nome: 'Corte', duracaoMinutos: 30, preco: 45 },
    cliente: { id: 'c1', nome: 'Diana', telefone: null },
  };
}

describe('semanaDe', () => {
  it('vai da segunda ao domingo da semana do dia', () => {
    expect(semanaDe('2026-10-21')).toEqual({ de: '2026-10-19', ate: '2026-10-25' }); // quarta
  });

  it('o domingo fecha a semana, não abre outra', () => {
    expect(semanaDe('2026-10-25')).toEqual({ de: '2026-10-19', ate: '2026-10-25' });
  });

  it('a segunda abre a semana e atravessa a virada do mês', () => {
    expect(semanaDe('2026-10-26')).toEqual({ de: '2026-10-26', ate: '2026-11-01' });
  });
});

describe('corDoBarbeiro', () => {
  it('dá a mesma cor ao mesmo barbeiro', () => {
    expect(corDoBarbeiro(equipe(3), 'p1')).toBe(corDoBarbeiro(equipe(3), 'p1'));
  });

  it('dá uma cor diferente a cada um dos seis primeiros e recomeça no sétimo', () => {
    const time = equipe(7);
    const cores = time.map(({ id }) => corDoBarbeiro(time, id));

    expect(new Set(cores.slice(0, 6)).size).toBe(6);
    expect(cores[6]).toBe(cores[0]);
  });

  it('barbeiro fora da equipe usa a primeira cor', () => {
    expect(corDoBarbeiro(equipe(3), 'desconhecido')).toBe(PALETA[0]);
  });
});

describe('PALETA', () => {
  it('tem seis cores', () => {
    expect(PALETA).toHaveLength(6);
  });

  it('todas têm contraste AA (4,5) com o texto branco', () => {
    for (const cor of PALETA) {
      expect(contrasteComBranco(cor)).toBeGreaterThanOrEqual(4.5);
    }
  });
});

describe('paraEvento', () => {
  const time = equipe(3);

  it('mostra cliente e serviço no título e mantém o horário do agendamento', () => {
    const evento = paraEvento(agendamento(), time);

    expect(evento.title).toBe('Diana · Corte');
    expect(evento.start).toBe('2026-10-21T11:00:00Z');
    expect(evento.end).toBe('2026-10-21T11:30:00Z');
  });

  it('pinta com a cor do barbeiro e texto branco', () => {
    const evento = paraEvento(agendamento(), time);

    expect(evento.backgroundColor).toBe(corDoBarbeiro(time, 'p1'));
    expect(evento.borderColor).toBe(corDoBarbeiro(time, 'p1'));
    expect(evento.textColor).toBe('#ffffff');
  });

  it('só o confirmado se arrasta', () => {
    expect(paraEvento(agendamento('CONFIRMADO'), time).startEditable).toBe(true);
    expect(paraEvento(agendamento('CONCLUIDO'), time).startEditable).toBe(false);
    expect(paraEvento(agendamento('NAO_COMPARECEU'), time).startEditable).toBe(false);
    expect(paraEvento(agendamento('CANCELADO'), time).startEditable).toBe(false);
  });

  it('marca o status numa classe em minúsculas para o estilo', () => {
    expect(paraEvento(agendamento('NAO_COMPARECEU'), time).classNames).toContain(
      'status-nao_compareceu',
    );
    expect(paraEvento(agendamento('CONFIRMADO'), time).classNames).toContain('status-confirmado');
  });

  it('leva o agendamento junto para quem clica no bloco', () => {
    const a = agendamento();

    expect(paraEvento(a, time).extendedProps.agendamento).toBe(a);
    expect(paraEvento(a, time).id).toBe('a1');
  });
});

describe('todasAsPaginas', () => {
  const pagina = (numero: number, quantos: number, totalPages: number): Pagina<number> => ({
    content: Array.from({ length: quantos }, (_, i) => numero * 100 + i),
    page: { size: 100, number: numero, totalElements: 212, totalPages },
  });

  it('segue as páginas até a última e junta tudo', async () => {
    const buscar = vi.fn(async (numero: number) => pagina(numero, numero === 2 ? 12 : 100, 3));

    const itens = await todasAsPaginas(buscar);

    expect(buscar).toHaveBeenCalledTimes(3);
    expect(buscar).toHaveBeenNthCalledWith(1, 0);
    expect(buscar).toHaveBeenNthCalledWith(2, 1);
    expect(buscar).toHaveBeenNthCalledWith(3, 2);
    expect(itens).toHaveLength(212);
    expect(itens[100]).toBe(100); // a ordem das páginas se mantém
  });

  it('com uma página só, busca uma vez', async () => {
    const buscar = vi.fn(async (numero: number) => pagina(numero, 5, 1));

    const itens = await todasAsPaginas(buscar);

    expect(buscar).toHaveBeenCalledTimes(1);
    expect(itens).toHaveLength(5);
  });
});
