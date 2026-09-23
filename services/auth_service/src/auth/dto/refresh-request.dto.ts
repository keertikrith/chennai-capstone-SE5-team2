import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

export class RefreshRequestDto {
  @ApiProperty({
    description: 'The refresh token issued by the previous login or refresh.',
    example: '9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c',
  })
  @IsString()
  @IsNotEmpty()
  refreshToken: string;
}
