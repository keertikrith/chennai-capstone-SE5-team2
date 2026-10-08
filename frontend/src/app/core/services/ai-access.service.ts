import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AiAuditEntry, AiConnector, CreateAiConnectorRequest, CreatedAiConnector } from '../models/ai-access.model';

/** The links that let an AI assistant (Claude, ChatGPT) use the customer's account, and what they have done. */
@Injectable({ providedIn: 'root' })
export class AiAccessService {
  private readonly http = inject(HttpClient);

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/mcp`;
  }

  list(accountId: number): Observable<AiConnector[]> {
    return this.http.get<AiConnector[]>(`${this.base(accountId)}/connectors`);
  }

  create(accountId: number, request: CreateAiConnectorRequest): Observable<CreatedAiConnector> {
    return this.http.post<CreatedAiConnector>(`${this.base(accountId)}/connectors`, request);
  }

  /** The link stops working on the assistant's very next call. */
  revoke(accountId: number, connectorId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(accountId)}/connectors/${connectorId}`);
  }

  activity(accountId: number, limit = 20): Observable<AiAuditEntry[]> {
    return this.http.get<AiAuditEntry[]>(`${this.base(accountId)}/audit`, { params: { limit } });
  }
}
