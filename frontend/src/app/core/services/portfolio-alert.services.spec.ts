import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AlertService } from './alert.service';
import { PortfolioService } from './portfolio.service';

describe('AlertService and PortfolioService', () => {
  let http: HttpTestingController;
  const trade = API_ENDPOINTS.tradeApiBase;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('AlertService', () => {
    it('lists, creates, updates, toggles and removes alerts on the account path', () => {
      const alerts = TestBed.inject(AlertService);

      alerts.list(1).subscribe();
      expect(http.expectOne(`${trade}/api/v1/accounts/1/alerts`).request.method).toBe('GET');

      alerts.create(1, { symbol: 'AAPL', direction: 'GT', threshold: 235 }).subscribe();
      const create = http.expectOne(`${trade}/api/v1/accounts/1/alerts`);
      expect(create.request.method).toBe('POST');
      expect(create.request.body).toEqual({ symbol: 'AAPL', direction: 'GT', threshold: 235 });

      alerts.update(1, 7, { symbol: 'AAPL', direction: 'LTE', threshold: 210 }).subscribe();
      const update = http.expectOne(`${trade}/api/v1/accounts/1/alerts/7`);
      expect(update.request.method).toBe('PUT');
      expect(update.request.body).toEqual({ symbol: 'AAPL', direction: 'LTE', threshold: 210 });

      alerts.setEnabled(1, 7, false).subscribe();
      const toggle = http.expectOne(`${trade}/api/v1/accounts/1/alerts/7/enabled`);
      expect(toggle.request.method).toBe('PATCH');
      expect(toggle.request.body).toEqual({ enabled: false });

      alerts.remove(1, 7).subscribe();
      expect(http.expectOne(`${trade}/api/v1/accounts/1/alerts/7`).request.method).toBe('DELETE');
    });

    it('never sends an account id in the body: it comes from the path and the token', () => {
      TestBed.inject(AlertService).create(1, { symbol: 'AAPL', direction: 'LT', threshold: 1 }).subscribe();

      expect(Object.keys(http.expectOne(`${trade}/api/v1/accounts/1/alerts`).request.body)).not.toContain('accountId');
    });
  });

  describe('PortfolioService', () => {
    it('reads the three contract routes under the portfolio prefix', () => {
      const portfolio = TestBed.inject(PortfolioService);

      portfolio.getSummary(1).subscribe();
      expect(http.expectOne(`${trade}/api/v1/portfolio/1`).request.method).toBe('GET');

      portfolio.getPositions(1).subscribe();
      http.expectOne(`${trade}/api/v1/portfolio/1/positions`);

      portfolio.getPnl(1).subscribe();
      const bare = http.expectOne(`${trade}/api/v1/portfolio/1/pnl`);
      expect(bare.request.params.keys()).toEqual([]);
    });

    it('passes the date range and the breakdown flag only when given', () => {
      TestBed.inject(PortfolioService).getPnl(1, '2026-09-01', '2026-10-19', true).subscribe();

      const request = http.expectOne((r) => r.url === `${trade}/api/v1/portfolio/1/pnl`);
      expect(request.request.params.get('from')).toBe('2026-09-01');
      expect(request.request.params.get('to')).toBe('2026-10-19');
      expect(request.request.params.get('bySymbol')).toBe('true');
    });

    it('never calls a quote provider: every request goes to the platform', () => {
      const portfolio = TestBed.inject(PortfolioService);
      portfolio.getSummary(1).subscribe();
      portfolio.getPositions(1).subscribe();

      http.match(() => true).forEach((request) => expect(request.request.url.startsWith(trade)).toBe(true));
    });
  });
});
