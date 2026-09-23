import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsArray,
  IsEnum,
  IsInt,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { Role } from './role.enum';

export class RegisterRequestDto {
  @ApiProperty({
    description: 'Username for the user',
    example: 'priya.menon',
    minLength: 3,
    maxLength: 64,
    pattern: '^[a-zA-Z0-9._-]+$',
  })
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/, {
    message: 'Username must contain only alphanumeric characters, dots, underscores, or hyphens',
  })
  username: string;

  @ApiProperty({
    description:
      'Minimum twelve characters. Length beats character-class rules, so do not impose a symbol requirement that pushes users towards a shorter password.',
    example: 'correct horse battery staple',
    minLength: 12,
    maxLength: 128,
    writeOnly: true,
  })
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password: string;

  @ApiProperty({
    description: 'The numeric trading account key, ACCOUNTS.id, that this user will trade.',
    example: 1,
    type: 'integer',
    format: 'int64',
    minimum: 1,
  })
  @IsInt()
  @Min(1)
  accountId: number;

  @ApiPropertyOptional({
    description:
      'Optional. Defaults to ["CUSTOMER"]. Ignore this field on a public registration route unless the caller already holds an ADMIN token.',
    enum: Role,
    isArray: true,
    example: [Role.CUSTOMER],
  })
  @IsOptional()
  @IsArray()
  @IsEnum(Role, { each: true })
  roles?: Role[];
}
