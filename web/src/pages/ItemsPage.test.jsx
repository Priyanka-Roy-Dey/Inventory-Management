import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import ItemsPage from './ItemsPage'

const PAPER = {
  id: 1,
  sku: 'PAP-A4',
  name: 'A4 paper',
  category: 'Stationery',
  quantity: 40,
  reorderLevel: 10,
  unitPrice: 4.5,
  lowStock: false,
  version: 0,
}
const CABLE = {
  id: 2,
  sku: 'CBL-USB',
  name: 'USB cable',
  category: 'Cables',
  quantity: 5,
  reorderLevel: 5,
  unitPrice: 3.99,
  lowStock: true,
  version: 0,
}

function renderPage(role = 'STAFF') {
  sessionStorage.setItem(
    'inventory.session',
    JSON.stringify({
      accessToken: 'token-1',
      refreshToken: 'refresh-1',
      user: { username: 'someone', role, mustChangePassword: false },
    }),
  )
  render(
    <AuthProvider>
      <MemoryRouter>
        <ItemsPage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

function serveItems(items) {
  server.use(http.get('/api/items', () => HttpResponse.json(items)))
}

describe('ItemsPage list', () => {
  it('shows a loading indicator, then the items', async () => {
    serveItems([CABLE, PAPER])
    renderPage()

    expect(screen.getByRole('progressbar', { name: 'Loading items' })).toBeInTheDocument()

    const table = await screen.findByRole('table', { name: 'Items' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('CBL-USB')).toBeInTheDocument()
    expect(within(rows[2]).getByText('PAP-A4')).toBeInTheDocument()
    expect(within(rows[2]).getByText('A4 paper')).toBeInTheDocument()
    expect(within(rows[2]).getByText('4.50')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('marks low-stock items', async () => {
    serveItems([CABLE, PAPER])
    renderPage()

    const rows = within(await screen.findByRole('table', { name: 'Items' })).getAllByRole('row')
    expect(within(rows[1]).getByText('Low stock')).toBeInTheDocument()
    expect(within(rows[2]).queryByText('Low stock')).not.toBeInTheDocument()
  })

  it('explains when there are no items yet', async () => {
    serveItems([])
    renderPage()

    expect(await screen.findByText('No items yet.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('shows an error with a retry button', async () => {
    let calls = 0
    server.use(
      http.get('/api/items', () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ message: 'Unexpected error' }, { status: 500 })
          : HttpResponse.json([PAPER])
      }),
    )
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load items: Unexpected error')

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('PAP-A4')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each(['ADMIN', 'STAFF'])('lets %s add items', async (role) => {
    serveItems([])
    renderPage(role)

    expect(await screen.findByRole('button', { name: 'Add item' })).toBeInTheDocument()
  })

  it('does not offer Add item to a Viewer', async () => {
    serveItems([])
    renderPage('VIEWER')

    await screen.findByText('No items yet.')
    expect(screen.queryByRole('button', { name: 'Add item' })).not.toBeInTheDocument()
  })
})

async function openAddItem() {
  await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))
  return screen.findByRole('dialog', { name: 'Add item' })
}

async function fillForm(dialog, values) {
  for (const [label, value] of Object.entries(values)) {
    const field = within(dialog).getByLabelText(new RegExp(`^${label}`, 'i'))
    await userEvent.clear(field)
    await userEvent.type(field, value)
  }
}

const VALID = {
  SKU: 'pap-a4',
  Name: 'A4 paper',
  Category: 'Stationery',
  Quantity: '40',
  'Reorder level': '10',
  'Unit price': '4.50',
}

describe('ItemsPage Add item', () => {
  it('blocks an invalid submit and does not call the API', async () => {
    let posted = false
    serveItems([])
    server.use(
      http.post('/api/items', () => {
        posted = true
        return HttpResponse.json(PAPER, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openAddItem()

    await fillForm(dialog, { SKU: 'has space', Quantity: '-1', 'Reorder level': '1.5', 'Unit price': '1.999' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(within(dialog).getByText("SKU may contain only letters, digits, '.', '_' and '-'")).toBeInTheDocument()
    expect(within(dialog).getByText('Name is required')).toBeInTheDocument()
    expect(within(dialog).getByText('Category is required')).toBeInTheDocument()
    expect(within(dialog).getAllByText('Enter a whole number of 0 or more')).toHaveLength(2)
    expect(within(dialog).getByText('Enter a price of 0 or more with up to 2 decimals')).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/^SKU/)).toHaveAttribute('aria-invalid', 'true')
    expect(posted).toBe(false)
  })

  it('creates the item and shows it in the list', async () => {
    let body
    serveItems([CABLE])
    server.use(
      http.post('/api/items', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(PAPER, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openAddItem()

    await fillForm(dialog, VALID)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('PAP-A4')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(body).toEqual({
      sku: 'pap-a4',
      name: 'A4 paper',
      category: 'Stationery',
      quantity: 40,
      reorderLevel: 10,
      unitPrice: 4.5,
    })
    const rows = within(screen.getByRole('table', { name: 'Items' })).getAllByRole('row')
    expect(within(rows[2]).getByText('PAP-A4')).toBeInTheDocument()
  })

  it('shows a duplicate SKU error in the form', async () => {
    serveItems([])
    server.use(
      http.post('/api/items', () => HttpResponse.json({ message: 'SKU already exists' }, { status: 409 })),
    )
    renderPage()
    const dialog = await openAddItem()

    await fillForm(dialog, VALID)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('SKU already exists')
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('discards the form on Cancel', async () => {
    serveItems([])
    renderPage()
    let dialog = await openAddItem()
    await fillForm(dialog, { SKU: 'TEMP-1' })

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    dialog = await openAddItem()

    expect(within(dialog).getByLabelText(/^SKU/)).toHaveValue('')
  })
})
