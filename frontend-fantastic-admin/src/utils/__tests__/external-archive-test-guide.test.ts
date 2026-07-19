import { describe, expect, it } from 'vitest'
import {
  buildExternalArchiveIntegrationConfig,
  createRandomHmacSecret,
  explainExternalArchiveTicketFailure,
  getExternalArchiveReadiness,
} from '../external-archive-test-guide'

describe('external archive integration test guide', () => {
  it('generates a 256-bit hexadecimal HMAC secret', () => {
    const secret = createRandomHmacSecret()
    expect(secret).toMatch(/^[0-9a-f]{64}$/)
  })

  it('explains that a browser-generated secret does not enable the backend', () => {
    expect(getExternalArchiveReadiness({
      enabled: false,
      requestIp: '10.0.0.8',
      ticketTtlSeconds: 90,
      sessionTtlSeconds: 1800,
      timestampToleranceSeconds: 300,
      maxArchivesPerTicket: 100,
      clients: [],
    }, 'his-system', 'a'.repeat(64))).toMatchObject({
      ready: false,
      title: '外部系统集成未启用',
    })
  })

  it('builds a copyable Spring properties configuration with the same secret', () => {
    const secret = 'a'.repeat(64)
    const config = buildExternalArchiveIntegrationConfig({
      clientId: 'his-system',
      secret,
      allowedIp: '10.0.0.8',
    })
    expect(config).toContain('mrr.integration.enabled=true')
    expect(config).toContain(`mrr.integration.clients[0].secret=${secret}`)
    expect(config).toContain('mrr.integration.clients[0].allowed-ips[0]=10.0.0.8')
  })

  it('maps the observed 503 response to an actionable instruction', () => {
    expect(explainExternalArchiveTicketFailure(503, {
      message: '外部系统集成未启用',
    })).toContain('mrr.integration.enabled=false')
  })
})
