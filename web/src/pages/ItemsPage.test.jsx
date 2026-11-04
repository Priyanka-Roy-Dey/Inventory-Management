import { render, screen, waitFor, within } from '@testing-library/react'
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

function rowFor(sku) {
  return screen.getByText(sku).closest('tr')
}

describe('ItemsPage row actions', () => {
  it('shows Edit to Staff but Delete only to Admin', async () => {
    serveItems([PAPER])
    renderPage('STAFF')

    await screen.findByText('PAP-A4')
    expect(screen.getByRole('button', { name: 'Edit PAP-A4' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete PAP-A4' })).not.toBeInTheDocument()
  })

  it('shows both to Admin and neither to Viewer', async () => {
    serveItems([PAPER])
    renderPage('ADMIN')
    expect(await screen.findByRole('button', { name: 'Delete PAP-A4' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit PAP-A4' })).toBeInTheDocument()
  })

  it('offers no actions to a Viewer', async () => {
    serveItems([PAPER])
    renderPage('VIEWER')

    await screen.findByText('PAP-A4')
    expect(screen.queryByRole('button', { name: 'Edit PAP-A4' })).not.toBeInTheDocument()
  })
})

describe('ItemsPage Edit item', () => {
  async function openEdit() {
    await userEvent.click(await screen.findByRole('button', { name: 'Edit PAP-A4' }))
    return screen.findByRole('dialog', { name: 'Edit item' })
  }

  it('starts from the current values and keeps SKU and quantity read-only', async () => {
    serveItems([PAPER])
    renderPage()
    const dialog = await openEdit()

    expect(within(dialog).getByLabelText(/^SKU/)).toHaveValue('PAP-A4')
    expect(within(dialog).getByLabelText(/^SKU/)).toBeDisabled()
    expect(within(dialog).getByLabelText(/^Quantity/)).toHaveValue('40')
    expect(within(dialog).getByLabelText(/^Quantity/)).toBeDisabled()
    expect(within(dialog).getByLabelText(/^Name/)).toHaveValue('A4 paper')
    expect(within(dialog).getByLabelText(/^Unit price/)).toHaveValue('4.50')
  })

  it('saves the changes with the version it read and updates the row', async () => {
    let body
    serveItems([PAPER])
    server.use(
      http.put('/api/items/1', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...PAPER, name: 'A4 paper, 80gsm', version: 1 })
      }),
    )
    renderPage()
    const dialog = await openEdit()

    await fillForm(dialog, { Name: 'A4 paper, 80gsm' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('A4 paper, 80gsm')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(body).toEqual({
      name: 'A4 paper, 80gsm',
      category: 'Stationery',
      reorderLevel: 10,
      unitPrice: 4.5,
      version: 0,
    })
  })

  it('blocks an invalid edit', async () => {
    let called = false
    serveItems([PAPER])
    server.use(
      http.put('/api/items/1', () => {
        called = true
        return HttpResponse.json(PAPER)
      }),
    )
    renderPage()
    const dialog = await openEdit()

    await userEvent.clear(within(dialog).getByLabelText(/^Name/))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(within(dialog).getByText('Name is required')).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('reloads the latest values after someone else saved first', async () => {
    const latest = { ...PAPER, name: 'Renamed by someone else', version: 1 }
    const versionsSent = []
    serveItems([PAPER])
    server.use(
      http.put('/api/items/1', async ({ request }) => {
        const { version } = await request.json()
        versionsSent.push(version)
        return version === 1
          ? HttpResponse.json({ ...latest, category: 'Office', version: 2 })
          : HttpResponse.json(
              { message: 'Item was changed by someone else. Reload it and try again.' },
              { status: 409 },
            )
      }),
      http.get('/api/items/1', () => HttpResponse.json(latest)),
    )
    renderPage()
    const dialog = await openEdit()

    await fillForm(dialog, { Category: 'Office' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Item was changed by someone else')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Reload' }))
    expect(await within(dialog).findByDisplayValue('Renamed by someone else')).toBeInTheDocument()
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument()

    await fillForm(dialog, { Category: 'Office' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Office')).toBeInTheDocument()
    expect(versionsSent).toEqual([0, 1])
  })
})

describe('ItemsPage Delete item', () => {
  async function openDelete() {
    await userEvent.click(await screen.findByRole('button', { name: 'Delete PAP-A4' }))
    return screen.findByRole('dialog', { name: 'Delete PAP-A4?' })
  }

  it('removes the item after confirmation', async () => {
    let deleted = false
    serveItems([CABLE, PAPER])
    server.use(
      http.delete('/api/items/1', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderPage('ADMIN')
    const dialog = await openDelete()

    expect(within(dialog).getByText(/its SKU can't be reused/i)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText('PAP-A4')).not.toBeInTheDocument())
    expect(deleted).toBe(true)
    expect(screen.getByText('CBL-USB')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keeps the item when cancelled', async () => {
    let deleted = false
    serveItems([PAPER])
    server.use(
      http.delete('/api/items/1', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderPage('ADMIN')
    const dialog = await openDelete()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(rowFor('PAP-A4')).toBeInTheDocument()
    expect(deleted).toBe(false)
  })

  it('shows the error when the delete fails', async () => {
    serveItems([PAPER])
    server.use(
      http.delete('/api/items/1', () => HttpResponse.json({ message: 'Item not found' }, { status: 404 })),
    )
    renderPage('ADMIN')
    const dialog = await openDelete()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Item not found')
  })
})
