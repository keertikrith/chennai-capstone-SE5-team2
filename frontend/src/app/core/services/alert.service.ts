import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { CreateAlertRequest, PriceAlert } from '../models/alert.model';

/** Price alerts. A fired alert is delivered by the platform through notifications; nothing here sends a message. */
@Injectable({ providedIn: 'root' })
export class AlertService {
  private readonly http = inject(HttpClient);

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/alerts`;
  }

  list(accountId: number): Observable<PriceAlert[]> {
    return this.http.get<PriceAlert[]>(this.base(accountId));
  }

  create(accountId: number, request: CreateAlertRequest): Observable<PriceAlert> {
    return this.http.post<PriceAlert>(this.base(accountId), request);
  }

  /** Cancels an active alert, or removes a finished one. */
  remove(accountId: number, alertId: number): Observable<PriceAlert> {
    return this.http.delete<PriceAlert>(`${this.base(accountId)}/${alertId}`);
  }
}
