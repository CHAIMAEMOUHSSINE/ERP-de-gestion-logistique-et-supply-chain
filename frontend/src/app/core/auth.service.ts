import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse } from './models';

const KEY = 'logismart.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<LoginResponse | null>(this.restore());

  readonly user = computed(() => this.session());
  readonly isLoggedIn = computed(() => {
    const s = this.session();
    return !!s && new Date(s.expiresAt).getTime() > Date.now();
  });
  readonly isAdmin = computed(() => this.session()?.role === 'ADMIN');
  get token(): string | null { return this.session()?.token ?? null; }

  login(username: string, password: string) {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, { username, password }).pipe(
      tap((res) => {
        this.session.set(res);
        try { localStorage.setItem(KEY, JSON.stringify(res)); } catch { /* stockage indisponible */ }
      }),
    );
  }

  logout() {
    this.session.set(null);
    try { localStorage.removeItem(KEY); } catch { /* ignore */ }
    this.router.navigate(['/login']);
  }

  private restore(): LoginResponse | null {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as LoginResponse) : null;
    } catch { return null; }
  }
}
