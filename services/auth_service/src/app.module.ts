import { Module } from '@nestjs/common';

import {
    ConfigModule,
    ConfigService,
} from '@nestjs/config';
import { configuration } from './config/configuration';

import {
    TypeOrmModule,
} from '@nestjs/typeorm';

import { AuthModule } from './auth/auth.module';
import { HealthController } from './health/health.controller';
import { HealthModule } from './health/health.module';

import { AuthUser } from './auth/entity/auth-user.entity';
import { RefreshToken } from './auth/entity/refresh-token.entity';


@Module({

    imports: [

        ConfigModule.forRoot({
            isGlobal: true,
            load: [configuration],
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
                    config.get<string>(
                        'database.host',
                    ),

                port:
                    config.get<number>(
                        'database.port',
                    ),

                username:
                    config.get<string>(
                        'database.user',
                    ),

                password:
                    config.get<string>(
                        'database.password',
                    ),

                database:
                    config.get<string>(
                        'database.database',
                    ),

                schema:
                    config.get<string>(
                        'database.schema',
                    ),

                entities: [
                    AuthUser,
                    RefreshToken,
                ],

                synchronize: false,
            }),
        }),


        AuthModule,
        HealthModule,
    ],
    controllers: [HealthController],
})
export class AppModule {}