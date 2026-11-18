import { Component, computed, input } from '@angular/core';

export interface ChartPoint { label: string; value: number; }

/**
 * Courbe SVG sans dépendance. `forecastFrom` : index à partir duquel la courbe est une prévision (pointillés).
 */
@Component({
  selector: 'app-line-chart',
  template: `
    <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel()" preserveAspectRatio="none">
      @for (t of ticks(); track t.y) {
        <line [attr.x1]="PAD_L" [attr.x2]="W - PAD_R" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
        <text [attr.x]="PAD_L - 6" [attr.y]="t.y + 4" class="axis" text-anchor="end">{{ t.text }}</text>
      }
      @if (areaPath()) { <path [attr.d]="areaPath()" class="area" /> }
      @if (actualPath()) { <path [attr.d]="actualPath()" class="line" /> }
      @if (forecastPath()) { <path [attr.d]="forecastPath()" class="line forecast" /> }
      @for (x of xLabels(); track x.x) {
        <text [attr.x]="x.x" [attr.y]="H - 6" class="axis" [attr.text-anchor]="x.anchor">{{ x.text }}</text>
      }
    </svg>
  `,
  styles: `
    :host { display: block; }
    svg { width: 100%; height: 220px; display: block; }
    .grid { stroke: var(--line); stroke-width: 1; }
    .axis { fill: var(--muted); font-size: 11px; }
    .line { fill: none; stroke: var(--brand); stroke-width: 2.2; stroke-linejoin: round; vector-effect: non-scaling-stroke; }
    .line.forecast { stroke: var(--warn); stroke-dasharray: 6 5; }
    .area { fill: var(--brand-soft); opacity: .7; }
  `,
})
export class LineChart {
  readonly points = input.required<ChartPoint[]>();
  readonly forecastFrom = input<number | null>(null);
  readonly unit = input('');

  protected readonly W = 640; protected readonly H = 220;
  protected readonly PAD_L = 44; protected readonly PAD_R = 10; private readonly PAD_T = 10; private readonly PAD_B = 24;

  private readonly max = computed(() => {
    const m = Math.max(0, ...this.points().map((p) => p.value));
    return m <= 0 ? 1 : this.niceCeil(m);
  });
  private x(i: number) {
    const n = Math.max(1, this.points().length - 1);
    return this.PAD_L + (i * (this.W - this.PAD_L - this.PAD_R)) / n;
  }
  private y(v: number) {
    return this.PAD_T + (1 - v / this.max()) * (this.H - this.PAD_T - this.PAD_B);
  }
  private path(from: number, to: number) {
    const pts = this.points();
    let d = '';
    for (let i = from; i <= to && i < pts.length; i++) {
      d += `${d ? 'L' : 'M'}${this.x(i).toFixed(1)},${this.y(pts[i].value).toFixed(1)} `;
    }
    return d.trim();
  }
  private niceCeil(v: number) {
    const mag = Math.pow(10, Math.floor(Math.log10(v)));
    for (const s of [1, 2, 2.5, 5, 10]) { if (v <= s * mag) { return s * mag; } }
    return 10 * mag;
  }

  protected readonly actualPath = computed(() => {
    const f = this.forecastFrom();
    return this.path(0, f === null ? this.points().length - 1 : f - 1);
  });
  protected readonly forecastPath = computed(() => {
    const f = this.forecastFrom();
    return f === null || f < 1 ? '' : this.path(f - 1, this.points().length - 1);
  });
  protected readonly areaPath = computed(() => {
    const f = this.forecastFrom();
    const last = f === null ? this.points().length - 1 : f - 1;
    if (last < 1) { return ''; }
    const base = this.y(0).toFixed(1);
    return `${this.path(0, last)} L${this.x(last).toFixed(1)},${base} L${this.x(0).toFixed(1)},${base} Z`;
  });
  protected readonly ticks = computed(() =>
    [0, 0.5, 1].map((r) => ({ y: this.y(this.max() * r), text: this.format(this.max() * r) })));
  protected readonly xLabels = computed(() => {
    const pts = this.points();
    const step = Math.max(1, Math.ceil(pts.length / 6));
    const last = pts.length - 1;
    return pts
      .map((p, i) => ({ x: this.x(i), text: p.label, show: i % step === 0, anchor: i === 0 ? 'start' : i === last ? 'end' : 'middle' }))
      .filter((l) => l.show);
  });
  protected readonly ariaLabel = computed(() => `Courbe de ${this.points().length} points`);
  private format(v: number) {
    return v >= 1000 ? `${Math.round(v / 1000)}k` : `${Math.round(v * 10) / 10}`;
  }
}
