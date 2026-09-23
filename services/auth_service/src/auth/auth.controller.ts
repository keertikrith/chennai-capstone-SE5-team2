import {
    Controller,
    Post,
    Body,
    Logger,
    UseGuards,
    Get
} from '@nestjs/common';


import {
    AuthService
} from './auth.service';


import {
    LoginRequestDto
} from './dto/login-request.dto';
import { JwtPayload } from './types/jwt-payload';
import { CurrentUser } from './decorator/current-user.decorator';
import { JwtAuthGuard } from './guards/jwt-auth.guard';



@Controller("auth")
export class AuthController {


    private readonly logger =
        new Logger(AuthController.name);


    constructor(
        private service: AuthService
    ) { }



    @Post("login")
    login(
        @Body() dto: LoginRequestDto
    ) {

        this.logger.log(
            `[AUTH_EVENT] login_request_received username=${dto.username}`
        );

        return this.service.login(
            dto.username,
            dto.password
        );

    }

    @Get('me')
    @UseGuards(JwtAuthGuard)
    me(
        @CurrentUser() user: JwtPayload,
    ) {
        return {
            userId: user.sub,
            accountId: user.accountId,
            roles: user.roles,
        };
    }


}