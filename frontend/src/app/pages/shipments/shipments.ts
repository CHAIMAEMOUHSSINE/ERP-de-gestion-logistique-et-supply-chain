import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../../core/api.service';
import { Shipment } from '../../core/models';
import { RISK, SHIPMENT_STATUS, pct } from '../../shared/badges';

@Component({
  selector: 'app-shipments',
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Expéditions</h1><p class="muted">Suivi des livraisons et risque de retard prédit par l'IA</p></div>
        <select [ngModel]="filter()" (ngModelChange)="filter.set($event); load()" aria-label="Filtrer par statut">
          <option value="">Tous les statuts</option>
          <option value="PLANNED">Planifiées</option><option value="IN_TRANSIT">En transit</option><option value="DELIVERED">Livrées</option>
        </select>
      </div>

      @if (message()) { <div class="alert success" role="status">{{ message() }}</div> }
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }

      <div class="card table-wrap">
        <table>
          <thead><tr><th>Réf.</th><th>Commande / client</th><th>Trajet</th><th>Transporteur</th><th>Départ</th><th>Livraison prévue</th>
            <th>Statut</th><th>Risque de retard</th><th></th></tr></thead>
          <tbody>
            @for (s of shipments(); track s.id) {
              <tr>
                <td class="num"><strong>{{ s.reference }}</strong></td>
                <td>{{ s.orderReference }}<div class="small muted">{{ s.customerName }}</div></td>
                <td>{{ s.originCity }} → {{ s.destinationCity }}<div class="small muted num">{{ s.distanceKm }} km · {{ s.weightKg | number: '1.0-0' }} kg</div></td>
                <td>{{ s.carrier }}</td>
                <td class="num">{{ s.shipDate | date: 'dd/MM' }}</td>
                <td class="num">{{ s.plannedDelivery | date: 'dd/MM/yyyy' }}
                  @if (s.deliveredAt) { <div class="small muted">livrée le {{ s.deliveredAt | date: 'dd/MM' }}</div> }</td>
                <td><span class="badge" [class]="st(s.status).cls">{{ st(s.status).label }}</span>
                  @if (s.overdue) { <div><span class="badge b-bad">En retard</span></div> }</td>
                <td>
                  @if (s.riskScore !== null && s.riskLevel) {
                    <span class="badge" [class]="risk(s.riskLevel).cls">{{ risk(s.riskLevel).label }} · {{ pct(s.riskScore) }}</span>
                    @if (s.riskFactors.length) { <div class="small muted">{{ s.riskFactors.join(' · ') }}</div> }
                  } @else if (s.delayed !== null) {
                    <span class="badge" [class]="s.delayed ? 'b-bad' : 'b-ok'">{{ s.delayed ? 'Livrée en retard' : 'Livrée à l\\'heure' }}</span>
                  }
                </td>
                <td class="actions">
                  @if (s.status === 'PLANNED') { <button class="sm primary" (click)="dispatch(s)">Expédier</button> }
                  @if (s.status === 'IN_TRANSIT') {
                    <button class="sm primary" (click)="deliver(s, 0)">Livrer à l'heure</button>
                    <button class="sm" (click)="deliver(s, 2)" title="Simule une livraison avec 2 jours de retard">Livrer +2 j</button>
                  }
                </td>
              </tr>
            } @empty { <tr><td colspan="9" class="empty">Aucune expédition.</td></tr> }
          </tbody>
        </table>
      </div>
      <p class="small muted">« Livrer +2 j » simule un retard : chaque livraison enrichit l'historique, ré-entraînable depuis l'onglet Assistant IA.</p>
    </div>
  `,
})
export class ShipmentsPage {
  private readonly api = inject(ApiService);
  protected readonly shipments = signal<Shipment[]>([]);
  protected readonly filter = signal('');
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly pct = pct;

  constructor() { this.load(); }

  protected load() {
    this.api.shipments(this.filter() || undefined).subscribe({
      next: (s) => this.shipments.set(s), error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected st(s: string) { return SHIPMENT_STATUS[s] ?? { label: s, cls: 'b-mute' }; }
  protected risk(l: string) { return RISK[l] ?? { label: l, cls: 'b-mute' }; }
  protected dispatch(s: Shipment) {
    this.api.dispatch(s.id).subscribe({
      next: () => { this.flash(`${s.reference} expédiée.`); this.load(); }, error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected deliver(s: Shipment, delay: number) {
    this.api.deliver(s.id, delay).subscribe({
      next: (r) => { this.flash(`${s.reference} livrée ${r.delayed ? 'avec retard' : 'à l\'heure'}.`); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  private flash(text: string) { this.error.set(''); this.message.set(text); setTimeout(() => this.message.set(''), 4000); }
}
