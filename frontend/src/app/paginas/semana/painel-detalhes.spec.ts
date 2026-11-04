import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Agendamento } from '../../core/api/modelos';
import { PainelDetalhes } from './painel-detalhes';

/** Terça, 10 de novembro, 10:00 em São Paulo: ainda por vir. */
const MARCADO: Agendamento = {
  id: 'a1',
  inicio: '2026-11-10T13:00:00Z',
  fim: '2026-11-10T13:30:00Z',
  preco: 50,
  status: 'CONFIRMADO',
  canceladoEm: null,
  motivoCancelamento: null,
  profissional: { id: 'p1', nome: 'Rafael', ativo: true },
  servico: { id: 's1', nome: 'Corte', duracaoMinutos: 30, preco: 50 },
  cliente: { id: 'c1', nome: 'Carlos Mendes', telefone: null },
};
const HISTORICO_URL = '/api/agendamentos/a1/historico';
const LIVRES_URL =
  '/api/profissionais/p1/horarios-livres/encaixe?servicoId=s1&data=2026-11-10&semLimite=true';
const LIVRES = [
  { inicio: '2026-11-10T14:00:00Z', fim: '2026-11-10T14:30:00Z' },
  { inicio: '2026-11-10T14:30:00Z', fim: '2026-11-10T15:00:00Z' },
];

describe('PainelDetalhes', () => {
  let fixture: ComponentFixture<PainelDetalhes>;
  let servidor: HttpTestingController;
  let mudancas: number;

  const tela = () => fixture.nativeElement as HTMLElement;
  /** Como no FormularioMarcar: sem whenStable, porque quem responde as requisições é o teste. */
  const assentar = async () => {
    await new Promise<void>((pronto) => setTimeout(pronto));
    TestBed.tick();
  };
  const botao = (texto: string) =>
    [...tela().querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(texto));

  async function abrir(agendamento: Agendamento): Promise<void> {
    fixture.componentRef.setInput('agendamento', agendamento);
    await assentar();
  }

  /** Abre o remarcar com a lista do dia do agendamento e escolhe o segundo horário. */
  async function escolherOutroHorario(): Promise<void> {
    await abrir(MARCADO);
    servidor.expectOne(HISTORICO_URL).flush([]);
    botao('Remarcar')!.click();
    await assentar();
    servidor.expectOne(LIVRES_URL).flush(LIVRES);
    await assentar();
    const lista = tela().querySelector<HTMLSelectElement>('#horario-remarcar')!;
    lista.value = '2026-11-10T14:30:00Z';
    lista.dispatchEvent(new Event('change'));
    await assentar();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PainelDetalhes],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servidor = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PainelDetalhes);
    mudancas = 0;
    fixture.componentInstance.mudou.subscribe(() => mudancas++);
  });

  afterEach(() => servidor.verify());

  it('remarca pelo botão para o horário escolhido, com a lista sem limite de tempo', async () => {
    await escolherOutroHorario();

    botao('Confirmar')!.click();
    await assentar();
    const envio = servidor.expectOne({ method: 'PATCH', url: '/api/agendamentos/a1/reagendar' });
    expect(envio.request.body).toEqual({ inicio: '2026-11-10T14:30:00Z' });
    envio.flush({ ...MARCADO, inicio: '2026-11-10T14:30:00Z' });
    await assentar();

    expect(mudancas).toBe(1);
  });

  it('se o servidor recusa (409), mostra o motivo e o formulário continua aberto', async () => {
    await escolherOutroHorario();

    botao('Confirmar')!.click();
    await assentar();
    servidor
      .expectOne({ method: 'PATCH', url: '/api/agendamentos/a1/reagendar' })
      .flush(
        { detail: 'Esse horário acabou de ser reservado.' },
        { status: 409, statusText: 'Conflict' },
      );
    await assentar();
    // A lista de horários se atualiza depois da recusa.
    servidor.expectOne(LIVRES_URL).flush(LIVRES);
    await assentar();

    expect(tela().querySelector('[role="alert"]')?.textContent).toContain(
      'Esse horário acabou de ser reservado.',
    );
    expect(tela().querySelector('#horario-remarcar')).not.toBeNull();
    expect(mudancas).toBe(0);
  });

  it('agendamento concluído não tem o botão "Remarcar"', async () => {
    await abrir({ ...MARCADO, status: 'CONCLUIDO' });
    servidor.expectOne(HISTORICO_URL).flush([]);
    await assentar();

    expect(botao('Remarcar')).toBeUndefined();
  });

  it('se o histórico não carrega, avisa só nele e o resto do painel segue', async () => {
    await abrir(MARCADO);
    servidor.expectOne(HISTORICO_URL).flush(null, { status: 500, statusText: 'Erro' });
    await assentar();

    expect(tela().querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar o histórico.',
    );
    expect(botao('Remarcar')).toBeDefined();
  });

  it('mostra cada evento do histórico com a hora e a frase', async () => {
    await abrir(MARCADO);
    servidor.expectOne(HISTORICO_URL).flush([
      {
        quando: '2026-11-04T15:00:00Z',
        autor: { nome: 'Ana', papel: 'ADMIN' },
        tipo: 'CRIADO',
        de: null,
        para: null,
        motivo: null,
      },
    ]);
    await assentar();

    const itens = [...tela().querySelectorAll('app-linha-do-tempo li')];
    expect(itens.map((li) => li.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      '04/11 12:00 Criado por Ana',
    ]);
  });
});
