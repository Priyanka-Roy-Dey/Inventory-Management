import { http, HttpResponse } from 'msw'

// Fake users for local development only. The real Admin is seeded by the API (INV-5).
const SEEDED_USERS = [
  { username: 'admin', password: 'admin123', role: 'ADMIN' },
  { username: 'staff', password: 'staff123', role: 'STAFF' },
  { username: 'viewer', password: 'viewer123', role: 'VIEWER' },
]

export const handlers = [
  http.post('*/api/auth/login', async ({ request }) => {
    const { username, password } = await request.json()
    const user = SEEDED_USERS.find((u) => u.username === username && u.password === password)

    if (!user) {
      return HttpResponse.json({ message: 'Invalid username or password' }, { status: 401 })
    }
    return HttpResponse.json({
      accessToken: `mock-token-${user.username}`,
      user: { username: user.username, role: user.role },
    })
  }),
]
