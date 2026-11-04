import { describe, expect, it } from 'vitest'
import { devApi } from './devApi'

describe('devApi', () => {
  it('proxies /api to the real API by default', () => {
    const config = devApi('development', {})

    expect(config.server.proxy['/api'].target).toBe('http://localhost:8080')
    expect(config.plugins).toEqual([])
  })

  it('uses VITE_API_URL when set', () => {
    const config = devApi('development', { VITE_API_URL: 'http://localhost:8081' })

    expect(config.server.proxy['/api'].target).toBe('http://localhost:8081')
  })

  it('serves the mock API instead in mock mode', () => {
    const config = devApi('mock', {})

    expect(config.server).toEqual({})
    expect(config.plugins.map((plugin) => plugin.name)).toEqual(['mock-api'])
  })
})
