import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import App from './App'
import AuthProvider from './auth/AuthProvider'
import { server } from './test/server'

// The Dashboard and Items pages load data; routing tests only need them to render.
beforeEach(() => {
  server.use(
    http.get('/api/items', () => HttpResponse.json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })),
    http.get('/api/items/categories', () => HttpResponse.json([])),
    http.get('/api/dashboard', () =>
      HttpResponse.json({
        totalItems: 0,
        lowStockCount: 0,
        totalUnits: 0,
        stockValue: 0,
        movementsLast24Hours: 0,
        lowStockItems: [],
        recentMovements: [],
      }),
    ),
  )
})

function signInAs(role, mustChangePassword = false) {
  const session = {
    accessToken: 'token-1',
    user: { username: `${role.toLowerCase()}-user`, role, mustChangePassword },
  }
  sessionStorage.setItem('inventory.session', JSON.stringify(session))
}

function renderAt(path, role = 'ADMIN') {
  if (role) {
    signInAs(role)
  }
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('App routing', () => {
  it.each([
    ['/', 'Dashboard'],
    ['/items', 'Items'],
    ['/alerts', 'Alerts'],
    ['/import', 'Import items'],
    ['/users', 'Users'],
  ])('shows the %s page', (path, heading) => {
    renderAt(path)

    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
  })

  it('shows a not-found page for an unknown route', () => {
    renderAt('/does-not-exist')

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /back to dashboard/i })).toHaveAttribute('href', '/')
  })
})

describe('App navigation', () => {
  it('links to every main page for an Admin', () => {
    renderAt('/')

    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(nav).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Items' })).toHaveAttribute('href', '/items')
    expect(screen.getByRole('link', { name: 'Alerts' })).toHaveAttribute('href', '/alerts')
    expect(screen.getByRole('link', { name: 'Import' })).toHaveAttribute('href', '/import')
    expect(screen.getByRole('link', { name: 'Users' })).toHaveAttribute('href', '/users')
  })

  it('marks the current page link', () => {
    renderAt('/items')

    expect(screen.getByRole('link', { name: 'Items' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current')
  })

  it('hides Users from Staff', () => {
    renderAt('/', 'STAFF')

    expect(screen.getByRole('link', { name: 'Import' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })

  it('hides Import and Users from a Viewer', () => {
    renderAt('/', 'VIEWER')

    expect(screen.getByRole('link', { name: 'Items' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Import' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })
})

describe('App access control', () => {
  it('sends a signed-out user to the sign-in page', () => {
    renderAt('/items', null)

    expect(screen.getByRole('heading', { level: 1, name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
  })

  it.each([
    ['/users', 'STAFF'],
    ['/users', 'VIEWER'],
    ['/import', 'VIEWER'],
  ])('blocks %s for a %s', (path, role) => {
    renderAt(path, role)

    expect(screen.getByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument()
  })

  it('shows who is signed in and signs out', async () => {
    renderAt('/', 'STAFF')

    expect(screen.getByText('staff-user (Staff)')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Sign in' })).toBeInTheDocument()
  })
})

describe('App forced password change', () => {
  function renderFlaggedAt(path) {
    signInAs('VIEWER', true)
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </AuthProvider>,
    )
  }

  it.each(['/', '/items', '/users'])('sends a user who must change their password from %s to the form', (path) => {
    renderFlaggedAt(path)

    expect(screen.getByRole('heading', { level: 1, name: 'Set a new password' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
  })

  it('lets other signed-in users open the form', () => {
    renderAt('/change-password', 'STAFF')

    expect(screen.getByRole('heading', { level: 1, name: 'Set a new password' })).toBeInTheDocument()
  })
})
