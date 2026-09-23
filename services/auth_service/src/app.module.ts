import { Module } from '@nestjs/common';

import {
    ConfigModule,
    ConfigService,
} from '@nestjs/config';

import {
    TypeOrmModule,
} from '@nestjs/typeorm';

import { AuthModule } from './auth/auth.module';

import { AuthUser } from './auth/entity/auth-user.entity';
import { RefreshToken } from './auth/entity/refresh-token.entity';


@Module({

    imports: [

        ConfigModule.forRoot({
            isGlobal: true,
        }),


        TypeOrmModule.forRootAsync({

            imports: [
                ConfigModule,
            ],

            inject: [
                ConfigService,
            ],

            useFactory: (
                config: ConfigService,
            ) => ({

                type: 'postgres',

                host:
                    config.getOrThrow<string>(
                        'DB_HOST',
                    ),

                port:
                    Number(
                        config.getOrThrow<string>(
                            'DB_PORT',
                        ),
                    ),

                username:
                    config.getOrThrow<string>(
                        'DB_USERNAME',
                    ),

                password:
                    config.getOrThrow<string>(
                        'DB_PASSWORD',
                    ),

                database:
                    config.getOrThrow<string>(
                        'DB_NAME',
                    ),

                entities: [
                    AuthUser,
                    RefreshToken,
                ],

                synchronize: false,
            }),
        }),


        AuthModule,
    ],
})
export class AppModule {}