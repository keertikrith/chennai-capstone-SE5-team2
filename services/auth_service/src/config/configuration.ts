export interface DatabaseConfig {
  host: string;
  port: number;
  database: string;
  user: string;
  password?: string;
  schema?: string;
}

export interface AuthConfig {
  jwtSecret: string;
  jwtIssuer: string;
  jwtExpiresIn: number;
}

export interface AppConfig {
  port: number;
  environment: string;
  database: DatabaseConfig;
  auth: AuthConfig;
}

export const configuration = (): AppConfig => ({
  port: parseInt(process.env.PORT || '3000', 10),
  environment: process.env.NODE_ENV || 'development',
  database: {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    database: process.env.DB_NAME || 'capstone_db',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    schema: process.env.DB_SCHEMA || 'auth',
  },
  auth: {
    jwtSecret: process.env.JWT_SECRET || 'dev-jwt-secret-at-least-32-chars-long-12345678',
    jwtIssuer: process.env.JWT_ISSUER || 'auth-service',
    jwtExpiresIn: 900, // 15 minutes in seconds
  },
});

