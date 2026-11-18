export interface LoginResponse { token: string; username: string; fullName: string; role: string; expiresAt: string; }

export interface Product {
  id: number; sku: string; name: string; category: string; unitPrice: number; weightKg: number;
  stockQty: number; reorderLevel: number; leadTimeDays: number; lowStock: boolean;
}
export type ProductRequest = Omit<Product, 'id' | 'lowStock'>;
export interface Movement { id: number; type: 'IN' | 'OUT'; quantity: number; reason: string; reference: string; createdAt: string; }

export interface Customer { id: number; name: string; city: string; email: string; phone: string; }

export interface OrderLine { productId: number; sku: string; name: string; quantity: number; unitPrice: number; lineTotal: number; }
export interface ShipmentSummary { id: number; reference: string; status: string; carrier: string; plannedDelivery: string; }
export interface Order {
  id: number; reference: string; customerId: number; customerName: string; city: string; status: OrderStatus;
  createdAt: string; totalAmount: number; lines: OrderLine[]; shipment: ShipmentSummary | null;
}
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface Shipment {
  id: number; reference: string; orderId: number; orderReference: string; customerName: string; carrier: string;
  originCity: string; destinationCity: string; distanceKm: number; weightKg: number;
  status: 'PLANNED' | 'IN_TRANSIT' | 'DELIVERED'; shipDate: string; plannedDelivery: string;
  deliveredAt: string | null; delayed: boolean | null; overdue: boolean;
  riskScore: number | null; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | null; riskFactors: string[];
}

export interface WeekPoint { weekStart: string; quantity: number; }
export interface Forecast {
  productId: number; sku: string; name: string; stockQty: number; leadTimeDays: number;
  avgDailyDemand: number; forecastDailyDemand: number; forecast30d: number; trend: 'UP' | 'DOWN' | 'STABLE';
  daysOfCover: number | null; stockoutDate: string | null; suggestedReorderQty: number;
  status: 'CRITICAL' | 'WARNING' | 'OK'; history: WeekPoint[]; forecast: WeekPoint[];
}
export interface CarrierRisk { carrier: string; probability: number; level: string; }
export interface DelayPrediction { distanceKm: number; transitDays: number; carriers: CarrierRisk[]; recommendedCarrier: string; }
export interface Insight { severity: 'HIGH' | 'MEDIUM' | 'INFO'; title: string; detail: string; }
export interface ModelReport {
  trained: boolean; trainSize: number; testSize: number; accuracy: number; auc: number; baseRate: number;
  trainedAt: string | null; coefficients: { feature: string; weight: number }[];
}

export interface Dashboard {
  kpis: {
    totalProducts: number; lowStockCount: number; stockValue: number; pendingOrders: number; activeShipments: number;
    atRiskShipments: number; overdueShipments: number; revenue30d: number; revenueGrowthPct: number; onTimeRate: number;
  };
  ordersByStatus: Record<string, number>;
  revenueByDay: { date: string; amount: number }[];
  topProducts: { name: string; quantity: number }[];
  carrierPerformance: { carrier: string; shipments: number; onTimeRate: number }[];
}
