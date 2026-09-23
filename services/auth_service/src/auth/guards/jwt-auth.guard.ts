import {
    CanActivate,
    ExecutionContext,
    Injectable,
    UnauthorizedException,
} from '@nestjs/common';

import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { Request } from 'express';

import { JwtPayload } from '../types/jwt-payload';

@Injectable()
export class JwtAuthGuard implements CanActivate {

    constructor(
        private readonly jwtService: JwtService,
        private readonly configService: ConfigService,
    ) {}

    canActivate(
        context: ExecutionContext,
    ): boolean {

        const request =
            context.switchToHttp().getRequest<Request>();

        const authorization =
            request.headers.authorization;

        /*
         * No Authorization header
         */
        if (!authorization) {
            throw this.unauthorized();
        }

        /*
         * Expected:
         *
         * Authorization: Bearer <JWT>
         */
        const parts =
            authorization.split(' ');

        if (
            parts.length !== 2 ||
            parts[0] !== 'Bearer' ||
            !parts[1]
        ) {
            throw this.unauthorized();
        }

        const token = parts[1];

        try {

            /*
             * IMPORTANT:
             *
             * verify() checks:
             *
             * 1. Signature
             * 2. Expiration (exp)
             * 3. Algorithm
             *
             * Only after this succeeds do we trust the payload.
             */
            const payload =
                this.jwtService.verify<JwtPayload>(
                    token,
                    {
                        secret:
                            this.configService.getOrThrow<string>(
                                'JWT_SECRET',
                            ),

                        algorithms: ['HS256'],
                    },
                );

            /*
             * Token is verified.
             *
             * It is now safe to attach the claims
             * to the request.
             */
            request.user = payload;

            return true;

        } catch {
            /*
             * Covers:
             *
             * - expired token
             * - invalid signature
             * - malformed JWT
             * - invalid algorithm
             * - invalid token
             */
            throw this.unauthorized();
        }
    }

    private unauthorized(): UnauthorizedException {

        return new UnauthorizedException({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
        });
    }
}