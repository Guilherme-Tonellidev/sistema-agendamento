import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import {
  Agendamento,
  ClienteCadastrado,
  Pagina,
  Profissional,
  ProfissionalComServicos,
} from '../../core/api/modelos';
import { FormularioMarcar } from './formulario-marcar';

const pagina = <T>(content: T[]): Pagina<T> => ({
  content,
  page: { size: content.length, number: 0, totalElements: content.length, totalPages: 1 },
});

const RAFAEL: Profissional = { id: 'p1', nome: 'Rafael', ativo: true };
const CORTE = { id: 's1', nome: 'Corte', duracaoMinutos: 30, preco: 50 };
const DETALHE: ProfissionalComServicos = { ...RAFAEL, servicos: [CORTE] };
const CARLOS: ClienteCadastrado = {
  id: 'c1',
  nome: 'Carlos Mendes',
  email: 'carlos@email.com',
  telefone: null,
};
/** Terça, 10 de novembro, 10:00 em São Paulo. */
const CLIQUE = new Date('2026-11-10T13:00:00Z');
const LIVRES_URL =
  '/api/profissionais/p1/horarios-livres/encaixe?servicoId=s1&data=2026-11-10&semLimite=true';
const MARCADO = { id: 'a1', inicio: CLIQUE.toISOString() } as Agendamento;

describe('FormularioMarcar', () => {
  let fixture: ComponentFixture<FormularioMarcar>;
  let servidor: HttpTestingController;
  let marcados: Agendamento[];

  const tela = () => fixture.nativeElement as HTMLElement;
  /**
   * Deixa as promessas terminarem e atualiza a tela. Sem whenStable: ele espera as requisições
   * pendentes, e aqui quem as responde é o próprio teste.
   */
  const assentar = async () => {
    await new Promise<void>((pronto) => setTimeout(pronto));
    TestBed.tick();
  };
  const botao = (texto: string) => {
    const achado = [...tela().querySelectorAll('button')].find((b) =>
      b.textContent?.trim().startsWith(texto),
    );
    if (!achado) {
      throw new Error(`Sem botão "${texto}"`);
    }
    return achado;
  };
  const digitar = (seletor: string, texto: string) => {
    const campo = tela().querySelector<HTMLInputElement>(seletor)!;
    campo.value = texto;
    campo.dispatchEvent(new Event('input'));
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [FormularioMarcar],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servidor = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FormularioMarcar);
    fixture.componentRef.setInput('inicio', CLIQUE);
    marcados = [];
    fixture.componentInstance.marcado.subscribe((agendamento) => marcados.push(agendamento));
    await assentar();
  });

  afterEach(() => servidor.verify());

  /** Barbeiro e serviço escolhidos; a busca dos horários fica pendente. */
  async function pedirHorarios(): Promise<void> {
    servidor.expectOne('/api/profissionais?size=50').flush(pagina([RAFAEL]));
    await assentar();
    botao('Rafael').click();
    await assentar();
    servidor.expectOne('/api/profissionais/p1').flush(DETALHE);
    await assentar();
    botao('Corte').click();
    await assentar();
  }

  /** Barbeiro e serviço escolhidos; o horário do clique vem pré-selecionado. */
  async function escolherBarbeiroEServico(): Promise<void> {
    await pedirHorarios();
    servidor.expectOne(LIVRES_URL).flush([
      { inicio: '2026-11-10T12:30:00Z', fim: '2026-11-10T13:00:00Z' },
      { inicio: '2026-11-10T13:00:00Z', fim: '2026-11-10T13:30:00Z' },
    ]);
    await assentar();
  }

  async function escolherCarlos(): Promise<void> {
    digitar('#busca-marcar', 'car');
    tela()
      .querySelector('#busca-marcar')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await assentar();
    servidor.expectOne('/api/clientes?nome=car&size=8').flush(pagina([CARLOS]));
    await assentar();
    botao('Carlos Mendes').click();
    await assentar();
  }

  it('busca os horários livres do dia clicado, sem limite de tempo', async () => {
    await escolherBarbeiroEServico();

    expect(botao('10:00').getAttribute('aria-checked')).toBe('true');
  });

  it('se a busca de horários falha, avisa em vez de dizer que não há horário livre', async () => {
    await pedirHorarios();
    servidor.expectOne(LIVRES_URL).flush(null, { status: 500, statusText: 'Erro' });
    await assentar();

    // Sem corpo do servidor, vale o texto padrão do mensagemDeErro.
    expect(tela().querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar os horários.',
    );
    expect(tela().textContent).not.toContain('Nenhum horário livre');
  });

  it('se a equipe não carrega, avisa em vez de mostrar a lista vazia', async () => {
    servidor
      .expectOne('/api/profissionais?size=50')
      .flush(null, { status: 500, statusText: 'Erro' });
    await assentar();

    expect(tela().querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar os barbeiros.',
    );
  });

  it('com cliente cadastrado, reserva para ele no horário clicado', async () => {
    await escolherBarbeiroEServico();
    await escolherCarlos();

    botao('Marcar às 10:00').click();
    await assentar();
    const envio = servidor.expectOne({ method: 'POST', url: '/api/agendamentos' });
    expect(envio.request.body).toEqual({
      profissionalId: 'p1',
      servicoId: 's1',
      clienteId: 'c1',
      inicio: '2026-11-10T13:00:00Z',
    });
    envio.flush(MARCADO);
    await assentar();

    expect(marcados).toEqual([MARCADO]);
  });

  it('com cliente novo, encaixa com nome e telefone só em dígitos', async () => {
    await escolherBarbeiroEServico();
    botao('Cliente novo').click();
    await assentar();
    digitar('#nome-marcar', 'Júlia Prado');
    digitar('#telefone-marcar', '(14) 99812-3344');
    await assentar();

    botao('Marcar às 10:00').click();
    await assentar();
    const envio = servidor.expectOne({ method: 'POST', url: '/api/agendamentos/encaixe' });
    expect(envio.request.body).toEqual({
      profissionalId: 'p1',
      servicoId: 's1',
      inicio: '2026-11-10T13:00:00Z',
      nomeDoCliente: 'Júlia Prado',
      telefone: '14998123344',
    });
    envio.flush(MARCADO);
    await assentar();

    expect(marcados).toEqual([MARCADO]);
  });

  it('se o servidor recusa (409), mostra o motivo e não marca', async () => {
    await escolherBarbeiroEServico();
    await escolherCarlos();

    botao('Marcar às 10:00').click();
    await assentar();
    servidor
      .expectOne({ method: 'POST', url: '/api/agendamentos' })
      .flush(
        { detail: 'Esse horário acabou de ser reservado.' },
        { status: 409, statusText: 'Conflict' },
      );
    await assentar();
    // A lista de horários se atualiza depois da recusa.
    servidor.expectOne(LIVRES_URL).flush([]);
    await assentar();

    expect(tela().querySelector('[role="alert"]')?.textContent).toContain(
      'Esse horário acabou de ser reservado.',
    );
    expect(marcados).toEqual([]);
  });
});
