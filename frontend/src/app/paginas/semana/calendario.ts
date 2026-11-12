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
import { diaLocal } from '../../core/datas';
import { EventoDaGrade, semanaDe } from './eventos';

const agendamentoDo = (evento: EventApi) => evento.extendedProps['agendamento'] as Agendamento;

/** Um bloco solto em outro horário. Quem recebe confirma no servidor ou chama `reverter`. */
export interface BlocoMovido {
  readonly agendamento: Agendamento;
  /** O novo início como instante ISO (UTC), o formato que a API lê. */
  readonly novoInicio: string;
  readonly reverter: () => void;
}

/** A grade semanal do FullCalendar: recebe os eventos, devolve cliques, arrastes e navegação. */
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
  readonly moveu = output<BlocoMovido>();
  readonly mudouSemana = output<string>();

  private readonly grade = viewChild.required<ElementRef<HTMLElement>>('grade');
  private readonly calendario = signal<Calendar | null>(null);

  constructor() {
    const destruir = inject(DestroyRef);
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
        // Só arrasta quem tem startEditable (o confirmado, em paraEvento); esticar não pode.
        editable: true,
        eventDurationEditable: false,
        // Eventos focáveis pelo teclado, abrindo os detalhes como o clique.
        eventInteractive: true,
        height: 'auto',
        datesSet: ({ view }) => this.mudouSemana.emit(diaLocal(view.currentStart)),
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

      // O FullCalendar mede a grade ao renderizar e quando a janela muda; o painel lateral estreita o
      // contêiner sem mudar a janela. Refazer no quadro seguinte evita o "ResizeObserver loop" do navegador.
      let quadro = 0;
      const observador = new ResizeObserver(() => {
        cancelAnimationFrame(quadro);
        quadro = requestAnimationFrame(() => calendario.updateSize());
      });
      observador.observe(this.grade().nativeElement);
      destruir.onDestroy(() => {
        cancelAnimationFrame(quadro);
        observador.disconnect();
      });

      this.calendario.set(calendario);
    });
    destruir.onDestroy(() => this.calendario()?.destroy());

    // Em cópia: o FullCalendar só aceita lista mutável.
    effect(() => this.calendario()?.setOption('events', [...this.eventos()]));

    // Só navega quando a semana visível é outra: o datesSet devolve a mesma e o ciclo para.
    effect(() => {
      const calendario = this.calendario();
      const dia = this.dia();
      if (calendario && diaLocal(calendario.view.currentStart) !== semanaDe(dia).de) {
        calendario.gotoDate(dia);
      }
    });
  }
}
