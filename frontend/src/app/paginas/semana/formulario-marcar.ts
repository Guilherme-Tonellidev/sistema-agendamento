import { HttpClient, httpResource } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import {
  FormField,
  FormRoot,
  TreeValidationResult,
  applyWhen,
  form,
  maxLength,
  required,
} from '@angular/forms/signals';
import { Observable, firstValueFrom } from 'rxjs';

import { errosDoEnvio } from '../../core/api/erros';
import {
  Agendamento,
  ClienteCadastrado,
  HorarioLivre,
  NovoAgendamento,
  NovoEncaixe,
  Profissional,
  ProfissionalComServicos,
} from '../../core/api/modelos';
import { conteudo, ultimoValor } from '../../core/api/recursos';
import { comMaiuscula, dataNoFuso, dataPorExtenso, horaNoFuso } from '../../core/datas';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';
import { TelefonePipe, soDigitos, telefoneOpcional } from '../../compartilhado/telefone';
import { escolherHorario } from './marcar';

/** Quantos horários aparecem de cada vez, como no encaixe da agenda. */
const HORARIOS_VISIVEIS = 8;
/** Espera entre a última tecla e a busca de clientes. */
const ESPERA_DA_BUSCA_MS = 300;

/** Marca pela grade: barbeiro, serviço, horário do dia clicado e cliente (cadastrado ou novo). */
@Component({
  selector: 'app-formulario-marcar',
  imports: [FormField, FormRoot, Ingresso, TelefonePipe],
  templateUrl: './formulario-marcar.html',
  styleUrl: '../agenda/painel.css',
})
export class FormularioMarcar {
  private readonly http = inject(HttpClient);

  readonly inicio = input.required<Date>();
  readonly marcado = output<Agendamento>();
  readonly voltar = output<void>();

  private readonly equipe = httpResource(
    () => ({ url: '/api/profissionais', params: { size: 50 } }),
    { parse: conteudo<Profissional> },
  );
  private readonly equipeCarregada = ultimoValor(this.equipe, []);
  /** Inativo não atende mais: fica fora da escolha. */
  protected readonly barbeiros = computed(() =>
    this.equipeCarregada().filter(({ ativo }) => ativo),
  );

  /** Sem barbeiro nem serviço pré-escolhidos: marcar na agenda errada é pior que um clique a mais. */
  protected readonly profissionalId = signal<string | null>(null);
  private readonly profissional = httpResource<ProfissionalComServicos>(() => {
    const id = this.profissionalId();
    return id ? `/api/profissionais/${id}` : undefined;
  });
  protected readonly servicos = computed(() =>
    this.profissional.hasValue()
      ? [...this.profissional.value().servicos].sort((a, b) =>
          a.nome.localeCompare(b.nome, 'pt-BR'),
        )
      : [],
  );
  protected readonly servicoId = signal<string | null>(null);
  /** Trocar para um barbeiro que não faz o serviço deixa o serviço sem escolha. */
  protected readonly servico = computed(
    () => this.servicos().find(({ id }) => id === this.servicoId()) ?? null,
  );

  private readonly dia = computed(() => dataNoFuso(this.inicio()));
  /** Sem limite: o administrador marca no passado e além da janela de reserva. */
  private readonly livres = httpResource<HorarioLivre[]>(() => {
    const servico = this.servico();
    const id = this.profissionalId();
    return servico && id
      ? {
          url: `/api/profissionais/${id}/horarios-livres/encaixe`,
          params: { servicoId: servico.id, data: this.dia(), semLimite: true },
        }
      : undefined;
  });
  private readonly horarios = ultimoValor(this.livres, []);
  protected readonly buscando = this.livres.isLoading;

  /** Volta ao horário mais perto do clique sempre que a lista ou o clique mudam. */
  protected readonly escolhido = linkedSignal<
    { horarios: readonly HorarioLivre[]; desejado: Date },
    HorarioLivre | null
  >({
    source: () => ({ horarios: this.horarios(), desejado: this.inicio() }),
    computation: ({ horarios, desejado }) => escolherHorario(horarios, desejado),
  });
  /** Em páginas fixas, para os botões não trocarem de lugar enquanto a pessoa escolhe. */
  protected readonly opcoes = computed(() => {
    const horarios = this.horarios();
    const escolhido = this.escolhido();
    const pagina = escolhido ? Math.floor(horarios.indexOf(escolhido) / HORARIOS_VISIVEIS) : 0;
    const desde = pagina * HORARIOS_VISIVEIS;
    return horarios
      .slice(desde, desde + HORARIOS_VISIVEIS)
      .map((horario) => ({ horario, hora: horaNoFuso(horario.inicio) }));
  });
  protected readonly titulo = computed(() => ({
    dia: comMaiuscula(dataPorExtenso(this.dia())),
    hora: horaNoFuso(this.escolhido()?.inicio ?? this.inicio()),
  }));

  protected readonly aba = signal<'cadastrado' | 'novo'>('cadastrado');

  /** O que a pessoa digitou na busca e o que já vale para buscar (depois da espera). */
  protected readonly busca = signal('');
  private readonly termo = signal('');
  private espera: ReturnType<typeof setTimeout> | undefined;
  private readonly encontrados = httpResource(
    () => {
      const nome = this.termo();
      return nome.length >= 2 ? { url: '/api/clientes', params: { nome, size: 8 } } : undefined;
    },
    { parse: conteudo<ClienteCadastrado> },
  );
  protected readonly clientes = ultimoValor(this.encontrados, []);
  protected readonly procurando = this.encontrados.isLoading;
  protected readonly nenhumCliente = computed(
    () => this.encontrados.hasValue() && this.encontrados.value().length === 0,
  );
  protected readonly clienteEscolhido = signal<ClienteCadastrado | null>(null);

  protected readonly cliente = form(
    signal({ nomeDoCliente: '', telefone: '' }),
    (campo) => {
      // Só o cliente novo preenche nome e telefone; na outra aba eles não barram o envio.
      applyWhen(
        campo,
        () => this.aba() === 'novo',
        (novo) => {
          required(novo.nomeDoCliente, { message: 'Informe o nome do cliente.' });
          maxLength(novo.nomeDoCliente, 120, { message: 'Use até 120 caracteres.' });
          telefoneOpcional(novo.telefone);
        },
      );
    },
    { submission: { action: () => this.marcar() } },
  );

  protected readonly pronto = computed(
    () =>
      !!this.escolhido() &&
      !this.buscando() &&
      (this.aba() === 'novo' || !!this.clienteEscolhido()),
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.espera));
  }

  /** Outra busca desfaz a escolha: o cliente escolhido fica sempre visível na lista. */
  protected digitou(texto: string): void {
    this.busca.set(texto);
    this.clienteEscolhido.set(null);
    clearTimeout(this.espera);
    this.espera = setTimeout(() => this.termo.set(texto.trim()), ESPERA_DA_BUSCA_MS);
  }

  /** Enter busca na hora, sem enviar o formulário. */
  protected buscarJa(evento: Event): void {
    evento.preventDefault();
    clearTimeout(this.espera);
    this.termo.set(this.busca().trim());
  }

  private async marcar(): Promise<TreeValidationResult> {
    const horario = this.escolhido();
    const servico = this.servico();
    const profissionalId = this.profissionalId();
    if (!horario || !servico || !profissionalId) {
      return { kind: 'horario', message: 'Escolha o barbeiro, o serviço e um horário livre.' };
    }
    const base = { profissionalId, servicoId: servico.id, inicio: horario.inicio };
    const envio = this.aba() === 'cadastrado' ? this.reservar(base) : this.encaixar(base);
    if (!envio) {
      return { kind: 'cliente', message: 'Escolha o cliente na busca.' };
    }
    try {
      this.marcado.emit(await firstValueFrom(envio));
      return undefined;
    } catch (erro) {
      // Se alguém pegou o horário antes, a lista se atualiza.
      this.livres.reload();
      return errosDoEnvio(erro, this.cliente, 'Não foi possível marcar agora. Tente de novo.');
    }
  }

  private reservar(base: Omit<NovoAgendamento, 'clienteId'>): Observable<Agendamento> | null {
    const cadastrado = this.clienteEscolhido();
    if (!cadastrado) {
      return null;
    }
    const pedido: NovoAgendamento = { ...base, clienteId: cadastrado.id };
    return this.http.post<Agendamento>('/api/agendamentos', pedido);
  }

  /** Cliente novo entra pelo encaixe, que o cadastra só com nome e telefone. */
  private encaixar(base: Omit<NovoAgendamento, 'clienteId'>): Observable<Agendamento> {
    const { nomeDoCliente, telefone } = this.cliente().value();
    const encaixe: NovoEncaixe = {
      ...base,
      nomeDoCliente: nomeDoCliente.trim(),
      telefone: soDigitos(telefone) || null,
    };
    return this.http.post<Agendamento>('/api/agendamentos/encaixe', encaixe);
  }
}
