export interface JwtPayload {
    sub: string;
    accountId: number;
    roles: string[];
    iat: number;
    exp: number;
    iss: string;
}