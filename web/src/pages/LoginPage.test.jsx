import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import LoginPage from './LoginPage'

const SESSION = { accessToken: 'token-1', user: { username: 'admin', role: 'ADMIN' } }

function renderLogin(initialEntry = '/login') {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<h1>Dashboard</h1>} />
          <Route path="/items" element={<h1>Items</h1>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

async function fillAndSubmit(username, password) {
  if (username) {
    await userEvent.type(screen.getByLabelText(/username/i), username)
  }
  if (password) {
    await userEvent.type(screen.getByLabelText(/password/i), password)
  }
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('LoginPage', () => {
  it('shows the sign-in form', () => {
    renderLogin()

    expect(screen.getByRole('heading', { level: 1, name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password')
  })

  it('blocks an empty submit and does not call the API', async () => {
    let called = false
    server.use(
      http.post('/api/auth/login', () => {
        called = true
        return HttpResponse.json(SESSION)
      }),
    )
    renderLogin()

    await fillAndSubmit('', '')

    expect(screen.getByText('Username is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toHaveAttribute('aria-invalid', 'true')
    expect(called).toBe(false)
  })

  it('shows the server error when the credentials are wrong', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ message: 'Invalid username or password' }, { status: 401 }),
      ),
    )
    renderLogin()

    await fillAndSubmit('admin', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
  })

  it('goes to the dashboard after signing in', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json(SESSION)))
    renderLogin()

    await fillAndSubmit('admin', 'secret')

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })

  it('returns to the page the user first asked for', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json(SESSION)))
    renderLogin({ pathname: '/login', state: { from: '/items' } })

    await fillAndSubmit('admin', 'secret')

    expect(await screen.findByRole('heading', { name: 'Items' })).toBeInTheDocument()
  })

  it('skips the form when already signed in', () => {
    sessionStorage.setItem('inventory.session', JSON.stringify(SESSION))

    renderLogin()

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })
})
