import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Role } from './role.enum';

export class UserResponseDto {
  @ApiProperty({
    description: 'The value carried in the sub claim.',
    example: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    format: 'uuid',
  })
  id: string;

  @ApiProperty({
    description: 'Username',
    example: 'priya.menon',
  })
  username: string;

  @ApiProperty({
    description: 'The numeric trading account key, ACCOUNTS.id.',
    example: 1,
    type: 'integer',
    format: 'int64',
  })
  accountId: number;

  @ApiProperty({
    description: 'User roles',
    enum: Role,
    isArray: true,
    example: [Role.CUSTOMER],
  })
  roles: Role[];

  @ApiPropertyOptional({
    description: 'Timestamp when user was created',
    example: '2026-10-05T08:00:00Z',
    format: 'date-time',
  })
  createdOn?: string;
}
