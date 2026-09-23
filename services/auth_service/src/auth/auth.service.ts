import {
    Injectable,
    UnauthorizedException,
    Logger
} from '@nestjs/common';


import * as argon2 from 'argon2';


import { AuthUserRepository }
    from './repository/auth-user.repository';


import { RefreshTokenService }
    from './services/refresh-token.service';


import { JwtTokenService }
    from './services/jwt-token.service';



@Injectable()
export class AuthService {


    private readonly logger =
        new Logger(AuthService.name);


    constructor(

        private userRepo: AuthUserRepository,

        private jwtService: JwtTokenService,

        private refreshService: RefreshTokenService

    ) { }




    async login(username: string, password: string) {

        this.logger.log(
            `[AUTH_EVENT] login_attempt_started username=${username}`
        );


        const user =
            await this.userRepo.findByUsername(username);



        if (!user) {

            this.logger.warn(
                `[AUTH_EVENT] login_rejected_user_not_found username=${username}`
            );

            throw new UnauthorizedException({

                errorCode: "AUTH-401",

                message: "Unauthorised"

            });

        }



        const valid =
            await argon2.verify(
                user.passwordHash,
                password
            );



        if (!valid) {

            this.logger.warn(
                `[AUTH_EVENT] login_rejected_invalid_password username=${username} userId=${user.id}`
            );

            throw new UnauthorizedException({

                errorCode: "AUTH-401",

                message: "Unauthorised"

            });

        }




        const accessToken =
            this.jwtService.generateAccessToken(

                user.id,

                user.accountId,

                user.roles

            );



        const refreshToken =
            await this.refreshService.create(
                user.id
            );


        this.logger.log(
            `[AUTH_EVENT] login_succeeded userId=${user.id} accountId=${user.accountId} roles=${user.roles.join(',')}`
        );

        return {

            accessToken,

            refreshToken,

            tokenType: "Bearer",

            expiresIn: 900

        };


    }


}