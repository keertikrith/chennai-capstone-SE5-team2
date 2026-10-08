/** What an AI assistant link may do. Reading is always included. */
export type AiScope = 'READ' | 'ALERTS' | 'TRADE';

export interface AiConnector {
  id: string;
  label: string;
  scopes: AiScope[];
  maxOrderValue: number;
  maxOrdersPerDay: number;
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED';
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
}

export interface CreateAiConnectorRequest {
  label: string;
  scopes: AiScope[];
  maxOrderValue?: number;
  maxOrdersPerDay?: number;
  expiresInDays?: number;
}

/** Answered once, when the link is made. The link holds the secret and is never shown again. */
export interface CreatedAiConnector {
  connector: AiConnector;
  url: string;
}

export interface AiAuditEntry {
  id: number;
  connectorId: string | null;
  connectorLabel: string | null;
  tool: string;
  outcome: string;
  detail: string | null;
  createdAt: string;
}
