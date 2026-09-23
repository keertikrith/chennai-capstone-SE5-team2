import { Injectable } from '@nestjs/common';
import {
  InjectRepository
} from '@nestjs/typeorm';

import {
  Repository
} from 'typeorm';

import { AuthUser } from '../entity/auth-user.entity';


@Injectable()
export class AuthUserRepository {


  constructor(
    @InjectRepository(AuthUser)
    private repo: Repository<AuthUser>
  ) { }



  findByUsername(
    username: string
  ) {

    return this.repo.findOne({
      where: {
        username
      }
    });

  }

  findById(id: string) {
    return this.repo.findOne({
      where: {
        id,
      },
    });
  }

  save(user: Partial<AuthUser>) {
    return this.repo.save(this.repo.create(user));
  }

}