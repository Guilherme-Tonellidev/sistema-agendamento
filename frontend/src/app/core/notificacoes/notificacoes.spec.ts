import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Notificacao, NotificacoesResposta, Usuario } from '../api/modelos';
import { Sessao } from '../autenticacao/sessao';
import { Notificacoes, descricaoDoSino, novasNotificacoes, rotuloDoContador } from './notificacoes';

function notificacao(id: string, lida = false): Notificacao {
  return {
    id,
    tipo: 'CRIADO',
    texto: `Carlos Mendes reservou Corte (${id}).`,
    inicio: '2026-10-24T17:30:00Z',
    criadaEm: '2026-10-22T13:00:00Z',
    lida,
  };
}

const [a, b, c] = ['a', 'b', 'c'].map((id) => notificacao(id));

describe('novasNotificacoes', () => {
  it('na primeira carga nada é novo: o barbeiro acabou de abrir o site', () => {
    expect(novasNotificacoes(undefined, [a, b])).toEqual([]);
  });

  it('devolve só as que não estavam na carga anterior', () => {
    expect(novasNotificacoes([a], [b, a])).toEqual([b]);
    expect(novasNotificacoes([a], [c, b, a])).toEqual([c, b]);
  });

  it('marcar como lida não é novidade', () => {
    expect(novasNotificacoes([a, b], [a, notificacao('b', true)])).toEqual([]);
  });

  it('lista que encolheu ou ficou igual não tem novidade', () => {
    expect(novasNotificacoes([a, b], [a])).toEqual([]);
    expect(novasNotificacoes([a, b], [a, b])).toEqual([]);
    expect(novasNotificacoes([a], [])).toEqual([]);
  });
});

describe('rotuloDoContador', () => {
  it.each([
    [0, ''],
    [1, '1'],
    [9, '9'],
    [10, '9+'],
    [250, '9+'],
  ])('%s não lidas aparecem como "%s"', (naoLidas, rotulo) => {
    expect(rotuloDoContador(naoLidas)).toBe(rotulo);
  });
});

describe('descricaoDoSino', () => {
  it('diz quantas faltam ler, no singular e no plural, e só o nome quando não há nenhuma', () => {
    expect(descricaoDoSino(0)).toBe('Notificações');
    expect(descricaoDoSino(1)).toBe('Notificações, 1 não lida');
    expect(descricaoDoSino(12)).toBe('Notificações, 12 não lidas');
  });
});

describe('Notificacoes (serviço)', () => {
  const BARBEIRO: Usuario = {
    id: 'u2',
    email: 'rafael@tonelli.com.br',
    papel: 'PROFISSIONAL',
    profissionalId: 'p1',
    clienteId: null,
  };
  const CLIENTE: Usuario = { ...BARBEIRO, id: 'u1', papel: 'CLIENTE', profissionalId: null };

  const resposta = (...recentes: Notificacao[]): NotificacoesResposta => ({
    naoLidas: recentes.filter(({ lida }) => !lida).length,
    recentes,
  });
  /** Deixa as promessas da consulta terminarem. */
  const assentar = () => new Promise<void>((pronto) => setTimeout(pronto));
  const aba = (visivel: boolean) =>
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => !visivel });
  const voltarParaAba = () => document.dispatchEvent(new Event('visibilitychange'));

  let servidor: HttpTestingController;
  let usuario: WritableSignal<Usuario | null>;
  let notificacoes: Notificacoes;

  function iniciar(quem: Usuario | null): void {
    usuario = signal(quem);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Sessao, useValue: { usuario } },
      ],
    });
    servidor = TestBed.inject(HttpTestingController);
    notificacoes = TestBed.inject(Notificacoes);
    TestBed.tick();
  }

  async function primeiraConsulta(...recentes: Notificacao[]): Promise<void> {
    servidor.expectOne('/api/notificacoes').flush(resposta(...recentes));
    await assentar();
  }

  beforeEach(() => aba(true));
  afterEach(() => {
    servidor.verify();
    Reflect.deleteProperty(document, 'hidden');
  });

  it('só consulta quando quem está logado é o barbeiro', () => {
    iniciar(CLIENTE);

    servidor.expectNone('/api/notificacoes');
  });

  it('a primeira consulta mostra as notificações sem contá-las como novas', async () => {
    iniciar(BARBEIRO);

    await primeiraConsulta(a, b);

    expect(notificacoes.naoLidas()).toBe(2);
    expect(notificacoes.recentes()).toEqual([a, b]);
    expect(notificacoes.chegouNova()).toBe(0);
  });

  it('ao voltar para a aba, consulta de novo e conta só as notificações novas', async () => {
    iniciar(BARBEIRO);
    await primeiraConsulta(a);

    voltarParaAba();
    servidor.expectOne('/api/notificacoes').flush(resposta(c, b, a));
    await assentar();

    expect(notificacoes.naoLidas()).toBe(3);
    expect(notificacoes.chegouNova()).toBe(2);
  });

  it('com a aba escondida não consulta', async () => {
    iniciar(BARBEIRO);
    await primeiraConsulta(a);

    aba(false);
    voltarParaAba();

    servidor.expectNone('/api/notificacoes');
  });

  it('se a consulta falha, o sino fica como estava', async () => {
    iniciar(BARBEIRO);
    await primeiraConsulta(a, b);

    voltarParaAba();
    servidor.expectOne('/api/notificacoes').error(new ProgressEvent('error'));
    await assentar();

    expect(notificacoes.naoLidas()).toBe(2);
    expect(notificacoes.chegouNova()).toBe(0);
  });

  it('marcar como lida avisa o back-end e consulta de novo', async () => {
    iniciar(BARBEIRO);
    await primeiraConsulta(a);

    const marcada = notificacoes.marcarComoLida('a');
    servidor.expectOne({ method: 'PATCH', url: '/api/notificacoes/a/ler' }).flush(null);
    await assentar();
    servidor.expectOne('/api/notificacoes').flush(resposta(notificacao('a', true)));
    await marcada;

    expect(notificacoes.naoLidas()).toBe(0);
    expect(notificacoes.chegouNova()).toBe(0);
  });

  it('marcar todas como lidas usa a rota própria', async () => {
    iniciar(BARBEIRO);
    await primeiraConsulta(a, b);

    const marcadas = notificacoes.marcarTodas();
    servidor.expectOne({ method: 'PATCH', url: '/api/notificacoes/ler-todas' }).flush(null);
    await assentar();
    servidor
      .expectOne('/api/notificacoes')
      .flush(resposta(notificacao('a', true), notificacao('b', true)));
    await marcadas;

    expect(notificacoes.naoLidas()).toBe(0);
  });

  it('ao sair, esvazia o sino e para de consultar', async () => {
    iniciar(BARBEIRO);
    await primeiraConsulta(a, b);

    usuario.set(null);
    TestBed.tick();
    voltarParaAba();

    expect(notificacoes.naoLidas()).toBe(0);
    expect(notificacoes.recentes()).toEqual([]);
    servidor.expectNone('/api/notificacoes');
  });

  it('resposta que chega depois da saída não devolve as notificações de quem saiu', async () => {
    iniciar(BARBEIRO);
    const pendente = servidor.expectOne('/api/notificacoes');

    usuario.set(null);
    TestBed.tick();
    pendente.flush(resposta(a, b));
    await assentar();

    expect(notificacoes.naoLidas()).toBe(0);
    expect(notificacoes.recentes()).toEqual([]);
  });
});
