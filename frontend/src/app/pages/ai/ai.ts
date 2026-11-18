import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../../core/api.service';
import { DelayPrediction, Forecast, Insight, ModelReport } from '../../core/models';
import { LineChart } from '../../shared/line-chart';
import { BarList } from '../../shared/bar-list';
import { RISK, STOCK_STATUS, pct } from '../../shared/badges';

@Component({
  selector: 'app-ai',
  imports: [FormsModule, DatePipe, DecimalPipe, LineChart, BarList],
  template: `
    <div class="page">
      <div class="page-head"><div><h1>Assistant IA</h1>
        <p class="muted">Prévision de la demande, réapprovisionnement et prédiction des retards</p></div></div>
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }

      <section class="card"><h2>Synthèse</h2>
        @for (i of insights(); track i.title) {
          <div class="insight"><span class="badge" [class]="sev(i)">{{ i.severity === 'HIGH' ? 'Urgent' : i.severity === 'MEDIUM' ? 'Attention' : 'Info' }}</span>
            <div><strong>{{ i.title }}</strong><div class="small muted">{{ i.detail }}</div></div></div>
        } @empty { <p class="muted">Chargement…</p> }
      </section>

      <section class="card">
        <h2>Prévision de la demande &amp; réapprovisionnement</h2>
        <p class="small muted">Lissage de Holt (tendance amortie) sur 12 semaines d'historique. Quantité suggérée = demande sur (délai fournisseur + 14 j) + stock de sécurité à 95 %.</p>
        <div class="table-wrap"><table>
          <thead><tr><th>Produit</th><th class="right">Stock</th><th class="right">Demande / jour</th><th>Tendance</th>
            <th class="right">Couverture</th><th>Rupture estimée</th><th class="right">À commander</th><th>État</th></tr></thead>
          <tbody>
            @for (f of forecasts(); track f.productId) {
              <tr class="clickable" (click)="select(f)" [class.sel]="selected()?.productId === f.productId">
                <td>{{ f.name }}<div class="small muted">{{ f.sku }} · délai {{ f.leadTimeDays }} j</div></td>
                <td class="right num">{{ f.stockQty | number }}</td>
                <td class="right num">{{ f.forecastDailyDemand | number: '1.0-1' }}</td>
                <td>{{ f.trend === 'UP' ? '▲ Hausse' : f.trend === 'DOWN' ? '▼ Baisse' : '■ Stable' }}</td>
                <td class="right num">{{ f.daysOfCover === null ? '—' : (f.daysOfCover | number: '1.0-0') + ' j' }}</td>
                <td class="num">{{ f.stockoutDate ? (f.stockoutDate | date: 'dd/MM/yyyy') : '—' }}</td>
                <td class="right num"><strong>{{ f.suggestedReorderQty | number }}</strong></td>
                <td><span class="badge" [class]="stock(f.status).cls">{{ stock(f.status).label }}</span></td>
              </tr>
            }
          </tbody>
        </table></div>
        @if (selected(); as s) {
          <h3 style="margin-top:1.2rem">{{ s.name }} : demande hebdomadaire (trait plein) et prévision 4 semaines (pointillés)</h3>
          <app-line-chart [points]="chartPoints()" [forecastFrom]="12" />
          <p class="small muted">Prévision à 30 jours : <strong>{{ s.forecast30d | number }}</strong> unités.</p>
        }
      </section>

      <div class="grid cols-2">
        <section class="card">
          <h2>Simulateur de risque de retard</h2>
          <p class="small muted">Compare les transporteurs pour une destination, un poids et une date de départ.</p>
          <form (ngSubmit)="predict()" class="form-grid">
            <label>Destination
              <select name="city" [(ngModel)]="city">@for (c of cities(); track c) { <option [value]="c">{{ c }}</option> }</select></label>
            <label>Poids (kg)<input name="w" type="number" min="1" [(ngModel)]="weight" required /></label>
            <label>Date de départ<input name="d" type="date" [(ngModel)]="shipDate" /></label>
            <button class="primary">Simuler</button>
          </form>
          @if (prediction(); as p) {
            <p style="margin-top:1rem" class="small muted">Distance estimée : <strong>{{ p.distanceKm }} km</strong> · transit {{ p.transitDays }} j ·
              recommandé : <strong>{{ p.recommendedCarrier }}</strong></p>
            <app-bar-list [items]="predictionItems()" [max]="100" />
          }
        </section>

        <section class="card">
          <div class="page-head"><h2>Modèle de prédiction des retards</h2>
            <button class="sm" (click)="retrain()" [disabled]="training()">{{ training() ? 'Entraînement…' : 'Ré-entraîner' }}</button></div>
          @if (model(); as m) {
            @if (m.trained) {
              <div class="grid kpis" style="grid-template-columns: repeat(3, 1fr)">
                <div class="kpi"><div class="label">Précision</div><div class="value">{{ pct(m.accuracy) }}</div></div>
                <div class="kpi"><div class="label">AUC</div><div class="value">{{ m.auc | number: '1.2-2' }}</div></div>
                <div class="kpi"><div class="label">Échantillons</div><div class="value">{{ m.trainSize + m.testSize }}</div></div>
              </div>
              <p class="small muted">Régression logistique entraînée sur les livraisons passées (80 % entraînement / 20 % test). Taux de retard historique : {{ pct(m.baseRate) }}.</p>
              <h3>Poids des variables</h3>
              <app-bar-list [items]="coefItems()" />
            } @else { <p class="muted">Modèle a priori (pas assez de livraisons pour l'entraîner).</p> }
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .insight { display: flex; gap: .6rem; align-items: start; padding: .5rem 0; border-bottom: 1px solid var(--line); }
    .insight:last-child { border-bottom: 0; } .insight .badge { margin-top: .15rem; }
    tr.sel td { background: var(--brand-soft); }
  `,
})
export class AiPage {
  private readonly api = inject(ApiService);
  protected readonly insights = signal<Insight[]>([]);
  protected readonly forecasts = signal<Forecast[]>([]);
  protected readonly selected = signal<Forecast | null>(null);
  protected readonly model = signal<ModelReport | null>(null);
  protected readonly cities = signal<string[]>([]);
  protected readonly prediction = signal<DelayPrediction | null>(null);
  protected readonly training = signal(false);
  protected readonly error = signal('');
  protected readonly pct = pct;
  protected city = 'Marrakech';
  protected weight = 500;
  protected shipDate = '';

  protected readonly chartPoints = computed(() => {
    const s = this.selected();
    return s ? [...s.history, ...s.forecast].map((w) => ({ label: w.weekStart.slice(5), value: w.quantity })) : [];
  });
  protected readonly predictionItems = computed(() =>
    (this.prediction()?.carriers ?? []).map((c) => ({
      label: c.carrier, value: c.probability * 100, display: pct(c.probability),
      tone: c.level === 'HIGH' ? ('bad' as const) : c.level === 'MEDIUM' ? ('warn' as const) : ('ok' as const),
    })));
  protected readonly coefItems = computed(() =>
    [...(this.model()?.coefficients ?? [])].sort((a, b) => b.weight - a.weight).map((c) => ({
      label: c.feature, value: Math.abs(c.weight) * 100, display: (c.weight >= 0 ? '+' : '') + c.weight.toFixed(2),
      tone: c.weight >= 0 ? ('bad' as const) : ('ok' as const),
    })));

  constructor() {
    this.api.insights().subscribe({ next: (i) => this.insights.set(i), error: (e) => this.error.set(errorMessage(e)) });
    this.api.forecast().subscribe({
      next: (f) => { this.forecasts.set(f); this.selected.set(f[0] ?? null); }, error: (e) => this.error.set(errorMessage(e)),
    });
    this.api.model().subscribe({ next: (m) => this.model.set(m), error: (e) => this.error.set(errorMessage(e)) });
    this.api.meta().subscribe((m) => this.cities.set(m.cities));
  }

  protected select(f: Forecast) { this.selected.set(f); }
  protected stock(s: string) { return STOCK_STATUS[s] ?? { label: s, cls: 'b-mute' }; }
  protected sev(i: Insight) { return i.severity === 'HIGH' ? 'b-bad' : i.severity === 'MEDIUM' ? 'b-warn' : 'b-info'; }
  protected risk(l: string) { return RISK[l]; }

  protected predict() {
    this.api.predictDelay(this.city, this.weight, this.shipDate || undefined).subscribe({
      next: (p) => this.prediction.set(p), error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected retrain() {
    this.training.set(true);
    this.api.retrain().subscribe({
      next: (m) => { this.model.set(m); this.training.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.training.set(false); },
    });
  }
}
