import { Body, Controller, Get, HttpCode, HttpStatus, Post } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiBody,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { AuthService } from './auth.service';
import { RegisterRequestDto } from './dto/register-request.dto';
import { LoginRequestDto } from './dto/login-request.dto';
import { RefreshRequestDto } from './dto/refresh-request.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { ErrorResponseDto } from './dto/error-response.dto';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  @ApiTags('Auth')
  @ApiOperation({
    operationId: 'register',
    summary: 'Register a user',
    description:
      'Creates a user and links it to an existing trading account. It does not create the trading account: accounts are owned by the Trade REST API\'s schema and seeded in Sprint 3. Registering against an unknown accountId fails validation.\n\nRegistration does not return tokens. The client logs in afterwards. Returning a session from a registration endpoint makes the endpoint an unauthenticated session factory, and any weakness in it becomes an authentication bypass.',
  })
  @ApiBody({ type: RegisterRequestDto })
  @ApiResponse({
    status: 201,
    description: 'User created.',
    type: UserResponseDto,
  })
  @ApiResponse({
    status: 409,
    description: 'The username is already taken.',
    type: ErrorResponseDto,
  })
  @ApiResponse({
    status: 422,
    description: 'The request failed field validation.',
    type: ErrorResponseDto,
  })
  async register(@Body() dto: RegisterRequestDto): Promise<UserResponseDto> {
    return this.authService.register(dto);
  }

  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiTags('Auth')
  @ApiOperation({
    operationId: 'login',
    summary: 'Log in and receive tokens',
    description:
      'Verifies the credentials and issues an access token and a refresh token. Every failure, whatever its cause, returns the same AUTH-401 body.',
  })
  @ApiBody({ type: LoginRequestDto })
  @ApiResponse({
    status: 200,
    description: 'Authenticated.',
    type: TokenResponseDto,
  })
  @ApiResponse({
    status: 401,
    description:
      'Authentication failed. Returned for an unknown username, a wrong password, an expired token, a wrongly signed token, a consumed refresh token and a malformed header. The body is identical in every case, on purpose.',
    type: ErrorResponseDto,
  })
  @ApiResponse({
    status: 422,
    description: 'The request failed field validation.',
    type: ErrorResponseDto,
  })
  async login(@Body() dto: LoginRequestDto): Promise<TokenResponseDto> {
    return this.authService.login(dto);
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  @ApiTags('Auth')
  @ApiOperation({
    operationId: 'refresh',
    summary: 'Exchange a refresh token for a new token pair',
    description:
      'Consumes the presented refresh token and issues a new pair. The presented token stops working immediately. Presenting a token that has already been consumed is treated as theft: revoke every refresh token for that user and return AUTH-401.',
  })
  @ApiBody({ type: RefreshRequestDto })
  @ApiResponse({
    status: 200,
    description: 'A new token pair.',
    type: TokenResponseDto,
  })
  @ApiResponse({
    status: 401,
    description:
      'Authentication failed. Returned for an unknown username, a wrong password, an expired token, a wrongly signed token, a consumed refresh token and a malformed header. The body is identical in every case, on purpose.',
    type: ErrorResponseDto,
  })
  @ApiResponse({
    status: 422,
    description: 'The request failed field validation.',
    type: ErrorResponseDto,
  })
  async refresh(@Body() dto: RefreshRequestDto): Promise<TokenResponseDto> {
    return this.authService.refresh(dto);
  }

  @Get('me')
  @HttpCode(HttpStatus.OK)
  @ApiTags('Profile')
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({
    operationId: 'getCurrentUser',
    summary: 'Get the authenticated user',
    description:
      'Returns the user identified by the access token. Protected. The UI calls it after login to populate the dashboard greeting and to learn which account to address.\n\nRead the identity from the verified token, never from a query parameter or a header the client controls. Accepting a client-supplied user identifier here is OWASP A01.',
  })
  @ApiResponse({
    status: 200,
    description: 'The authenticated user.',
    type: UserResponseDto,
  })
  @ApiResponse({
    status: 401,
    description:
      'Authentication failed. Returned for an unknown username, a wrong password, an expired token, a wrongly signed token, a consumed refresh token and a malformed header. The body is identical in every case, on purpose.',
    type: ErrorResponseDto,
  })
  async getCurrentUser(): Promise<UserResponseDto> {
    return this.authService.getCurrentUser();
  }
}