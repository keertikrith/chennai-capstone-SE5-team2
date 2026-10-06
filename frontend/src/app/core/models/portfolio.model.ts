/** contracts/portfolio-api.yaml PortfolioSummary. Totals are in `baseCurrency`. */
export interface PortfolioSummary {
  accountId: number;
  baseCurrency: string;
  cashBalance: number;
  marketValue: number;
  costBasis: number;
  unrealisedPnl: number;
  unrealisedPnlPercent: number | null;
  realisedPnl: number;
  totalValue: number;
  positionCount: number;
  /** True when at least one holding could not be priced; the totals then understate the portfolio. */
  partial: boolean;
  asOf: string;
}

/** contracts/portfolio-api.yaml PricedPosition. Amounts are in the instrument's own `currency`. */
export interface PricedPosition {
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
  costBasis: number;
  lastPrice: number | null;
  marketValue: number | null;
  unrealisedPnl: number | null;
  unrealisedPnlPercent: number | null;
  currency: string;
  priceAsOf: string | null;
  /** True when the price is past its freshness window or could not be fetched. Never hide it. */
  stale: boolean;
}

export interface SymbolPnl {
  symbol: string;
  realisedPnl: number;
  unrealisedPnl: number;
  totalPnl: number;
}

export interface PnlResponse {
  accountId: number;
  baseCurrency: string;
  from: string | null;
  to: string | null;
  realisedPnl: number;
  unrealisedPnl: number;
  totalPnl: number;
  bySymbol?: SymbolPnl[];
  asOf: string;
}
