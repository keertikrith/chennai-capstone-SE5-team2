import { Injectable } from '@nestjs/common';

import {
    JwtService
} from '@nestjs/jwt';



@Injectable()
export class JwtTokenService {


    constructor(
        private jwtService: JwtService
    ) { }



    generateAccessToken(
        userId: string,
        accountId: number,
        roles: string[]
    ) {


        return this.jwtService.sign({

            sub: userId,
            accountId: Number(accountId),
            roles: roles
        },
            {
                issuer: "auth-service",
                expiresIn: "15m",
                algorithm: "HS256"

            });


    }


}