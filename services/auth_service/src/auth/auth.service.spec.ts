import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from './auth.service';
import { AuthUserRepository } from './repository/auth-user.repository';
import { JwtTokenService } from './services/jwt-token.service';
import { RefreshTokenService } from './services/refresh-token.service';
import { UnauthorizedException } from '@nestjs/common';
import { Role } from './dto/role.enum';

describe('AuthService', () => {
  let service: AuthService;
  let authUserRepository: { findById: jest.Mock };
  let jwtTokenService: { generateAccessToken: jest.Mock };
  let refreshTokenService: { exchange: jest.Mock; create: jest.Mock };

  beforeEach(async () => {
    authUserRepository = {
      findById: jest.fn(),
    };

    jwtTokenService = {
      generateAccessToken: jest.fn(),
    };

    refreshTokenService = {
      exchange: jest.fn(),
      create: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: AuthUserRepository,
          useValue: authUserRepository,
        },
        {
          provide: JwtTokenService,
          useValue: jwtTokenService,
        },
        {
          provide: RefreshTokenService,
          useValue: refreshTokenService,
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  it('refresh returns a new access token and a new refresh token', async () => {
    refreshTokenService.exchange.mockResolvedValue('user-1');
    authUserRepository.findById.mockResolvedValue({
      id: 'user-1',
      username: 'priya.menon',
      accountId: 1,
      roles: [Role.CUSTOMER],
    });
    jwtTokenService.generateAccessToken.mockReturnValue('new-access-token');
    refreshTokenService.create.mockResolvedValue('new-refresh-token');

    const result = await service.refresh({ refreshToken: 'presented-refresh-token' });

    expect(result).toEqual({
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
    expect(refreshTokenService.exchange).toHaveBeenCalledWith('presented-refresh-token');
    expect(refreshTokenService.create).toHaveBeenCalledWith('user-1');
  });

  it('newly issued refresh token works', async () => {
    refreshTokenService.exchange
      .mockResolvedValueOnce('user-1')
      .mockResolvedValueOnce('user-1');
    authUserRepository.findById.mockResolvedValue({
      id: 'user-1',
      username: 'priya.menon',
      accountId: 1,
      roles: [Role.CUSTOMER],
    });
    jwtTokenService.generateAccessToken
      .mockReturnValueOnce('access-token-1')
      .mockReturnValueOnce('access-token-2');
    refreshTokenService.create
      .mockResolvedValueOnce('refresh-token-2')
      .mockResolvedValueOnce('refresh-token-3');

    const firstRefresh = await service.refresh({ refreshToken: 'refresh-token-1' });
    const secondRefresh = await service.refresh({ refreshToken: firstRefresh.refreshToken });

    expect(firstRefresh.refreshToken).toBe('refresh-token-2');
    expect(secondRefresh.refreshToken).toBe('refresh-token-3');
    expect(refreshTokenService.exchange).toHaveBeenNthCalledWith(1, 'refresh-token-1');
    expect(refreshTokenService.exchange).toHaveBeenNthCalledWith(2, 'refresh-token-2');
  });

  it('declared revocation behaviour holds for a token that has already been exchanged', async () => {
    refreshTokenService.exchange.mockRejectedValue(
      new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Unauthorised' }),
    );

    await expect(
      service.refresh({ refreshToken: 'already-exchanged-refresh-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });
});
