import { http, HttpResponse } from 'msw'

// Fake users for `npm run dev:mock` only. With the real API, the first Admin is seeded by the API (INV-5).
const SEEDED_USERS = [
  { username: 'admin', password: 'admin123', role: 'ADMIN' },
  { username: 'staff', password: 'staff123', role: 'STAFF' },
  { username: 'viewer', password: 'viewer123', role: 'VIEWER' },
]

function sessionFor(user) {
  return {
    accessToken: `mock-token-${user.username}`,
    refreshToken: `mock-refresh-${user.username}`,
    user: { username: user.username, role: user.role, mustChangePassword: false },
  }
}

export const handlers = [
  http.post('*/api/auth/login', async ({ request }) => {
    const { username, password } = await request.json()
    const user = SEEDED_USERS.find((u) => u.username === username && u.password === password)

    if (!user) {
      return HttpResponse.json({ message: 'Invalid username or password' }, { status: 401 })
    }
    return HttpResponse.json(sessionFor(user))
  }),

  http.post('*/api/auth/refresh', async ({ request }) => {
    const { refreshToken } = await request.json()
    const user = SEEDED_USERS.find((u) => refreshToken === `mock-refresh-${u.username}`)

    if (!user) {
      return HttpResponse.json({ message: 'Invalid or expired refresh token' }, { status: 401 })
    }
    return HttpResponse.json(sessionFor(user))
  }),
]
