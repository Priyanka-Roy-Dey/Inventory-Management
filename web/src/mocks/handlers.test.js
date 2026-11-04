import { describe, expect, it } from 'vitest'
import { apiRequest } from '../api/client'
import { server } from '../test/server'
import { handlers } from './handlers'

describe('mock API handlers', () => {
  it.each([
    ['admin', 'admin123', 'ADMIN'],
    ['staff', 'staff123', 'STAFF'],
    ['viewer', 'viewer123', 'VIEWER'],
  ])('logs in the seeded %s user', async (username, password, role) => {
    server.use(...handlers)

    const session = await apiRequest('/auth/login', { method: 'POST', body: { username, password } })

    expect(session.user).toEqual({ username, role, mustChangePassword: false })
    expect(session.accessToken).toEqual(expect.any(String))
    expect(session.refreshToken).toEqual(expect.any(String))
  })

  it('refreshes a session like the real API', async () => {
    server.use(...handlers)
    const { refreshToken } = await apiRequest('/auth/login', {
      method: 'POST',
      body: { username: 'staff', password: 'staff123' },
    })

    const session = await apiRequest('/auth/refresh', { method: 'POST', body: { refreshToken } })

    expect(session.user).toEqual({ username: 'staff', role: 'STAFF', mustChangePassword: false })
    expect(session.accessToken).toEqual(expect.any(String))
  })

  it('rejects an unknown refresh token with 401', async () => {
    server.use(...handlers)

    const error = await apiRequest('/auth/refresh', {
      method: 'POST',
      body: { refreshToken: 'nope' },
    }).catch((e) => e)

    expect(error.status).toBe(401)
    expect(error.message).toBe('Invalid or expired refresh token')
  })

  it('rejects a wrong password with 401', async () => {
    server.use(...handlers)

    const error = await apiRequest('/auth/login', {
      method: 'POST',
      body: { username: 'admin', password: 'wrong' },
    }).catch((e) => e)

    expect(error.status).toBe(401)
    expect(error.message).toBe('Invalid username or password')
  })
})
