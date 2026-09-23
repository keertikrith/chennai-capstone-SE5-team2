import { ApiProperty } from '@nestjs/swagger';

export class ErrorResponseDto {
  @ApiProperty({
    description: 'Platform standard error code',
    enum: ['AUTH-401', 'AUTH-409', 'VAL-422'],
    example: 'AUTH-401',
  })
  errorCode: string;

  @ApiProperty({
    description: 'Vague error message to prevent credential enumeration',
    example: 'Unauthorised',
  })
  message: string;
}
