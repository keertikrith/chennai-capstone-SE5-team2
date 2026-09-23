import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { AuthUser } from './entity/auth-user.entity';
import { RefreshToken } from './entity/refresh-token.entity';
import { AuthUserRepository } from './repository/auth-user.repository';
import { RefreshTokenRepository } from './repository/refresh-token.repository';
import { JwtTokenService } from './services/jwt-token.service';
import { RefreshTokenService } from './services/refresh-token.service';

@Module({
  imports: [
    ConfigModule,
    TypeOrmModule.forFeature([AuthUser, RefreshToken]),
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        secret: configService.get<string>('auth.jwtSecret'),
        signOptions: {
          issuer: configService.get<string>('auth.jwtIssuer', 'auth-service'),
          algorithm: 'HS256',
        },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [
    AuthService,
    AuthUserRepository,
    RefreshTokenRepository,
    JwtTokenService,
    RefreshTokenService,
  ],
  exports: [AuthService],
})
export class AuthModule {}