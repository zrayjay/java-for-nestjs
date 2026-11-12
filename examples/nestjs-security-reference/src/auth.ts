import { createHash, randomBytes, randomUUID } from 'node:crypto'
import {
  BadRequestException,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common'
import { compare, hash as hashPassword } from 'bcryptjs'
import { SignJWT, jwtVerify } from 'jose'
import {
  createDataClient,
  type DataTransaction,
} from '@java-for-nestjs/prisma-reference/data-client'
import { SecurityContext, type Principal } from './context.js'
import type { SigningKeys } from './keys.js'

export type Client = ReturnType<typeof createDataClient>
export interface SecurityClock {
  now(): number
}
export const systemClock: SecurityClock = {
  now: () => Math.floor(Date.now() / 1000),
}
export const ISSUER = 'https://java-for-nestjs.example'
export const AUDIENCE = 'teaching-api'
const ACCESS_SECONDS = 300
const REFRESH_SECONDS = 604800
export interface Tokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: 'Bearer'
}
type User = NonNullable<Awaited<ReturnType<Client['secUser']['findUnique']>>>
type Session = {
  userId: number
  expiresAt: bigint
  revokedAt: bigint | null
  authVersion: number
}
type Refresh = {
  sessionId: string
  expiresAt: bigint
  consumedAt: bigint | null
  revokedAt: bigint | null
}
const digest = (value: string) =>
  createHash('sha256').update(value, 'ascii').digest('hex')

export class AuthService {
  private readonly dummyHash = hashPassword(randomUUID(), 10)
  constructor(
    readonly client: Client,
    readonly keys: SigningKeys,
    readonly clock: SecurityClock,
    readonly context: SecurityContext,
  ) {}
  async audit(
    tx: DataTransaction,
    actor: number | null,
    eventCode: string,
    resourceId: string | null,
  ): Promise<void> {
    await tx.secAudit.create({
      data: {
        actorId: actor,
        eventCode,
        resourceId,
        requestId: this.context.state()?.requestId ?? 'non-http',
        occurredAt: BigInt(this.clock.now()),
      },
    })
  }
  private async lockUser(
    tx: DataTransaction,
    id: number,
  ): Promise<User | null> {
    const rows = await tx.$queryRaw<
      (Omit<User, 'enabled'> & { enabled: boolean | number })[]
    >`
      SELECT id,username,password_hash AS passwordHash,department_id AS departmentId,
      enabled,auth_version AS authVersion FROM sec_users WHERE id=${id} FOR UPDATE`
    const row = rows[0]
    return row
      ? {
          ...row,
          id: Number(row.id),
          departmentId: Number(row.departmentId),
          authVersion: Number(row.authVersion),
          enabled: Boolean(row.enabled),
        }
      : null
  }
  private async issue(
    tx: DataTransaction,
    user: User,
    sessionId: string,
    sessionExpiry: bigint,
  ): Promise<Tokens> {
    const refreshToken = randomBytes(32).toString('base64url')
    await tx.secRefreshToken.create({
      data: {
        tokenHash: digest(refreshToken),
        sessionId,
        expiresAt: sessionExpiry,
      },
    })
    const now = this.clock.now()
    const expiresIn = Math.min(ACCESS_SECONDS, Number(sessionExpiry) - now)
    const accessToken = await new SignJWT({
      sid: sessionId,
      ver: user.authVersion,
    })
      .setProtectedHeader({ alg: 'RS256', kid: 'teaching-rsa' })
      .setIssuer(ISSUER)
      .setAudience(AUDIENCE)
      .setSubject(String(user.id))
      .setIssuedAt(now)
      .setNotBefore(now)
      .setExpirationTime(now + expiresIn)
      .setJti(randomUUID())
      .sign(this.keys.privateKey)
    return { accessToken, refreshToken, expiresIn, tokenType: 'Bearer' }
  }
  async login(username: string, password: string): Promise<Tokens> {
    if (Buffer.byteLength(password, 'utf8') > 72)
      throw new BadRequestException()
    const user = await this.client.secUser.findUnique({ where: { username } })
    const matches = await compare(
      password,
      user?.passwordHash ?? (await this.dummyHash),
    )
    if (!user || !user.enabled || !matches) {
      await this.client.$transaction((tx) =>
        this.audit(tx, null, 'auth.login.denied', null),
      )
      throw new UnauthorizedException()
    }
    const result = await this.client.$transaction(async (tx) => {
      const current = await this.lockUser(tx, user.id)
      if (
        !current?.enabled ||
        current.authVersion !== user.authVersion ||
        current.passwordHash !== user.passwordHash
      ) {
        await this.audit(tx, null, 'auth.login.denied', null)
        return null
      }
      const sessionId = randomUUID()
      const expiry = BigInt(this.clock.now() + REFRESH_SECONDS)
      await tx.secSession.create({
        data: {
          id: sessionId,
          userId: current.id,
          expiresAt: expiry,
          authVersion: current.authVersion,
        },
      })
      const tokens = await this.issue(tx, current, sessionId, expiry)
      await this.audit(tx, user.id, 'auth.login', null)
      return tokens
    })
    if (!result) throw new UnauthorizedException()
    return result
  }
  private async revoke(
    tx: DataTransaction,
    sessionId: string,
    actor: number,
    event: string,
  ): Promise<void> {
    const now = BigInt(this.clock.now())
    await tx.secSession.updateMany({
      where: { id: sessionId, revokedAt: null },
      data: { revokedAt: now },
    })
    await tx.secRefreshToken.updateMany({
      where: { sessionId, revokedAt: null },
      data: { revokedAt: now },
    })
    await this.audit(tx, actor, event, null)
  }
  async rotate(token: string): Promise<Tokens> {
    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new UnauthorizedException()
    const tokenHash = digest(token)
    const result = await this.client.$transaction(async (tx) => {
      const oldIdentity = await tx.secRefreshToken.findUnique({
        where: { tokenHash },
        select: { sessionId: true },
      })
      if (!oldIdentity) return null
      const id = oldIdentity.sessionId
      const routing = await tx.secSession.findUnique({
        where: { id },
        select: { userId: true },
      })
      if (!routing) return null
      // Current locking reads avoid an earlier MySQL RR snapshot.
      // Lock order: user state -> session family -> refresh token rows.
      const user = await this.lockUser(tx, routing.userId)
      const sessions = await tx.$queryRaw<
        Session[]
      >`SELECT user_id AS userId,expires_at AS expiresAt,revoked_at AS revokedAt,auth_version AS authVersion FROM sec_sessions WHERE id=${id} FOR UPDATE`
      const rows = await tx.$queryRaw<
        Refresh[]
      >`SELECT session_id AS sessionId,expires_at AS expiresAt,consumed_at AS consumedAt,revoked_at AS revokedAt FROM sec_refresh_tokens WHERE token_hash=${tokenHash} FOR UPDATE`
      const family = sessions[0]
          ? {
              ...sessions[0],
              userId: Number(sessions[0].userId),
              authVersion: Number(sessions[0].authVersion),
            }
          : undefined,
        old = rows[0]
      if (!family || !old) return null
      const now = BigInt(this.clock.now())
      if (
        old.consumedAt !== null ||
        old.revokedAt !== null ||
        old.expiresAt <= now ||
        family.revokedAt !== null ||
        family.expiresAt <= now ||
        !user?.enabled ||
        Number(family.userId) !== user.id ||
        Number(family.authVersion) !== user.authVersion ||
        old.sessionId !== id
      ) {
        await this.revoke(tx, id, family.userId, 'auth.refresh.denied')
        return null
      }
      await tx.secRefreshToken.update({
        where: { tokenHash },
        data: { consumedAt: now },
      })
      const tokens = await this.issue(tx, user, id, family.expiresAt)
      await this.audit(tx, user.id, 'auth.refresh', null)
      return tokens
    })
    if (!result) throw new UnauthorizedException() // revocation has committed
    return result
  }
  async authenticate(
    header: string | undefined,
  ): Promise<Principal | undefined> {
    if (!header || !/^Bearer\b/i.test(header)) return undefined
    const match = /^Bearer ([^\s]+)$/i.exec(header)
    if (!match) throw new UnauthorizedException()
    let payload
    try {
      payload = (
        await jwtVerify(match[1], this.keys.publicKey, {
          algorithms: ['RS256'],
          issuer: ISSUER,
          audience: AUDIENCE,
          clockTolerance: 0,
          currentDate: new Date(this.clock.now() * 1000),
          requiredClaims: [
            'iss',
            'aud',
            'exp',
            'iat',
            'nbf',
            'sub',
            'jti',
            'sid',
            'ver',
          ],
        })
      ).payload
    } catch {
      throw new UnauthorizedException()
    }
    const id = Number(payload.sub)
    if (
      !/^[1-9][0-9]*$/.test(payload.sub ?? '') ||
      id > 2147483647 ||
      typeof payload.sid !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
        payload.sid,
      ) ||
      typeof payload.iat !== 'number' ||
      payload.iat > this.clock.now() ||
      !Number.isSafeInteger(payload.ver)
    )
      throw new UnauthorizedException()
    const family = await this.client.secSession.findUnique({
      where: { id: payload.sid },
      include: {
        user: {
          include: {
            roles: {
              include: {
                role: {
                  include: { permissions: { include: { permission: true } } },
                },
              },
            },
          },
        },
      },
    })
    if (
      !family ||
      family.userId !== id ||
      family.revokedAt !== null ||
      family.expiresAt <= BigInt(this.clock.now()) ||
      !family.user.enabled ||
      family.authVersion !== family.user.authVersion ||
      family.user.authVersion !== payload.ver
    )
      throw new UnauthorizedException()
    return Object.freeze({
      id,
      username: family.user.username,
      departmentId: family.user.departmentId,
      permissions: new Set(
        family.user.roles.flatMap((role) =>
          role.role.permissions.map((value) => value.permission.code),
        ),
      ),
      sessionId: payload.sid,
    })
  }
  async logout(): Promise<void> {
    const user = this.context.current()
    await this.client.$transaction(async (tx) => {
      await this.lockUser(tx, user.id)
      await tx.$queryRaw`SELECT id FROM sec_sessions WHERE id=${user.sessionId} FOR UPDATE`
      await this.revoke(tx, user.sessionId, user.id, 'auth.logout')
    })
  }
  async assignRoles(id: number, roles: number[]): Promise<void> {
    this.context.require('role:manage')
    if (new Set(roles).size !== roles.length) throw new BadRequestException()
    await this.client.$transaction(async (tx) => {
      if (!(await tx.secUser.findUnique({ where: { id } })))
        throw new NotFoundException()
      if (
        (await tx.secRole.count({ where: { id: { in: roles } } })) !==
        roles.length
      )
        throw new BadRequestException()
      await tx.secUserRole.deleteMany({ where: { userId: id } })
      if (roles.length)
        await tx.secUserRole.createMany({
          data: roles.map((roleId) => ({ userId: id, roleId })),
        })
      await this.audit(
        tx,
        this.context.current().id,
        'roles.changed',
        String(id),
      )
    })
  }
  async onModuleDestroy(): Promise<void> {
    await this.client.$disconnect()
  }
}
