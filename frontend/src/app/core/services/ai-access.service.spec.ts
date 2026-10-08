import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AiAccessService } from './ai-access.service';

describe('AiAccessService', () => {
  let http: HttpTestingController;
  let service: AiAccessService;
  const base = `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/7/mcp`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AiAccessService);
  });

  afterEach(() => http.verify());

  it('lists the account\'s links', () => {
    service.list(7).subscribe();
    const request = http.expectOne(`${base}/connectors`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a link with the chosen permissions and limits', () => {
    const body = { label: 'My Claude', scopes: ['READ', 'TRADE'] as const, maxOrderValue: 5000, maxOrdersPerDay: 3, expiresInDays: 30 };
    service.create(7, { ...body, scopes: [...body.scopes] }).subscribe();
    const request = http.expectOne(`${base}/connectors`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('revokes a link by id', () => {
    service.revoke(7, 'abc').subscribe();
    const request = http.expectOne(`${base}/connectors/abc`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('reads recent activity with a limit', () => {
    service.activity(7, 10).subscribe();
    const request = http.expectOne((r) => r.url === `${base}/audit`);
    expect(request.request.params.get('limit')).toBe('10');
    request.flush([]);
  });
});
