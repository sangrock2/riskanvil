/* eslint-disable */
// AUTO-GENERATED FILE. DO NOT EDIT MANUALLY.
// Source: docs/openapi/stock-ai.openapi.json

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export interface ApiClientConfig {
  baseUrl: string;
  getAccessToken?: () => string | null;
}

export type RegisterRequest = { email: string; password: string };
export type LoginRequest = { email: string; password: string };
export type AuthResponse = { accessToken?: string; refreshToken?: string; requires2FA?: boolean; pendingToken?: string; [key: string]: unknown };
export type AnalysisRequest = { ticker: string; market: 'US' | 'KR'; horizonDays: number; riskProfile: 'conservative' | 'moderate' | 'aggressive' };
export type AnalysisResponse = { runId?: number; result?: { [key: string]: unknown }; [key: string]: unknown };
export type CreatePortfolioRequest = { name: string; description?: string; targetReturn?: number; riskProfile: 'conservative' | 'moderate' | 'aggressive' };
export type PortfolioResponse = { id?: number; name?: string; [key: string]: unknown };
export type AddPositionRequest = { ticker: string; market: 'US' | 'KR'; quantity: number; entryPrice: number; entryDate?: string; notes?: string };
export type RiskDashboardResponse = { portfolioId?: number; metrics?: { [key: string]: unknown }; positions?: { [key: string]: unknown }[]; [key: string]: unknown };
export type EarningsCalendarResponse = { portfolioId?: number; items?: { [key: string]: unknown }[]; [key: string]: unknown };

function encodeQuery(query?: Record<string, unknown>): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null) continue;
    params.append(k, String(v));
  }
  const s = params.toString();
  return s ? `?${s}` : '';
}

export class StockAiApiClient {
  private readonly baseUrl: string;
  private readonly getAccessToken?: () => string | null;

  constructor(config: ApiClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.getAccessToken = config.getAccessToken;
  }

  private async request<T>(method: HttpMethod, path: string, options: { query?: Record<string, unknown>; body?: unknown; auth?: boolean; init?: RequestInit } = {}): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (options.auth && this.getAccessToken) {
      const token = this.getAccessToken();
      if (token) headers.Authorization = `Bearer ${token}`;
    }
    const res = await fetch(`${this.baseUrl}${path}${encodeQuery(options.query)}`, {
      ...(options.init ?? {}),
      method,
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`${method} ${path} failed: ${res.status} ${text}`);
    }
    const text = await res.text();
    return (text ? JSON.parse(text) : {}) as T;
  }

  async post_api_auth_register(body: RegisterRequest, init?: RequestInit): Promise<AuthResponse> {
    return this.request<AuthResponse>('POST', `/api/auth/register`, { query: undefined, body: body, auth: false, init });
  }

  async post_api_auth_login(body: LoginRequest, init?: RequestInit): Promise<AuthResponse> {
    return this.request<AuthResponse>('POST', `/api/auth/login`, { query: undefined, body: body, auth: false, init });
  }

  async post_api_analysis(body: AnalysisRequest, init?: RequestInit): Promise<AnalysisResponse> {
    return this.request<AnalysisResponse>('POST', `/api/analysis`, { query: undefined, body: body, auth: true, init });
  }

  async get_api_analysis_id(path: { id: number }, init?: RequestInit): Promise<{ [key: string]: unknown }> {
    return this.request<{ [key: string]: unknown }>('GET', `/api/analysis/${path.id}`, { query: undefined, body: undefined, auth: true, init });
  }

  async post_api_portfolio(body: CreatePortfolioRequest, init?: RequestInit): Promise<PortfolioResponse> {
    return this.request<PortfolioResponse>('POST', `/api/portfolio`, { query: undefined, body: body, auth: true, init });
  }

  async post_api_portfolio_id_position(path: { id: number }, body: AddPositionRequest, init?: RequestInit): Promise<{ [key: string]: number }> {
    return this.request<{ [key: string]: number }>('POST', `/api/portfolio/${path.id}/position`, { query: undefined, body: body, auth: true, init });
  }

  async get_api_portfolio_id_risk_dashboard(path: { id: number }, query?: { lookbackDays?: number }, init?: RequestInit): Promise<RiskDashboardResponse> {
    return this.request<RiskDashboardResponse>('GET', `/api/portfolio/${path.id}/risk-dashboard`, { query: query, body: undefined, auth: true, init });
  }

  async get_api_portfolio_id_earnings_calendar(path: { id: number }, query?: { daysAhead?: number }, init?: RequestInit): Promise<EarningsCalendarResponse> {
    return this.request<EarningsCalendarResponse>('GET', `/api/portfolio/${path.id}/earnings-calendar`, { query: query, body: undefined, auth: true, init });
  }

}
