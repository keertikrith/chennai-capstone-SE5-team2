export type AlertChannel = 'EMAIL' | 'SMS' | 'PUSH';

export type ChannelSource = 'STORED' | 'DEFAULT';

/** One channel the customer could pick, and whether their profile can serve it. */
export interface ChannelOption {
  channel: AlertChannel;
  available: boolean;
  /** A masked destination ("j***@example.com"), or why the channel is unavailable. */
  hint: string;
}

/**
 * The customer's default account and alert channel. The contact detail is masked by the server: the
 * full address or number never reaches the browser, and there is no field to send one back.
 */
export interface PreferencesResponse {
  accountId: number;
  defaultAccountId: number;
  alertChannel: AlertChannel;
  contactDetail: string | null;
  source: ChannelSource;
  channels: ChannelOption[];
  updatedAt: string | null;
}

export interface UpdatePreferencesRequest {
  defaultAccountId: number;
  alertChannel: AlertChannel;
}
