BEGIN;


-- CLIENTS

INSERT INTO clients (id, client_ref, name, email, phone, date_of_birth, gender, status) VALUES
(1,  'C100000001', 'John Doe',        'john.doe@example.com',        '9876543210', '1985-04-12', 'MALE',   'ACTIVE'),
(2,  'C100000002', 'Jane Smith',      'jane.smith@example.com',      '9876543211', '1990-07-23', 'FEMALE', 'ACTIVE'),
(3,  'C100000003', 'Alex Johnson',    'alex.johnson@example.com',    NULL,         '1978-11-02', 'OTHER',  'DORMANT'),
(4,  'C100000004', 'Priya Singh',     'priya.singh@example.com',     '9123456780', '1995-01-30', 'FEMALE', 'ACTIVE'),
(5,  'C100000005', 'Robert Brown',    'robert.brown@example.com',    '9123456781', '1960-09-15', 'MALE',   'CLOSED'),
(6,  'C100000006', 'Emily Davis',     'emily.davis@example.com',     NULL,         NULL,         NULL,     'ACTIVE'),
(7,  'C100000007', 'Wei Chen',        'wei.chen@example.com',        '9988776655', '1988-03-19', 'MALE',   'ACTIVE'),
(8,  'C100000008', 'Fatima Ali',      'fatima.ali@example.com',      '9988776656', '1992-06-08', 'FEMALE', 'DORMANT'),
(9,  'C100000009', 'Carlos Garcia',   'carlos.garcia@example.com',   '9988776657', '1975-12-25', 'MALE',   'ACTIVE'),
(10, 'C100000010', 'Sara Kim',        'sara.kim@example.com',        '9988776658', '1983-02-14', 'FEMALE', 'CLOSED'),
(11, 'C100000011', 'Liam O''Brien',   'liam.obrien@example.com',     '9988776659', '1991-08-05', 'MALE',   'ACTIVE'),
(12, 'C100000012', 'Olga Petrova',    'olga.petrova@example.com',    '9988776660', '1987-10-11', 'FEMALE', 'ACTIVE'),
(13, 'C100000013', 'Hiro Tanaka',     'hiro.tanaka@example.com',     '9988776661', '1993-05-27', 'MALE',   'ACTIVE'),
(14, 'C100000014', 'Nina Patel',      'nina.patel@example.com',      '9988776662', '1980-01-09', 'FEMALE', 'DORMANT'),
(15, 'C100000015', 'Minimal Fields',  'minimal.fields@example.com',  NULL,         NULL,         NULL,     'ACTIVE');


-- ACCOUNTS

INSERT INTO accounts (id, account_ref, client_id, account_type, currency, cash_balance, status, version, created_at, suspended_at, closed_at, updated_at) VALUES
(1,  'ACC0000001', 1,  'US_EQUITY', 'USD', 15000.50, 'ACTIVE',    3, '2023-01-10 09:00:00+00', NULL,                     NULL,                     '2024-06-01 10:00:00+00'),
(2,  'ACC0000002', 2,  'FOREX',  'EUR', 2000.00,  'ACTIVE',    2, '2023-03-01 09:00:00+00', NULL,                     NULL,                     '2024-05-20 08:30:00+00'),
(3,  'ACC0000003', 3,  'INDIAN_EQUITY', 'INR', 0.00,     'SUSPENDED', 4, '2022-11-05 09:00:00+00', '2024-02-01 12:00:00+00', NULL,                     '2024-02-01 12:00:00+00'),
(4,  'ACC0000004', 4,  'US_EQUITY', 'USD', 10000.00, 'ACTIVE',    5, '2023-04-18 09:00:00+00', NULL,                     NULL,                     '2024-07-12 14:00:00+00'),
(5,  'ACC0000005', 5,  'US_EQUITY', 'USD', 0.00,     'CLOSED',    6, '2021-06-01 09:00:00+00', NULL,                     '2023-12-31 17:00:00+00', '2023-12-31 17:00:00+00'),
(6,  'ACC0000006', 6,  'CRYPTO', 'EUR', 2222250.75,   'ACTIVE',    2, '2023-05-22 09:00:00+00', NULL,                     NULL,                     NULL),
(7,  'ACC0000007', 7,  'FOREX',  'USD', 5000.00,  'ACTIVE',    3, '2023-06-30 09:00:00+00', NULL,                     NULL,                     '2024-03-11 09:20:00+00'),
(8,  'ACC0000008', 8,  'INDIAN_EQUITY', 'INR', 1200.00,  'SUSPENDED', 2, '2022-09-14 09:00:00+00', '2024-01-15 11:00:00+00', NULL,                     '2024-01-15 11:00:00+00'),
(9,  'ACC0000009', 9,  'CRYPTO', 'GBP', 13244389.99,    'ACTIVE',    1, '2024-01-02 09:00:00+00', NULL,                     NULL,                     NULL),
(10, 'ACC0000010', 10, 'US_EQUITY', 'USD', 0.00,     'CLOSED',    5, '2020-03-01 09:00:00+00', NULL,                     '2022-08-19 16:45:00+00', '2022-08-19 16:45:00+00'),
(11, 'ACC0000011', 11, 'US_EQUITY', 'USD', 30000.00, 'ACTIVE',    4, '2023-07-07 09:00:00+00', NULL,                     NULL,                     '2024-04-02 13:10:00+00'),
(12, 'ACC0000012', 12, 'FOREX',  'GBP', 750.00,   'ACTIVE',    1, '2023-08-19 09:00:00+00', NULL,                     NULL,                     NULL),
(13, 'ACC0000013', 13, 'CRYPTO', 'USD', 100000.00,'ACTIVE',    2, '2023-09-25 09:00:00+00', NULL,                     NULL,                     '2024-08-01 07:00:00+00'),
(14, 'ACC0000014', 14, 'INDIAN_EQUITY', 'INR', 4000.00,  'SUSPENDED', 3, '2022-12-12 09:00:00+00', '2024-03-05 10:15:00+00', NULL,                     '2024-03-05 10:15:00+00'),
(15, 'ACC0000015', 15, 'US_EQUITY', 'USD', 0.00,     'ACTIVE',    0, '2024-08-20 09:00:00+00', NULL,                     NULL,                     NULL);


-- ACCOUNT PREFERENCES 

INSERT INTO account_preferences (account_id, risk_profile, investment_goals, preferred_sectors) VALUES
(1,  'MODERATE',     'Long-term wealth growth',           ARRAY['Technology','Healthcare']),
(2,  'CONSERVATIVE', 'Capital preservation via FX carry',  ARRAY['Currency']),
(3,  'MODERATE',     'Retirement planning',                ARRAY['Energy','Financials']),
(4,  'AGGRESSIVE',   'Aggressive growth for early retirement', ARRAY['Technology']),
(6,  'AGGRESSIVE',   'Diversified crypto portfolio',       ARRAY['Crypto','DeFi']),
(7,  'MODERATE',     'Steady FX income',                   ARRAY['Currency']),
(8,  'CONSERVATIVE', 'Low-volatility Indian large caps',   ARRAY['IT','Banking']),
(11, 'MODERATE',     'Balanced international equity',      ARRAY['Technology','Consumer']),
(12, 'CONSERVATIVE', 'FX hedging for overseas expenses',   ARRAY['Currency']),
(13, 'AGGRESSIVE',   'Crypto speculation',                 ARRAY['Crypto']),
(14, 'CONSERVATIVE', 'Dividend income',                    ARRAY['Banking','Utilities']);


-- PORTFOLIOS 

INSERT INTO portfolios (id, account_id, name, current_value, created_at, updated_at) VALUES
(1,  1,  'John''s US Equity Portfolio',      12000.00, '2023-01-10 09:05:00+00', '2024-06-01 10:00:00+00'),
(2,  2,  'Jane''s FX Portfolio',             18700.00, '2023-03-01 09:05:00+00', '2024-05-20 08:30:00+00'),
(3,  3,  'Alex''s Indian Equity Portfolio',  245000.00,'2022-11-05 09:05:00+00', '2024-02-01 12:00:00+00'),
(4,  4,  'Priya''s Growth Portfolio',        41500.00, '2023-04-18 09:05:00+00', '2024-07-12 14:00:00+00'),
(5,  5,  'Robert''s Legacy Portfolio',       0.00,     '2021-06-01 09:05:00+00', '2023-12-31 17:00:00+00'),
(6,  6,  'Emily''s Crypto Basket',           20550.00, '2023-05-22 09:05:00+00', '2024-05-01 10:00:00+00'),
(7,  7,  'Wei''s FX Portfolio',              14820000.00,'2023-06-30 09:05:00+00','2024-03-11 09:20:00+00'),
(8,  8,  'Fatima''s Indian Equity Portfolio',34000.00, '2022-09-14 09:05:00+00', '2024-01-15 11:00:00+00'),
(9,  9,  'Carlos''s Crypto Portfolio',       0.00,     '2024-01-02 09:05:00+00', '2024-01-02 09:05:00+00'),
(10, 10, 'Sara''s Closed Portfolio',         0.00,     '2020-03-01 09:05:00+00', '2022-08-19 16:45:00+00'),
(11, 11, 'Liam''s Euro Equity Portfolio',    12200.00, '2023-07-07 09:05:00+00', '2024-04-02 13:10:00+00'),
(12, 12, 'Olga''s FX Portfolio',             2180.00,  '2023-08-19 09:05:00+00', '2023-08-19 09:05:00+00'),
(13, 13, 'Hiro''s Crypto Portfolio',         6300000.00,'2023-09-25 09:05:00+00','2024-08-01 07:00:00+00'),
(14, 14, 'Nina''s Mixed Indian Portfolio',   9270.00,  '2022-12-12 09:05:00+00', '2024-03-05 10:15:00+00');


-- INSTRUMENTS 

INSERT INTO instruments
    (id, symbol, exchange, name, asset_class, currency, is_tradable, stopped_at)
VALUES
    (1,  'AAPL',        'NYSE', 'Apple Inc.',                'US_EQUITY',     'USD', TRUE,  NULL),
    (2,  'MSFT',        'NYSE', 'Microsoft Corporation',     'US_EQUITY',     'USD', TRUE,  NULL),
    (3,  'GOOGL',       'NYSE', 'Alphabet Inc.',              'US_EQUITY',     'USD', TRUE,  NULL),
    (4,  'TSLA',        'NYSE', 'Tesla Inc.',                 'US_EQUITY',     'USD', TRUE,  NULL),
    (5,  'RELIANCE.NS', 'NSE',  'Reliance Industries Ltd.',  'INDIAN_EQUITY', 'INR', TRUE,  NULL),
    (6,  'TCS.NS',      'NSE',  'Tata Consultancy Services', 'INDIAN_EQUITY', 'INR', TRUE,  NULL),
    (7,  'INFY.NS',     'NSE',  'Infosys Ltd.',              'INDIAN_EQUITY', 'INR', TRUE,  NULL),
    (8,  'FX:EURUSD',   'FX',   'Euro / US Dollar',          'FX',            'USD', TRUE,  NULL),
    (9,  'FX:GBPUSD',   'FX',   'British Pound / US Dollar', 'FX',            'USD', TRUE,  NULL),
    (10, 'FX:USDJPY',   'FX',   'US Dollar / Japanese Yen',  'FX',            'JPY', TRUE,  NULL),
    (11, 'X:BTC-USD',   'CRYPTO', 'Bitcoin',                 'CRYPTO',        'USD', TRUE,  NULL),
    (12, 'X:ETH-USD',   'CRYPTO', 'Ethereum',                'CRYPTO',        'USD', TRUE,  NULL),
    (13, 'X:SOL-USD',   'CRYPTO', 'Solana',                  'CRYPTO',        'USD', TRUE,  NULL),
    (14, 'WCOM',        'NYSE', 'WorldCom Inc. (Delisted)',  'US_EQUITY',     'USD', FALSE, '2024-01-01 00:00:00+00'),
    (15, 'YESBANK.NS',  'NSE',  'Yes Bank Ltd.',             'INDIAN_EQUITY', 'INR', TRUE,  NULL),
    (16, 'X:BTC-EUR',   'CRYPTO', 'Bitcoin',                 'CRYPTO',        'EUR', TRUE,  NULL),
    (17, 'X:BTC-GBP',   'CRYPTO', 'Bitcoin',                 'CRYPTO',        'GBP', TRUE,  NULL);



-- PORTFOLIO HOLDINGS

INSERT INTO portfolio_holdings (portfolio_id, instrument_id, quantity, avg_price, updated_at) VALUES
(1,  1,  50,  170.25,     '2024-06-01 10:00:00+00'),
(1,  2,  20,  310.10,     '2024-05-15 09:00:00+00'),
(2,  8,  10000,1.08,      '2024-05-20 08:30:00+00'),
(2,  9,  5000, 1.27,      '2024-05-20 08:30:00+00'),
(3,  5,  100, 2450.00,    '2024-01-30 09:00:00+00'),
(4,  1,  200, 165.00,     '2024-07-12 14:00:00+00'),
(4,  4,  30,  250.00,     '2024-06-20 09:00:00+00'),
(5,  1,  0,   170.00,     '2023-12-31 17:00:00+00'),  -- fully sold out before closure
(6,  12, 5,   2200.00,    '2024-05-01 10:00:00+00'),
(6,  13, 100, 95.50,      '2024-05-01 10:00:00+00'),
(7,  10, 1000000, 148.20, '2024-03-11 09:20:00+00'),
(8,  6,  10,  3400.00,    '2024-01-15 11:00:00+00'),
(10, 1,  0,   150.00,     '2022-08-19 16:45:00+00'), -- closed out before account closure
(11, 2,  40,  305.00,     '2024-04-02 13:10:00+00'),
(12, 8,  2000, 1.09,      '2023-08-19 09:05:00+00'),
(13, 11, 1,   6300000.00, '2024-08-01 07:00:00+00'),
(14, 15, 500, 18.50,      '2024-02-05 10:00:00+00'),
(14, 14, 200, 0.10,       '2023-12-15 10:00:00+00'); -- legacy holding, instrument now delisted
-- portfolio_id 10 -an empty portfolio.


-- ORDERS

INSERT INTO orders
(id, account_id, instrument_id, order_type, quantity, limit_price, status, reason, created_at, terminal_at)
VALUES
('00000000-0000-0000-0000-000000000001', 1,  1,  'BUY',  50,      170.00,   'FILLED',    NULL,                    '2024-06-01 09:50:00+00', '2024-06-01 10:00:00+00'),
('00000000-0000-0000-0000-000000000002', 1,  2,  'BUY',  20,      NULL,     'FILLED',    NULL,                    '2024-05-15 08:50:00+00', '2024-05-15 09:00:00+00'),
('00000000-0000-0000-0000-000000000004', 2,  8,  'BUY',  10000,   1.08,     'FILLED',    NULL,                    '2024-05-20 08:20:00+00', '2024-05-20 08:30:00+00'),
('00000000-0000-0000-0000-000000000005', 2,  9,  'SELL', 2000,    1.27,     'NEW',       NULL,                    '2024-08-01 11:00:00+00', NULL),
('00000000-0000-0000-0000-000000000006', 3,  5,  'BUY',  100,     2450.00,  'FILLED',    NULL,                    '2024-01-30 08:50:00+00', '2024-01-30 09:00:00+00'),
('00000000-0000-0000-0000-000000000007', 4,  1,  'BUY',  200,     NULL,     'FILLED',    NULL,                    '2024-07-12 13:50:00+00', '2024-07-12 14:00:00+00'),
('00000000-0000-0000-0000-000000000008', 4,  4,  'BUY',  30,      255.00,   'CANCELLED', 'CANCELLED_BY_CUSTOMER', '2024-06-19 09:00:00+00', '2024-06-20 09:00:00+00'),
('00000000-0000-0000-0000-000000000009', 5,  1,  'SELL', 50,      175.00,   'FILLED',    NULL,                    '2023-12-31 16:50:00+00', '2023-12-31 17:00:00+00'),
('00000000-0000-0000-0000-000000000010', 6,  12, 'BUY',  5,       NULL,     'FILLED',    NULL,                    '2024-05-01 09:50:00+00', '2024-05-01 10:00:00+00'),
('00000000-0000-0000-0000-000000000011', 6,  13, 'BUY',  100,     96.00,    'REJECTED',  'INSUFFICIENT_FUNDS',     '2024-08-02 10:00:00+00', '2024-08-02 10:00:05+00'),
('00000000-0000-0000-0000-000000000012', 7,  10, 'BUY',  1000000, 148.00,  'FILLED',    NULL,                    '2024-03-11 09:10:00+00', '2024-03-11 09:20:00+00'),
('00000000-0000-0000-0000-000000000013', 8,  6,  'BUY',  10,      3400.00,  'FILLED',    NULL,                    '2024-01-15 10:50:00+00', '2024-01-15 11:00:00+00'),
('00000000-0000-0000-0000-000000000014', 9,  11, 'BUY',  1,       43000.00, 'NEW',       NULL,                    '2024-08-05 12:00:00+00', NULL),
('00000000-0000-0000-0000-000000000015', 11, 2,  'BUY',  40,      NULL,     'FILLED',    NULL,                    '2024-04-02 13:00:00+00', '2024-04-02 13:10:00+00'),
('00000000-0000-0000-0000-000000000016', 12, 8,  'BUY',  2000,    1.09,     'FILLED',    NULL,                    '2023-08-19 09:00:00+00', '2023-08-19 09:05:00+00'),
('00000000-0000-0000-0000-000000000017', 13, 11, 'BUY',  1,       NULL,     'FILLED',    NULL,                    '2024-08-01 06:50:00+00', '2024-08-01 07:00:00+00'),
('00000000-0000-0000-0000-000000000018', 14, 15, 'BUY',  500,     18.50,    'FILLED',    NULL,                    '2024-02-05 09:50:00+00', '2024-02-05 10:00:00+00'),
('00000000-0000-0000-0000-000000000019', 14, 14, 'BUY',  200,     0.10,     'FILLED',    NULL,                    '2023-12-15 09:50:00+00', '2023-12-15 10:00:00+00'),
('00000000-0000-0000-0000-000000000023', 15, 1,  'BUY',  10,      170.00,   'NEW',       NULL,                    '2024-08-20 09:10:00+00', NULL),
('00000000-0000-0000-0000-000000000025', 8,  6,  'SELL', 3,       3450.00,  'REJECTED',  'INSUFFICIENT_HOLDINGS',  '2024-08-03 10:00:00+00', '2024-08-03 10:00:04+00');



-- TRANSACTIONS

INSERT INTO transactions
(order_id, quantity, executed_price, status, reason, executed_at)
VALUES
('00000000-0000-0000-0000-000000000001', 50,      169.95,    'EXECUTED', NULL,              '2024-06-01 10:00:00+00'),
('00000000-0000-0000-0000-000000000002', 20,      310.05,    'EXECUTED', NULL,              '2024-05-15 09:00:00+00'),
('00000000-0000-0000-0000-000000000004', 10000,   1.08,      'EXECUTED', NULL,              '2024-05-20 08:30:00+00'),
('00000000-0000-0000-0000-000000000006', 100,     2445.50,   'EXECUTED', NULL,              '2024-01-30 09:00:00+00'),
('00000000-0000-0000-0000-000000000007', 200,     164.80,    'EXECUTED', NULL,              '2024-07-12 14:00:00+00'),
('00000000-0000-0000-0000-000000000009', 50,      174.50,    'EXECUTED', NULL,              '2023-12-31 17:00:00+00'),
('00000000-0000-0000-0000-000000000010', 5,       2195.00,   'EXECUTED', NULL,              '2024-05-01 10:00:00+00'),
('00000000-0000-0000-0000-000000000012', 1000000, 148.15,   'EXECUTED', NULL,              '2024-03-11 09:20:00+00'),
('00000000-0000-0000-0000-000000000013', 10,      3395.00,   'EXECUTED', NULL,              '2024-01-15 11:00:00+00'),
('00000000-0000-0000-0000-000000000015', 40,      304.90,    'EXECUTED', NULL,              '2024-04-02 13:10:00+00'),
('00000000-0000-0000-0000-000000000016', 2000,    1.09,      'EXECUTED', NULL,              '2023-08-19 09:05:00+00'),
('00000000-0000-0000-0000-000000000017', 1,       6300000.00, 'FAILED',  'INSUFFICIENT_FUNDS', '2024-08-01 07:00:05+00'),
('00000000-0000-0000-0000-000000000018', 500,     18.45,     'EXECUTED', NULL,              '2024-02-05 10:00:00+00'),
('00000000-0000-0000-0000-000000000019', 200,     0.10,      'EXECUTED', NULL,              '2023-12-15 10:00:00+00');


INSERT INTO watchlist (id, account_id, name, created_at, updated_at)
VALUES
(1, 1, 'My Watchlist', '2024-01-10 09:00:00+00', '2024-06-01 10:00:00+00'),
(2, 2, 'My Watchlist', '2024-03-01 09:00:00+00', '2024-05-20 08:30:00+00'),
(3, 4, 'My Watchlist', '2024-04-18 09:00:00+00', '2024-07-12 14:00:00+00'),
(4, 6, 'My Watchlist', '2024-05-22 09:00:00+00', '2024-05-01 10:00:00+00'),
(5, 11, 'My Watchlist', '2024-07-07 09:00:00+00', '2024-04-02 13:10:00+00'),
(6, 12, 'My Watchlist', '2024-08-19 09:00:00+00', '2024-08-19 09:05:00+00');


INSERT INTO watchlist_items (watchlist_id, instrument_id, added_at)
VALUES
-- Account 1 (watchlist_id = 1): AAPL, MSFT, GOOGL
(1, 1, '2024-01-10 09:00:00+00'),
(1, 2, '2024-01-15 10:00:00+00'),
(1, 3, '2024-02-01 09:00:00+00'),

-- Account 2 (watchlist_id = 2): EURUSD, GBPUSD, USDJPY
(2, 8, '2024-03-01 09:00:00+00'),
(2, 9, '2024-03-10 10:00:00+00'),
(2, 10, '2024-03-20 09:00:00+00'),

-- Account 4 (watchlist_id = 3): AAPL, TSLA
(3, 1, '2024-04-18 09:00:00+00'),
(3, 4, '2024-05-01 10:00:00+00'),

-- Account 6 (watchlist_id = 4): ETH, SOL, BTC
(4, 12, '2024-05-22 09:00:00+00'),
(4, 13, '2024-05-25 10:00:00+00'),
(4, 11, '2024-06-01 09:00:00+00'),

-- Account 11 (watchlist_id = 5): MSFT, AAPL
(5, 2, '2024-07-07 09:00:00+00'),
(5, 1, '2024-07-15 10:00:00+00'),

-- Account 12 (watchlist_id = 6): EURUSD, GBPUSD
(6, 8, '2024-08-19 09:00:00+00'),
(6, 9, '2024-08-20 10:00:00+00');

COMMIT;