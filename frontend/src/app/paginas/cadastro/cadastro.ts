import { Component, inject, input, signal } from '@angular/core';
import {
  FieldTree,
  FormField,
  FormRoot,
  TreeValidationResult,
  email,
  form,
  maxLength,
  minLength,
  required,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { errosDoEnvio } from '../../core/api/erros';
import { destinoAposEntrar } from '../../core/autenticacao/guardas';
import { Sessao } from '../../core/autenticacao/sessao';
import { soDigitos, telefoneOpcional } from '../../compartilhado/telefone';
import { Vitrine } from '../../compartilhado/vitrine/vitrine';

/** O que a pessoa digita; celular em branco vai como null para a API. */
interface NovaConta {
  nome: string;
  email: string;
  telefone: string;
  senha: string;
}

/** Mesmos limites do CadastroRequest no back-end. */
const NOME_MAXIMO = 120;
const SENHA_MINIMA = 8;
const SENHA_MAXIMA = 64;

@Component({
  selector: 'app-cadastro',
  imports: [FormField, FormRoot, RouterLink, Vitrine],
  templateUrl: './cadastro.html',
  styleUrl: '../acesso.css',
})
export class Cadastro {
  private readonly sessao = inject(Sessao);
  private readonly router = inject(Router);

  readonly voltar = input<string>();

  protected readonly conta = form(
    signal<NovaConta>({ nome: '', email: '', telefone: '', senha: '' }),
    (campo) => {
      required(campo.nome, { message: 'Informe seu nome.' });
      maxLength(campo.nome, NOME_MAXIMO, { message: `Use até ${NOME_MAXIMO} caracteres.` });
      required(campo.email, { message: 'Informe seu e-mail.' });
      email(campo.email, { message: 'Confira o e-mail digitado.' });
      telefoneOpcional(campo.telefone);
      required(campo.senha, {
        message: `Crie uma senha com pelo menos ${SENHA_MINIMA} caracteres.`,
      });
      minLength(campo.senha, SENHA_MINIMA, {
        message: `Use pelo menos ${SENHA_MINIMA} caracteres.`,
      });
      maxLength(campo.senha, SENHA_MAXIMA, { message: `Use até ${SENHA_MAXIMA} caracteres.` });
    },
    { submission: { action: (conta) => this.criar(conta) } },
  );

  private async criar(conta: FieldTree<NovaConta>): Promise<TreeValidationResult> {
    const { telefone, ...dados } = conta().value();
    try {
      const usuario = await firstValueFrom(
        this.sessao.cadastrar({ ...dados, telefone: soDigitos(telefone) || null }),
      );
      await this.router.navigateByUrl(destinoAposEntrar(usuario, this.voltar()));
      return undefined;
    } catch (erro) {
      return errosDoEnvio(erro, conta, 'Não foi possível criar a conta agora. Tente de novo.');
    }
  }
}
