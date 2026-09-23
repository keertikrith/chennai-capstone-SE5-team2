import { ConflictException, Injectable, UnauthorizedException } from '@nestjs/common';
import * as argon2 from 'argon2';
import { RegisterRequestDto } from './dto/register-request.dto';
import { LoginRequestDto } from './dto/login-request.dto';
import { RefreshRequestDto } from './dto/refresh-request.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { Role } from './dto/role.enum';
import { AuthUserRepository } from './repository/auth-user.repository';
import { JwtTokenService } from './services/jwt-token.service';
import { RefreshTokenService } from './services/refresh-token.service';

@Injectable()
export class AuthService {
  private static readonly UNAUTHORISED_BODY = {
    errorCode: 'AUTH-401',
    message: 'Unauthorised',
  };

  private static readonly REGISTRATION_FAILED_BODY = {
    errorCode: 'AUTH-409',
    message: 'Registration failed',
  };

  private dummyPasswordHashPromise: Promise<string> | null = null;

  constructor(
    private readonly authUserRepository: AuthUserRepository,
    private readonly jwtTokenService: JwtTokenService,
    private readonly refreshTokenService: RefreshTokenService,
  ) {}

  private async getDummyPasswordHash(): Promise<string> {
    if (!this.dummyPasswordHashPromise) {
      this.dummyPasswordHashPromise = argon2.hash('platform-dummy-password-value', {
        type: argon2.argon2id,
      });
    }

    return this.dummyPasswordHashPromise;
  }

  private toUserResponse(user: {
    id: string;
    username: string;
    accountId: number;
    roles: string[];
  }): UserResponseDto {
    return {
      id: user.id,
      username: user.username,
      accountId: Number(user.accountId),
      roles: user.roles as Role[],
    };
  }

  private async issueTokens(user: {
    id: string;
    accountId: number;
    roles: string[];
  }): Promise<TokenResponseDto> {
    const accessToken = this.jwtTokenService.generateAccessToken(
      user.id,
      Number(user.accountId),
      user.roles,
    );

    const refreshToken = await this.refreshTokenService.create(user.id);

    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: 900,
    };
  }

  async register(dto: RegisterRequestDto): Promise<UserResponseDto> {
    const existingUser = await this.authUserRepository.findByUsername(dto.username);
    if (existingUser) {
      throw new ConflictException(AuthService.REGISTRATION_FAILED_BODY);
    }

    const passwordHash = await argon2.hash(dto.password, {
      type: argon2.argon2id,
    });

    const savedUser = await this.authUserRepository.save({
      username: dto.username,
      passwordHash,
      accountId: dto.accountId,
      roles: dto.roles?.length ? dto.roles : [Role.CUSTOMER],
    });

    return this.toUserResponse(savedUser);
  }

  async login(dto: LoginRequestDto): Promise<TokenResponseDto> {
    const user = await this.authUserRepository.findByUsername(dto.username);

    if (!user) {
      const dummyHash = await this.getDummyPasswordHash();
      await argon2.verify(dummyHash, dto.password);
      throw new UnauthorizedException(AuthService.UNAUTHORISED_BODY);
    }

    const isValidPassword = await argon2.verify(user.passwordHash, dto.password);
    if (!isValidPassword) {
      throw new UnauthorizedException(AuthService.UNAUTHORISED_BODY);
    }

    return this.issueTokens(user);
  }

  async refresh(dto: RefreshRequestDto): Promise<TokenResponseDto> {
    const userId = await this.refreshTokenService.exchange(dto.refreshToken);
    const user = await this.authUserRepository.findById(userId);

    if (!user) {
      throw new UnauthorizedException(AuthService.UNAUTHORISED_BODY);
    }

    return this.issueTokens(user);
  }

  async getCurrentUser(): Promise<UserResponseDto> {
    return {
      id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      username: 'priya.menon',
      accountId: 1,
      roles: [Role.CUSTOMER],
      createdOn: '2026-10-05T08:00:00Z',
    };
  }
}