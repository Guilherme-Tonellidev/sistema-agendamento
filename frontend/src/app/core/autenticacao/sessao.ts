import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, firstValueFrom, map, shareReplay, switchMap, tap } from 'rxjs';

import { Cadastro, Credenciais, Token, Usuario } from '../api/modelos';

/** Só um aviso de que houve login neste navegador. O token de acesso nunca vai para o storage. */
const MARCA_DE_SESSAO = 'tonelli.sessao';

/**
 * Sessão do usuário. O token de acesso fica só em memória (um XSS não o encontra no storage) e,
 * quando expira, é trocado pelo refresh token que o back-end guarda num cookie HttpOnly.
 */
@Injectable({ providedIn: 'root' })
export class Sessao {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private readonly conta = signal<Usuario | null>(null);
  private renovacaoEmAndamento: Observable<string> | null = null;

  readonly usuario = this.conta.asReadonly();
  readonly logado = computed(() => this.conta() !== null);

  tokenAtual(): string | null {
    return this.token();
  }

  entrar(credenciais: Credenciais): Observable<Usuario> {
    return this.http
      .post<Token>('/api/auth/login', credenciais)
      .pipe(switchMap((token) => this.abrir(token)));
  }

  cadastrar(cadastro: Cadastro): Observable<Usuario> {
    return this.http
      .post<Token>('/api/auth/cadastro', cadastro)
      .pipe(switchMap((token) => this.abrir(token)));
  }

  /** Requisições que recebem 401 ao mesmo tempo compartilham uma única renovação. */
  renovar(): Observable<string> {
    this.renovacaoEmAndamento ??= this.http.post<Token>('/api/auth/refresh', null).pipe(
      map(({ tokenDeAcesso }) => {
        this.token.set(tokenDeAcesso);
        return tokenDeAcesso;
      }),
      finalize(() => (this.renovacaoEmAndamento = null)),
      shareReplay(1),
    );
    return this.renovacaoEmAndamento;
  }

  /** Ao abrir o site, recupera a sessão pelo cookie, se houve login antes neste navegador. */
  async restaurar(): Promise<void> {
    if (!lerMarca()) {
      return;
    }
    try {
      await firstValueFrom(this.renovar());
      this.conta.set(await firstValueFrom(this.http.get<Usuario>('/api/auth/eu')));
    } catch {
      this.encerrar();
    }
  }

  sair(): void {
    this.http
      .post<void>('/api/auth/logout', null)
      .pipe(
        finalize(() => {
          this.encerrar();
          void this.router.navigateByUrl('/entrar');
        }),
      )
      .subscribe({ error: () => undefined });
  }

  /** Esquece a sessão só neste navegador (o cookie já expirou ou foi revogado). */
  encerrar(): void {
    this.token.set(null);
    this.conta.set(null);
    gravarMarca(false);
  }

  private abrir({ tokenDeAcesso }: Token): Observable<Usuario> {
    this.token.set(tokenDeAcesso);
    return this.http.get<Usuario>('/api/auth/eu').pipe(
      tap((usuario) => {
        this.conta.set(usuario);
        gravarMarca(true);
      }),
    );
  }
}

function lerMarca(): boolean {
  try {
    return localStorage.getItem(MARCA_DE_SESSAO) === '1';
  } catch {
    return false;
  }
}

function gravarMarca(ativa: boolean): void {
  try {
    if (ativa) {
      localStorage.setItem(MARCA_DE_SESSAO, '1');
    } else {
      localStorage.removeItem(MARCA_DE_SESSAO);
    }
  } catch {
    // Storage bloqueado (aba anônima, por exemplo): a sessão só não sobrevive ao recarregar.
  }
}
