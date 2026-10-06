import { AlertChannel, ChannelSource } from './preferences.model';

export type NotificationKind = 'ORDER_FILLED' | 'ORDER_REJECTED' | 'ORDER_CANCELLED' | 'PRICE_ALERT';

/** QUEUED waiting to be sent; HELD no usable contact for the chosen channel; SENT handed to the channel; FAILED gave up. */
export type NotificationStatus = 'QUEUED' | 'HELD' | 'SENT' | 'FAILED';

export interface NotificationItem {
  id: number;
  kind: NotificationKind;
  subject: string;
  body: string;
  status: NotificationStatus;
  /** NO_CONTACT, PREFERENCES_UNAVAILABLE, PROVIDER_ERROR or STUB_DELIVERY. */
  statusReason: string | null;
  /** The channel the platform resolved when it sent (or tried to send) this message. Null while it is waiting. */
  channel: AlertChannel | null;
  channelSource: ChannelSource | null;
  createdAt: string;
  sentAt: string | null;
  read: boolean;
}
