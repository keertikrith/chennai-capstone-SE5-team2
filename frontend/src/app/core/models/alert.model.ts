export type AlertDirection = 'ABOVE' | 'BELOW';

export type AlertStatus = 'ACTIVE' | 'TRIGGERED' | 'CANCELLED';

/** What became of a fired alert's hand-over to notifications. */
export type DeliveryState = 'NONE' | 'PENDING_HANDOVER' | 'HANDED_OVER' | 'HANDOVER_FAILED';

export interface PriceAlert {
  id: number;
  symbol: string;
  direction: AlertDirection;
  threshold: number;
  status: AlertStatus;
  deliveryState: DeliveryState;
  triggeredPrice: number | null;
  triggeredAt: string | null;
  createdAt: string;
}

export interface CreateAlertRequest {
  symbol: string;
  direction: AlertDirection;
  threshold: number;
}

/** The server's cap per account; shown as a hint, enforced by the server. */
export const MAX_ALERTS_PER_ACCOUNT = 25;
