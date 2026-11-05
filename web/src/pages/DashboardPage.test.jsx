import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import DashboardPage from './DashboardPage'

const SUMMARY = {
  totalItems: 42,
  lowStockCount: 2,
  totalUnits: 1250,
  stockValue: 12345.5,
  movementsLast24Hours: 7,
  lowStockItems: [
    { id: 2, sku: 'CBL-USB', name: 'USB cable', quantity: 0, reorderLevel: 5 },
    { id: 3, sku: 'PAP-A3', name: 'A3 paper', quantity: 9, reorderLevel: 10 },
  ],
  recentMovements: [
    {
      id: 21,
      itemId: 1,
      sku: 'PAP-A4',
      itemName: 'A4 paper',
      type: 'OUT',
      quantityChange: -5,
      createdBy: 'staff',
      createdAt: '2026-11-05T09:00:00Z',
    },
    {
      id: 20,
      itemId: 2,
      sku: 'CBL-USB',
      itemName: 'USB cable',
      type: 'IN',
      quantityChange: 20,
      createdBy: 'admin',
      createdAt: '2026-11-04T09:00:00Z',
    },
  ],
}
const EMPTY = {
  totalItems: 0,
  lowStockCount: 0,
  totalUnits: 0,
  stockValue: 0,
  movementsLast24Hours: 0,
  lowStockItems: [],
  recentMovements: [],
}

function renderPage() {
  sessionStorage.setItem(
    'inventory.session',
    JSON.stringify({
      accessToken: 'token-1',
      refreshToken: 'refresh-1',
      user: { username: 'viewer', role: 'VIEWER', mustChangePassword: false },
    }),
  )
  render(
    <AuthProvider>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

function serve(summary) {
  server.use(http.get('/api/dashboard', () => HttpResponse.json(summary)))
}

function card(title) {
  return screen.getByRole('group', { name: title })
}

describe('DashboardPage', () => {
  it('shows a loading indicator, then the summary cards', async () => {
    serve(SUMMARY)
    renderPage()

    expect(screen.getByRole('progressbar', { name: 'Loading dashboard' })).toBeInTheDocument()

    expect(await screen.findByRole('group', { name: 'Items' })).toHaveTextContent('42')
    expect(card('Low stock')).toHaveTextContent('2')
    expect(card('Units in stock')).toHaveTextContent('1,250')
    expect(card('Stock value')).toHaveTextContent('12,345.50')
    expect(card('Movements (24 h)')).toHaveTextContent('7')
  })

  it('lists the low-stock items most urgent first', async () => {
    serve(SUMMARY)
    renderPage()

    const table = await screen.findByRole('table', { name: 'Low-stock items' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('CBL-USB')).toBeInTheDocument()
    expect(within(rows[1]).getByText('USB cable')).toBeInTheDocument()
    expect(within(rows[1]).getByText('0 / 5')).toBeInTheDocument()
    expect(within(rows[2]).getByText('PAP-A3')).toBeInTheDocument()
  })

  it('lists the recent movements newest first', async () => {
    serve(SUMMARY)
    renderPage()

    const table = await screen.findByRole('table', { name: 'Recent movements' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('PAP-A4')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Out')).toBeInTheDocument()
    expect(within(rows[1]).getByText('-5')).toBeInTheDocument()
    expect(within(rows[1]).getByText('staff')).toBeInTheDocument()
    expect(within(rows[2]).getByText('+20')).toBeInTheDocument()
  })

  it('highlights the Low stock card only when something is low', async () => {
    serve(SUMMARY)
    renderPage()

    expect(await screen.findByRole('group', { name: 'Low stock' })).toHaveAttribute('data-alert', 'true')
  })

  it('explains empty lists', async () => {
    serve(EMPTY)
    renderPage()

    expect(await screen.findByText('Nothing is low on stock.')).toBeInTheDocument()
    expect(screen.getByText('No movements yet.')).toBeInTheDocument()
    expect(card('Stock value')).toHaveTextContent('0.00')
    expect(card('Low stock')).toHaveAttribute('data-alert', 'false')
  })

  it('shows an error with a retry button', async () => {
    let calls = 0
    server.use(
      http.get('/api/dashboard', () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ message: 'Unexpected error' }, { status: 500 })
          : HttpResponse.json(SUMMARY)
      }),
    )
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the dashboard: Unexpected error')

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('group', { name: 'Items' })).toHaveTextContent('42')
  })
})
