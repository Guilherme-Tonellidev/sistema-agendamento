import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Calendar, EventApi } from '@fullcalendar/core';
import ptBr from '@fullcalendar/core/locales/pt-br';
import interactionPlugin from '@fullcalendar/interaction';
import timeGridPlugin from '@fullcalendar/timegrid';

import { Agendamento } from '../../core/api/modelos';
import { INTERVALO_ENTRE_HORARIOS_EM_MINUTOS, faixaDeHoras } from '../../core/barbearia';
import { dataNoFuso } from '../../core/datas';
import { EventoDaGrade, semanaDe } from './eventos';

const MEIO_DIA_MS = 12 * 60 * 60 * 1000;

/** O dia (AAAA-MM-DD) dessa meia-noite local, lido ao meio-dia para nenhum fuso cair na véspera. */
const diaDe = (meiaNoite: Date) => dataNoFuso(new Date(meiaNoite.getTime() + MEIO_DIA_MS));

const agendamentoDo = (evento: EventApi) => evento.extendedProps['agendamento'] as Agendamento;

/** A grade semanal do FullCalendar: recebe eventos e o dia a mostrar, devolve cliques e navegação. */
@Component({
  selector: 'app-calendario',
  // Uma div própria: o FullCalendar mexe nas classes dela sem disputar o host com o Angular.
  template: '<div #grade></div>',
})
export class Calendario {
  readonly eventos = input.required<readonly EventoDaGrade[]>();
  readonly dia = input.required<string>();

  readonly clicouVazio = output<Date>();
  readonly clicouEvento = output<Agendamento>();
  readonly moveu = output<{ agendamento: Agendamento; novoInicio: string; reverter: () => void }>();
  readonly mudouSemana = output<string>();

  private readonly grade = viewChild.required<ElementRef<HTMLElement>>('grade');
  private readonly calendario = signal<Calendar | null>(null);

  constructor() {
    // O FullCalendar mexe direto no DOM: só no navegador, depois que o elemento existe.
    afterNextRender(() => {
      const { abre, fecha } = faixaDeHoras();
      const passo = { minutes: INTERVALO_ENTRE_HORARIOS_EM_MINUTOS };
      const calendario = new Calendar(this.grade().nativeElement, {
        plugins: [timeGridPlugin, interactionPlugin],
        initialView: 'timeGridWeek',
        initialDate: this.dia(),
        locale: ptBr,
        firstDay: 1,
        allDaySlot: false,
        nowIndicator: true,
        slotEventOverlap: false,
        timeZone: 'local',
        slotDuration: passo,
        snapDuration: passo,
        slotMinTime: { hours: abre },
        slotMaxTime: { hours: fecha },
        // Arrastar fica desligado até a remarcação pela grade existir.
        editable: false,
        eventDurationEditable: false,
        // Eventos focáveis pelo teclado, abrindo os detalhes como o clique.
        eventInteractive: true,
        height: 'auto',
        datesSet: ({ view }) => this.mudouSemana.emit(diaDe(view.currentStart)),
        // Clique em evento vai para o eventClick; aqui só chegam horários vazios.
        dateClick: ({ date }) => this.clicouVazio.emit(date),
        eventClick: ({ event }) => this.clicouEvento.emit(agendamentoDo(event)),
        eventDrop: ({ event, revert }) => {
          if (!event.start) {
            revert();
            return;
          }
          this.moveu.emit({
            agendamento: agendamentoDo(event),
            novoInicio: event.start.toISOString(),
            reverter: revert,
          });
        },
      });
      calendario.render();
      this.calendario.set(calendario);
    });
    inject(DestroyRef).onDestroy(() => this.calendario()?.destroy());

    // O startEditable de cada evento passaria por cima do editable: desligado também, por ora.
    effect(() =>
      this.calendario()?.setOption(
        'events',
        this.eventos().map((evento) => ({ ...evento, startEditable: false })),
      ),
    );

    // Só navega quando a semana visível é outra: o datesSet devolve a mesma e o ciclo para.
    effect(() => {
      const calendario = this.calendario();
      const dia = this.dia();
      if (calendario && diaDe(calendario.view.currentStart) !== semanaDe(dia).de) {
        calendario.gotoDate(dia);
      }
    });
  }
}
