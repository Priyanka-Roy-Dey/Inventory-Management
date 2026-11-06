import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import AlertsPage from './AlertsPage'

const PAPER_ALERT = {
  id: 'a1',
  itemId: 1,
  sku: 'PAP-A4',
  itemName: 'A4 paper',
  quantity: 8,
  reorderLevel: 10,
  cause: 'STOCK_MOVEMENT',
  occurredAt: '2026-11-06T09:00:00Z',
  currentQuantity: 8,
  currentReorderLevel: 10,
  stillLow: true,
}
const CABLE_ALERT = {
  id: 'a2',
  itemId: 2,
  sku: 'CBL-USB',
  itemName: 'USB cable',
  quantity: 2,
  reorderLevel: 5,
  cause: 'CREATED',
  occurredAt: '2026-11-05T09:00:00Z',
  currentQuantity: 40,
  currentReorderLevel: 5,
  stillLow: false,
}

function pageOf(alerts, { page = 0, totalItems = alerts.length } = {}) {
  return { alerts, page, size: 20, totalItems, totalPages: Math.ceil(totalItems / 20) }
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
        <AlertsPage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

function serveAlerts(alerts, options) {
  const queries = []
  server.use(
    http.get('/api/alerts', ({ request }) => {
      queries.push(Object.fromEntries(new URL(request.url).searchParams))
      return HttpResponse.json(pageOf(alerts, options))
    }),
  )
  return queries
}

function rowFor(sku) {
  return screen.getByText(sku).closest('tr')
}

describe('AlertsPage', () => {
  it('shows a loading indicator, then the alerts newest first', async () => {
    const queries = serveAlerts([PAPER_ALERT, CABLE_ALERT])
    renderPage()

    expect(screen.getByRole('progressbar', { name: 'Loading alerts' })).toBeInTheDocument()

    const table = await screen.findByRole('table', { name: 'Low-stock alerts' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('PAP-A4')).toBeInTheDocument()
    expect(within(rows[2]).getByText('CBL-USB')).toBeInTheDocument()
    expect(queries[0]).toEqual({ page: '0', size: '20' })
  })

  it('explains each alert and whether the item is still low', async () => {
    serveAlerts([PAPER_ALERT, CABLE_ALERT])
    renderPage()

    await screen.findByText('PAP-A4')
    const paper = rowFor('PAP-A4')
    expect(within(paper).getByText('A4 paper')).toBeInTheDocument()
    expect(within(paper).getByText('8 / 10')).toBeInTheDocument()
    expect(within(paper).getByText('Stock movement')).toBeInTheDocument()
    expect(within(paper).getByText('Still low')).toBeInTheDocument()

    const cable = rowFor('CBL-USB')
    expect(within(cable).getByText('Created already low')).toBeInTheDocument()
    expect(within(cable).getByText('Restocked (40)')).toBeInTheDocument()
  })

  it('filters by search, cause and still low', async () => {
    const queries = serveAlerts([PAPER_ALERT])
    renderPage()
    await screen.findByText('PAP-A4')

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search SKU or name' }), 'pap')
    await waitFor(() => expect(queries.at(-1)).toMatchObject({ search: 'pap', page: '0' }))

    await userEvent.click(screen.getByRole('combobox', { name: 'Cause' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Reorder level raised' }))
    await waitFor(() => expect(queries.at(-1)).toMatchObject({ cause: 'REORDER_LEVEL_RAISED' }))

    await userEvent.click(screen.getByRole('switch', { name: 'Still low only' }))
    await waitFor(() => expect(queries.at(-1)).toMatchObject({ stillLow: 'true' }))
  })

  it('moves between pages', async () => {
    const queries = serveAlerts([PAPER_ALERT], { totalItems: 30 })
    renderPage()
    await screen.findByText('PAP-A4')

    await userEvent.click(screen.getByRole('button', { name: 'Go to next page' }))

    await waitFor(() => expect(queries.at(-1)).toMatchObject({ page: '1' }))
  })

  it('says when there are no alerts, and when nothing matches', async () => {
    serveAlerts([])
    renderPage()

    expect(await screen.findByText(/No low-stock alerts yet/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('switch', { name: 'Still low only' }))
    expect(await screen.findByText('No alerts match your filters.')).toBeInTheDocument()
  })

  it('shows an error with a retry button', async () => {
    let calls = 0
    server.use(
      http.get('/api/alerts', () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ message: 'Unexpected error' }, { status: 500 })
          : HttpResponse.json(pageOf([PAPER_ALERT]))
      }),
    )
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load alerts: Unexpected error')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('PAP-A4')).toBeInTheDocument()
  })
})
