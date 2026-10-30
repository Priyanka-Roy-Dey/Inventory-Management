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
