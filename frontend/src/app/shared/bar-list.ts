import { Component, computed, input } from '@angular/core';

export interface BarItem { label: string; value: number; display?: string; tone?: 'ok' | 'warn' | 'bad' | 'brand'; }

/** Barres horizontales étiquetées (top produits, performance transporteurs). */
@Component({
  selector: 'app-bar-list',
  template: `
    @for (b of rows(); track b.label) {
      <div class="row">
        <div class="head"><span>{{ b.label }}</span><span class="num">{{ b.display ?? b.value }}</span></div>
        <div class="track"><div class="fill" [class]="b.tone ?? 'brand'" [style.width.%]="b.pct"></div></div>
      </div>
    } @empty { <p class="muted">Aucune donnée.</p> }
  `,
  styles: `
    .row { margin-bottom: .7rem; } .head { display: flex; justify-content: space-between; font-size: .88rem; gap: 1rem; }
    .track { height: 8px; background: var(--bg); border-radius: 999px; overflow: hidden; margin-top: .25rem; }
    .fill { height: 100%; border-radius: 999px; background: var(--brand); }
    .fill.ok { background: var(--ok); } .fill.warn { background: var(--warn); } .fill.bad { background: var(--bad); }
  `,
})
export class BarList {
  readonly items = input.required<BarItem[]>();
  readonly max = input<number | null>(null);
  protected readonly rows = computed(() => {
    const items = this.items();
    const top = this.max() ?? Math.max(1, ...items.map((i) => i.value));
    return items.map((i) => ({ ...i, pct: Math.min(100, (i.value / top) * 100) }));
  });
}
