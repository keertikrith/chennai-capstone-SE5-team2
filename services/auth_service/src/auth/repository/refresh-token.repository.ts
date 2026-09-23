import { Injectable } from '@nestjs/common';

import {
    InjectRepository
} from '@nestjs/typeorm';

import {
    Repository
} from 'typeorm';

import {
    RefreshToken
} from '../entity/refresh-token.entity';



@Injectable()
export class RefreshTokenRepository {


    constructor(
        @InjectRepository(RefreshToken)
        private repo: Repository<RefreshToken>
    ) { }



    save(token: RefreshToken) {

        return this.repo.save(token);

    }

}