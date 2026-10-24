import { HttpClient } from '@angular/common/http';
import { Component, inject, input, linkedSignal, output } from '@angular/core';
import {
  FormField,
  FormRoot,
  TreeValidationResult,
  form,
  maxLength,
  required,
  validate,
} from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';

import { errosDoEnvio } from '../../core/api/erros';
import { NovoBloqueio } from '../../core/api/modelos';
import { dataNoFuso, instanteNoFuso, minutosNoFuso } from '../../core/datas';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';

const ULTIMO_QUARTO_DE_HORA = 23 * 60 + 45;

const relogio = (minutos: number) => {
  const limitado = Math.min(minutos, ULTIMO_QUARTO_DE_HORA);
  return `${String(Math.floor(limitado / 60)).padStart(2, '0')}:${String(limitado % 60).padStart(2, '0')}`;
};

/** Uma hora a partir do próximo quarto de hora; em outro dia, a partir do meio-dia. */
function sugestao(dia: string, agora: Date) {
  const inicio =
    dia === dataNoFuso(agora) ? Math.ceil((minutosNoFuso(agora) + 1) / 15) * 15 : 12 * 60;
  return { inicio: relogio(inicio), fim: relogio(inicio + 60), motivo: '' };
}

/** Período do dia em que o barbeiro não atende; a API recusa se já houver cliente marcado nele. */
@Component({
  selector: 'app-painel-bloqueio',
  imports: [FormField, FormRoot, Ingresso],
  templateUrl: './painel-bloqueio.html',
  styleUrl: './painel.css',
})
export class PainelBloqueio {
  private readonly http = inject(HttpClient);

  readonly profissionalId = input.required<string>();
  readonly dia = input.required<string>();
  readonly bloqueado = output<void>();
  readonly voltar = output<void>();

  /** Horas do relógio da barbearia (HH:mm); a sugestão recomeça quando o dia muda. */
  protected readonly periodo = form(
    linkedSignal(() => sugestao(this.dia(), new Date())),
    (campo) => {
      required(campo.inicio, { message: 'Informe a hora de início.' });
      required(campo.fim, { message: 'Informe a hora de fim.' });
      validate(campo.fim, ({ value, valueOf }) =>
        !value() || value() > valueOf(campo.inicio)
          ? undefined
          : { kind: 'ordem', message: 'O fim precisa ser depois do início.' },
      );
      maxLength(campo.motivo, 200, { message: 'Use até 200 caracteres.' });
    },
    { submission: { action: () => this.bloquear() } },
  );

  private async bloquear(): Promise<TreeValidationResult> {
    const { inicio, fim, motivo } = this.periodo().value();
    const bloqueio: NovoBloqueio = {
      inicio: instanteNoFuso(this.dia(), inicio).toISOString(),
      fim: instanteNoFuso(this.dia(), fim).toISOString(),
      motivo: motivo.trim() || null,
    };
    try {
      await firstValueFrom(
        this.http.post(`/api/profissionais/${this.profissionalId()}/bloqueios`, bloqueio),
      );
      this.bloqueado.emit();
      return undefined;
    } catch (erro) {
      return errosDoEnvio(erro, this.periodo, 'Não foi possível bloquear agora. Tente de novo.');
    }
  }
}
