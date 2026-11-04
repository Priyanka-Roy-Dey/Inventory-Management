import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import AuthProvider from './AuthProvider'
import useAuth from './useAuth'

const SESSION = { accessToken: 'token-1', user: { username: 'admin', role: 'ADMIN' } }

function AuthProbe() {
  const { user, token, login, logout } = useAuth()
  const [error, setError] = useState('')

  async function handleLogin() {
    try {
      await login('admin', 'secret')
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <>
      <p>user: {user ? `${user.username} (${user.role})` : 'none'}</p>
      <p>token: {token ?? 'none'}</p>
      <p>error: {error || 'none'}</p>
      <button onClick={handleLogin}>log in</button>
      <button onClick={logout}>log out</button>
    </>
  )
}

function renderProbe() {
  render(
    <AuthProvider>
      <AuthProbe />
    </AuthProvider>,
  )
}

describe('AuthProvider', () => {
  it('starts logged out', () => {
    renderProbe()

    expect(screen.getByText('user: none')).toBeInTheDocument()
  })

  it('logs in with the credentials and keeps the session', async () => {
    let credentials
    server.use(
      http.post('/api/auth/login', async ({ request }) => {
        credentials = await request.json()
        return HttpResponse.json(SESSION)
      }),
    )
    renderProbe()

    await userEvent.click(screen.getByRole('button', { name: 'log in' }))

    expect(await screen.findByText('user: admin (ADMIN)')).toBeInTheDocument()
    expect(screen.getByText('token: token-1')).toBeInTheDocument()
    expect(credentials).toEqual({ username: 'admin', password: 'secret' })
    expect(JSON.parse(sessionStorage.getItem('inventory.session'))).toEqual(SESSION)
  })

  it('stays logged out when login fails', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ message: 'Invalid username or password' }, { status: 401 }),
      ),
    )
    renderProbe()

    await userEvent.click(screen.getByRole('button', { name: 'log in' }))

    expect(await screen.findByText('error: Invalid username or password')).toBeInTheDocument()
    expect(screen.getByText('user: none')).toBeInTheDocument()
  })

  it('restores the session after a page reload', () => {
    sessionStorage.setItem('inventory.session', JSON.stringify(SESSION))

    renderProbe()

    expect(screen.getByText('user: admin (ADMIN)')).toBeInTheDocument()
  })

  it('logs out and clears the session', async () => {
    sessionStorage.setItem('inventory.session', JSON.stringify(SESSION))
    renderProbe()

    await userEvent.click(screen.getByRole('button', { name: 'log out' }))

    expect(screen.getByText('user: none')).toBeInTheDocument()
    expect(sessionStorage.getItem('inventory.session')).toBeNull()
  })
})

const SIGNED_IN = {
  accessToken: 'old-access',
  refreshToken: 'old-refresh',
  user: { username: 'staff', role: 'STAFF', mustChangePassword: false },
}
const REFRESHED = { ...SIGNED_IN, accessToken: 'new-access', refreshToken: 'new-refresh' }

function RequestProbe({ paths = ['/items'] }) {
  const { user, request } = useAuth()
  const [result, setResult] = useState('none')

  async function handleLoad() {
    try {
      const responses = await Promise.all(paths.map((path) => request(path)))
      setResult(JSON.stringify(responses))
    } catch (e) {
      setResult(`error ${e.status}: ${e.message}`)
    }
  }

  return (
    <>
      <p>user: {user ? user.username : 'none'}</p>
      <p>result: {result}</p>
      <button onClick={handleLoad}>load</button>
    </>
  )
}

function renderRequestProbe(props) {
  sessionStorage.setItem('inventory.session', JSON.stringify(SIGNED_IN))
  render(
    <AuthProvider>
      <RequestProbe {...props} />
    </AuthProvider>,
  )
}

// Accepts only the new access token, like the API after the old one expired.
function itemsNeedingTheNewToken() {
  return http.get('/api/items', ({ request }) =>
    request.headers.get('Authorization') === 'Bearer new-access'
      ? HttpResponse.json(['A-1'])
      : HttpResponse.json({ message: 'Invalid or expired token' }, { status: 401 }),
  )
}

describe('AuthProvider request', () => {
  it('sends the access token', async () => {
    let authorization
    server.use(
      http.get('/api/items', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json([])
      }),
    )
    renderRequestProbe()

    await userEvent.click(screen.getByRole('button', { name: 'load' }))

    expect(await screen.findByText('result: [[]]')).toBeInTheDocument()
    expect(authorization).toBe('Bearer old-access')
  })

  it('refreshes an expired access token once and retries', async () => {
    let refreshedWith
    server.use(
      itemsNeedingTheNewToken(),
      http.post('/api/auth/refresh', async ({ request }) => {
        refreshedWith = await request.json()
        return HttpResponse.json(REFRESHED)
      }),
    )
    renderRequestProbe()

    await userEvent.click(screen.getByRole('button', { name: 'load' }))

    expect(await screen.findByText('result: [["A-1"]]')).toBeInTheDocument()
    expect(refreshedWith).toEqual({ refreshToken: 'old-refresh' })
    expect(JSON.parse(sessionStorage.getItem('inventory.session'))).toEqual(REFRESHED)
  })

  it('shares one refresh between requests that fail together', async () => {
    let refreshes = 0
    server.use(
      itemsNeedingTheNewToken(),
      http.post('/api/auth/refresh', () => {
        refreshes += 1
        return HttpResponse.json(REFRESHED)
      }),
    )
    renderRequestProbe({ paths: ['/items', '/items', '/items'] })

    await userEvent.click(screen.getByRole('button', { name: 'load' }))

    expect(await screen.findByText('result: [["A-1"],["A-1"],["A-1"]]')).toBeInTheDocument()
    expect(refreshes).toBe(1)
  })

  it('signs out when the refresh token is rejected too', async () => {
    server.use(
      itemsNeedingTheNewToken(),
      http.post('/api/auth/refresh', () =>
        HttpResponse.json({ message: 'Invalid or expired refresh token' }, { status: 401 }),
      ),
    )
    renderRequestProbe()

    await userEvent.click(screen.getByRole('button', { name: 'load' }))

    expect(await screen.findByText('user: none')).toBeInTheDocument()
    expect(screen.getByText('result: error 401: Invalid or expired token')).toBeInTheDocument()
    expect(sessionStorage.getItem('inventory.session')).toBeNull()
  })

  it('does not refresh for other errors', async () => {
    server.use(
      http.get('/api/items', () => HttpResponse.json({ message: 'Access denied' }, { status: 403 })),
    )
    renderRequestProbe()

    await userEvent.click(screen.getByRole('button', { name: 'load' }))

    expect(await screen.findByText('result: error 403: Access denied')).toBeInTheDocument()
    expect(screen.getByText('user: staff')).toBeInTheDocument()
  })
})

describe('AuthProvider changePassword', () => {
  function ChangePasswordProbe() {
    const { user, changePassword } = useAuth()
    return (
      <>
        <p>must change: {String(user.mustChangePassword)}</p>
        <button onClick={() => changePassword('temporary-pass', 'my-new-password')}>change</button>
      </>
    )
  }

  it('sends both passwords and keeps the fresh session', async () => {
    const changed = { ...REFRESHED, user: { ...SIGNED_IN.user, mustChangePassword: false } }
    let received
    server.use(
      http.post('/api/auth/change-password', async ({ request }) => {
        received = {
          authorization: request.headers.get('Authorization'),
          body: await request.json(),
        }
        return HttpResponse.json(changed)
      }),
    )
    sessionStorage.setItem(
      'inventory.session',
      JSON.stringify({ ...SIGNED_IN, user: { ...SIGNED_IN.user, mustChangePassword: true } }),
    )
    render(
      <AuthProvider>
        <ChangePasswordProbe />
      </AuthProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'change' }))

    expect(await screen.findByText('must change: false')).toBeInTheDocument()
    expect(received).toEqual({
      authorization: 'Bearer old-access',
      body: { currentPassword: 'temporary-pass', newPassword: 'my-new-password' },
    })
    expect(JSON.parse(sessionStorage.getItem('inventory.session'))).toEqual(changed)
  })
})
