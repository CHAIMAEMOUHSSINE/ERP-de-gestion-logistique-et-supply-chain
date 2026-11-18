import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/api.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="wrap">
      <form class="card" (ngSubmit)="submit()" #f="ngForm">
        <div class="brand"><span class="logo">LS</span> LogiSmart</div>
        <p class="muted">Mini ERP logistique &amp; supply chain avec IA</p>
        <label>Identifiant
          <input name="username" [(ngModel)]="username" required autocomplete="username" />
        </label>
        <label>Mot de passe
          <input name="password" type="password" [(ngModel)]="password" required autocomplete="current-password" />
        </label>
        @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
        <button class="primary" [disabled]="f.invalid || loading()">{{ loading() ? 'Connexion…' : 'Se connecter' }}</button>
        <div class="demo small muted">
          Comptes de démonstration :
          <button type="button" class="sm" (click)="fill('admin', 'admin123')">admin</button>
          <button type="button" class="sm" (click)="fill('manager', 'manager123')">manager</button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: 1rem; }
    form { width: min(380px, 100%); display: flex; flex-direction: column; gap: .9rem; padding: 1.5rem; }
    .brand { font-weight: 700; font-size: 1.3rem; display: flex; align-items: center; gap: .6rem; }
    .logo { background: var(--brand); color: var(--brand-ink); border-radius: 8px; padding: .15rem .45rem; font-size: .95rem; }
    .demo { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected username = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  constructor() {
    if (this.auth.isLoggedIn()) { this.router.navigate(['/dashboard']); }
  }
  protected fill(u: string, p: string) { this.username = u; this.password = p; }
  protected submit() {
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
