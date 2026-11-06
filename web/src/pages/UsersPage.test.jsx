import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import UsersPage from './UsersPage'

const ADMIN = { id: 1, username: 'admin', role: 'ADMIN', mustChangePassword: false, createdAt: '2026-11-01T09:00:00Z' }
const SAM = { id: 2, username: 'sam.staff', role: 'STAFF', mustChangePassword: true, createdAt: '2026-11-05T09:00:00Z' }
const VIC = { id: 3, username: 'vic', role: 'VIEWER', mustChangePassword: false, createdAt: '2026-11-04T09:00:00Z' }

function renderPage() {
  sessionStorage.setItem(
    'inventory.session',
    JSON.stringify({
      accessToken: 'token-1',
      refreshToken: 'refresh-1',
      user: { username: 'admin', role: 'ADMIN', mustChangePassword: false },
    }),
  )
  render(
    <AuthProvider>
      <MemoryRouter>
        <UsersPage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

function serveUsers(users) {
  server.use(http.get('/api/users', () => HttpResponse.json(users)))
}

function rowFor(username) {
  return screen.getByText(username).closest('tr')
}

describe('UsersPage list', () => {
  it('shows a loading indicator, then the users with their role and status', async () => {
    serveUsers([ADMIN, SAM, VIC])
    renderPage()

    expect(screen.getByRole('progressbar', { name: 'Loading users' })).toBeInTheDocument()

    const table = await screen.findByRole('table', { name: 'Users' })
    expect(within(table).getAllByRole('row')).toHaveLength(4)
    expect(within(rowFor('sam.staff')).getByText('Staff')).toBeInTheDocument()
    expect(within(rowFor('sam.staff')).getByText('Must change password')).toBeInTheDocument()
    expect(within(rowFor('vic')).getByText('Viewer')).toBeInTheDocument()
    expect(within(rowFor('vic')).getByText('Active')).toBeInTheDocument()
  })

  it('does not offer to reset your own password', async () => {
    serveUsers([ADMIN, SAM])
    renderPage()

    await screen.findByText('sam.staff')
    expect(within(rowFor('sam.staff')).getByRole('button', { name: 'Reset password for sam.staff' })).toBeInTheDocument()
    expect(within(rowFor('admin')).queryByRole('button', { name: /Reset password/ })).not.toBeInTheDocument()
    expect(within(rowFor('admin')).getByText('You')).toBeInTheDocument()
  })

  it('shows an error with a retry button', async () => {
    let calls = 0
    server.use(
      http.get('/api/users', () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ message: 'Unexpected error' }, { status: 500 })
          : HttpResponse.json([ADMIN])
      }),
    )
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load users: Unexpected error')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('admin')).toBeInTheDocument()
  })
})

describe('UsersPage create user', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn(() => Promise.resolve()) },
      configurable: true,
    })
  })

  async function openCreate() {
    await userEvent.click(await screen.findByRole('button', { name: 'Create user' }))
    return screen.findByRole('dialog', { name: 'Create user' })
  }

  it('blocks an invalid username without calling the API', async () => {
    let posted = false
    serveUsers([ADMIN])
    server.use(
      http.post('/api/users', () => {
        posted = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openCreate()

    await userEvent.type(within(dialog).getByLabelText(/^Username/), 'no spaces!')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }))

    expect(within(dialog).getByText("Use 3–50 letters, digits, '.', '_' or '-'")).toBeInTheDocument()
    expect(posted).toBe(false)
  })

  it('creates the user and shows the temporary password once', async () => {
    let body
    const created = { id: 4, username: 'nina', role: 'VIEWER', mustChangePassword: true, createdAt: '2026-11-06T09:00:00Z' }
    serveUsers([ADMIN])
    server.use(
      http.post('/api/users', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ user: created, temporaryPassword: 'q3Zk_8vL-pX1mN0a' }, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openCreate()

    await userEvent.type(within(dialog).getByLabelText(/^Username/), 'nina')
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Role' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Viewer' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }))

    const passwordDialog = await screen.findByRole('dialog', { name: 'Temporary password for nina' })
    expect(body).toEqual({ username: 'nina', role: 'VIEWER' })
    expect(within(passwordDialog).getByText('q3Zk_8vL-pX1mN0a')).toBeInTheDocument()
    expect(within(passwordDialog).getByText(/will not be shown again/i)).toBeInTheDocument()

    await userEvent.click(within(passwordDialog).getByRole('button', { name: 'Copy' }))
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('q3Zk_8vL-pX1mN0a')
    expect(await within(passwordDialog).findByText('Copied')).toBeInTheDocument()

    await userEvent.click(within(passwordDialog).getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(within(rowFor('nina')).getByText('Must change password')).toBeInTheDocument()
  })

  it('shows a duplicate username error in the form', async () => {
    serveUsers([ADMIN])
    server.use(http.post('/api/users', () => HttpResponse.json({ message: 'Username already exists' }, { status: 409 })))
    renderPage()
    const dialog = await openCreate()

    await userEvent.type(within(dialog).getByLabelText(/^Username/), 'admin')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Username already exists')
  })
})

describe('UsersPage reset password', () => {
  it('asks first, then shows the new temporary password and flags the user', async () => {
    let reset = false
    serveUsers([ADMIN, { ...VIC }])
    server.use(
      http.post('/api/users/3/reset-password', () => {
        reset = true
        return HttpResponse.json({ user: { ...VIC, mustChangePassword: true }, temporaryPassword: 'n3w-Temp_pass1234' })
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for vic' }))
    const confirm = await screen.findByRole('dialog', { name: 'Reset password for vic?' })
    expect(within(confirm).getByText(/signed out/i)).toBeInTheDocument()
    expect(reset).toBe(false)
    await userEvent.click(within(confirm).getByRole('button', { name: 'Reset password' }))

    const passwordDialog = await screen.findByRole('dialog', { name: 'Temporary password for vic' })
    expect(within(passwordDialog).getByText('n3w-Temp_pass1234')).toBeInTheDocument()
    await userEvent.click(within(passwordDialog).getByRole('button', { name: 'Done' }))
    expect(within(rowFor('vic')).getByText('Must change password')).toBeInTheDocument()
  })

  it('changes nothing when cancelled', async () => {
    let reset = false
    serveUsers([ADMIN, VIC])
    server.use(
      http.post('/api/users/3/reset-password', () => {
        reset = true
        return HttpResponse.json({})
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for vic' }))
    const confirm = await screen.findByRole('dialog', { name: 'Reset password for vic?' })
    await userEvent.click(within(confirm).getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(reset).toBe(false)
  })
})
