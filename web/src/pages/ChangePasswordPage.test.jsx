import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import ChangePasswordPage from './ChangePasswordPage'

const FLAGGED = {
  accessToken: 'token-1',
  refreshToken: 'refresh-1',
  user: { username: 'newbie', role: 'VIEWER', mustChangePassword: true },
}
const CHANGED = {
  accessToken: 'token-2',
  refreshToken: 'refresh-2',
  user: { ...FLAGGED.user, mustChangePassword: false },
}

function renderPage() {
  sessionStorage.setItem('inventory.session', JSON.stringify(FLAGGED))
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/change-password']}>
        <Routes>
          <Route path="/change-password" element={<ChangePasswordPage />} />
          <Route path="/" element={<h1>Dashboard</h1>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

async function fillAndSubmit({ current = '', next = '', confirm = '' }) {
  if (current) {
    await userEvent.type(screen.getByLabelText(/current password/i), current)
  }
  if (next) {
    await userEvent.type(screen.getByLabelText(/^new password/i), next)
  }
  if (confirm) {
    await userEvent.type(screen.getByLabelText(/confirm new password/i), confirm)
  }
  await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
}

describe('ChangePasswordPage', () => {
  it('explains why a new password is needed', () => {
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Set a new password' })).toBeInTheDocument()
    expect(screen.getByText(/temporary password/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/current password/i)).toHaveAttribute('type', 'password')
  })

  it('blocks an invalid submit and does not call the API', async () => {
    let called = false
    server.use(
      http.post('/api/auth/change-password', () => {
        called = true
        return HttpResponse.json(CHANGED)
      }),
    )
    renderPage()

    await fillAndSubmit({ next: 'short', confirm: 'different' })

    expect(screen.getByText('Current password is required')).toBeInTheDocument()
    expect(screen.getByText('Use at least 12 characters')).toBeInTheDocument()
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('changes the password and continues to the dashboard', async () => {
    let body
    server.use(
      http.post('/api/auth/change-password', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(CHANGED)
      }),
    )
    renderPage()

    await fillAndSubmit({ current: 'temp-pass', next: 'my-new-password', confirm: 'my-new-password' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()
    expect(body).toEqual({ currentPassword: 'temp-pass', newPassword: 'my-new-password' })
  })

  it('shows the server message when the change is rejected', async () => {
    server.use(
      http.post('/api/auth/change-password', () =>
        HttpResponse.json({ message: 'Current password is incorrect' }, { status: 400 }),
      ),
    )
    renderPage()

    await fillAndSubmit({ current: 'wrong', next: 'my-new-password', confirm: 'my-new-password' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Current password is incorrect')
    expect(screen.getByRole('button', { name: 'Change password' })).toBeEnabled()
  })
})
