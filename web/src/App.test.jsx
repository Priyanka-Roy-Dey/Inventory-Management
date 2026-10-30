import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'

function renderAt(path) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
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
  it('links to every main page', () => {
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
})
