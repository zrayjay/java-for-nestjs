import { performance } from 'node:perf_hooks'
import { MessageStore, type TaskEvent } from './message.js'

export type RelayResult = 'EMPTY' | 'SENT' | 'LEASE_LOST' | 'CIRCUIT_OPEN'
/** Serial, instance-local circuit. SQL lease tokens do not fence a broker publication. */
export class OutboxRelay {
  private failures = 0
  private openedAt = 0
  private busy = false
  constructor(
    private readonly store: MessageStore,
    private readonly publish: (event: TaskEvent) => Promise<void>,
    private readonly leaseMillis: number,
    private readonly cooldownMillis: number,
  ) {
    if (
      !Number.isInteger(leaseMillis) ||
      leaseMillis < 100 ||
      leaseMillis > 5000 ||
      !Number.isFinite(cooldownMillis) ||
      cooldownMillis <= 0 ||
      cooldownMillis > 60000
    )
      throw new Error('Explicit bounded teaching lease and cooldown required')
  }
  async runOnce(
    afterConfirm: () => void | Promise<void> = () => {},
  ): Promise<RelayResult> {
    if (this.busy)
      throw new Error('This teaching relay instance must be called serially')
    this.busy = true
    try {
      if (
        this.failures >= 2 &&
        performance.now() - this.openedAt < this.cooldownMillis
      )
        return 'CIRCUIT_OPEN'
      const claim = await this.store.claim(this.leaseMillis)
      if (!claim) return 'EMPTY'
      try {
        await this.publish(claim.event)
      } catch (error) {
        this.failures++
        if (this.failures >= 2) this.openedAt = performance.now()
        throw error
      }
      this.failures = 0
      await afterConfirm()
      const result = (await this.store.markSent(claim)) ? 'SENT' : 'LEASE_LOST'
      console.log('OUTBOX_RESULT', result, 'eventId=' + claim.event.eventId)
      return result
    } finally {
      this.busy = false
    }
  }
}
