import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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

const LOW_PAPER = { ...PAPER, quantity: 5, lowStock: true, version: 1 }
const OLDER = {
  id: 11,
  type: 'IN',
  quantityChange: 40,
  quantityBefore: 0,
  quantityAfter: 40,
  reason: 'First delivery',
  createdBy: 'admin',
  createdAt: '2026-11-01T09:00:00Z',
}
const NEWER = {
  id: 12,
  type: 'ADJUST',
  quantityChange: -2,
  quantityBefore: 40,
  quantityAfter: 38,
  reason: 'Damaged',
  createdBy: 'staff',
  createdAt: '2026-11-02T09:00:00Z',
}

describe('ItemsPage Stock', () => {
  function serveHistory(movements) {
    server.use(http.get('/api/items/1/movements', () => HttpResponse.json(movements)))
  }

  async function openStock() {
    await userEvent.click(await screen.findByRole('button', { name: 'Stock PAP-A4' }))
    return screen.findByRole('dialog', { name: 'Stock: PAP-A4' })
  }

  async function record(dialog, { type, quantity, reason }) {
    if (type) {
      await userEvent.click(within(dialog).getByRole('radio', { name: type }))
    }
    const quantityField = within(dialog).getByLabelText(/^Quantity/)
    await userEvent.clear(quantityField)
    if (quantity) {
      await userEvent.type(quantityField, quantity)
    }
    if (reason) {
      await userEvent.type(within(dialog).getByLabelText(/^Reason/), reason)
    }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record' }))
  }

  it('is offered to Staff and Admin but not to a Viewer', async () => {
    serveItems([PAPER])
    renderPage('VIEWER')

    await screen.findByText('PAP-A4')
    expect(screen.queryByRole('button', { name: 'Stock PAP-A4' })).not.toBeInTheDocument()
  })

  it('shows the current quantity and the history, newest first', async () => {
    serveItems([PAPER])
    serveHistory([NEWER, OLDER])
    renderPage('STAFF')
    const dialog = await openStock()

    expect(card(dialog, 'In stock')).toHaveTextContent('40')
    const history = await within(dialog).findByRole('table', { name: 'Stock history' })
    const rows = within(history).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Adjust')).toBeInTheDocument()
    expect(within(rows[1]).getByText('-2')).toBeInTheDocument()
    expect(within(rows[1]).getByText('40 → 38')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Damaged')).toBeInTheDocument()
    expect(within(rows[1]).getByText('staff')).toBeInTheDocument()
    expect(within(rows[2]).getByText('+40')).toBeInTheDocument()
  })

  function card(dialog, title) {
    return within(dialog).getByRole('group', { name: title })
  }

  it('summarises the stock in cards', async () => {
    const issued = { ...NEWER, id: 15, type: 'OUT', quantityChange: -7, quantityBefore: 38, quantityAfter: 31 }
    const received = { ...OLDER, id: 16, quantityChange: 9, quantityBefore: 31, quantityAfter: 40 }
    serveItems([PAPER])
    serveHistory([received, issued, NEWER, OLDER])
    renderPage()
    const dialog = await openStock()
    await within(dialog).findByRole('table', { name: 'Stock history' })

    expect(card(dialog, 'In stock')).toHaveTextContent('40')
    expect(card(dialog, 'In stock')).toHaveTextContent('OK')
    expect(card(dialog, 'Reorder level')).toHaveTextContent('10')
    expect(card(dialog, 'Received')).toHaveTextContent('+49')
    expect(card(dialog, 'Issued')).toHaveTextContent('-7')
    expect(card(dialog, 'Adjusted')).toHaveTextContent('-2')
  })

  it('marks the In stock card when stock is low', async () => {
    serveItems([LOW_PAPER])
    serveHistory([])
    renderPage()
    const dialog = await openStock()

    expect(card(dialog, 'In stock')).toHaveTextContent('5')
    expect(card(dialog, 'In stock')).toHaveTextContent('Low stock')
    expect(await within(dialog).findByText('No movements yet.')).toBeInTheDocument()
    expect(card(dialog, 'Received')).toHaveTextContent('0')
  })

  it('says when there is no history yet', async () => {
    serveItems([PAPER])
    serveHistory([])
    renderPage()
    const dialog = await openStock()

    expect(await within(dialog).findByText('No movements yet.')).toBeInTheDocument()
  })

  it('records a movement and updates the dialog and the row', async () => {
    let body
    const movement = { ...NEWER, id: 13, type: 'OUT', quantityChange: -35, quantityBefore: 40, quantityAfter: 5 }
    serveItems([PAPER])
    serveHistory([OLDER])
    server.use(
      http.post('/api/items/1/movements', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ movement, item: LOW_PAPER }, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openStock()
    await within(dialog).findByRole('table', { name: 'Stock history' })

    await record(dialog, { type: 'Out', quantity: '35', reason: 'Order 12' })

    expect(await within(dialog).findByText('Stock updated: 40 → 5')).toBeInTheDocument()
    expect(card(dialog, 'In stock')).toHaveTextContent('5')
    expect(card(dialog, 'In stock')).toHaveTextContent('Low stock')
    expect(card(dialog, 'Issued')).toHaveTextContent('-35')
    expect(body).toEqual({ type: 'OUT', quantity: 35, reason: 'Order 12' })
    const rows = within(within(dialog).getByRole('table', { name: 'Stock history' })).getAllByRole('row')
    expect(within(rows[1]).getByText('-35')).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/^Quantity/)).toHaveValue('')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }))
    const itemRow = rowFor('PAP-A4')
    expect(within(itemRow).getByText('5')).toBeInTheDocument()
    expect(within(itemRow).getByText('Low stock')).toBeInTheDocument()
  })

  it('sends a signed change for an adjustment', async () => {
    let body
    serveItems([PAPER])
    serveHistory([])
    server.use(
      http.post('/api/items/1/movements', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(
          {
            movement: { ...NEWER, id: 14, quantityChange: -3, quantityBefore: 40, quantityAfter: 37 },
            item: { ...PAPER, quantity: 37, version: 1 },
          },
          { status: 201 },
        )
      }),
    )
    renderPage()
    const dialog = await openStock()

    await record(dialog, { type: 'Adjust', quantity: '-3', reason: 'Stocktake' })

    expect(await within(dialog).findByText('Stock updated: 40 → 37')).toBeInTheDocument()
    expect(body).toEqual({ type: 'ADJUST', quantity: -3, reason: 'Stocktake' })
  })

  it('blocks invalid movements without calling the API', async () => {
    let posted = false
    serveItems([PAPER])
    serveHistory([])
    server.use(
      http.post('/api/items/1/movements', () => {
        posted = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openStock()

    await record(dialog, { type: 'Out', quantity: '0' })
    expect(within(dialog).getByText('Enter a whole number of 1 or more')).toBeInTheDocument()

    await record(dialog, { type: 'Adjust', quantity: '0' })
    expect(within(dialog).getByText('Enter a change such as 5 or -3, not 0')).toBeInTheDocument()
    expect(within(dialog).getByText('A reason is required for an adjustment')).toBeInTheDocument()

    expect(posted).toBe(false)
  })

  it('shows the server message when there is not enough stock', async () => {
    serveItems([PAPER])
    serveHistory([])
    server.use(
      http.post('/api/items/1/movements', () =>
        HttpResponse.json({ message: 'Not enough stock: only 40 available' }, { status: 409 }),
      ),
    )
    renderPage()
    const dialog = await openStock()

    await record(dialog, { type: 'Out', quantity: '99' })

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Not enough stock: only 40 available')
    expect(card(dialog, 'In stock')).toHaveTextContent('40')
  })

  it('shows an error when the history cannot be loaded', async () => {
    serveItems([PAPER])
    server.use(
      http.get('/api/items/1/movements', () => HttpResponse.json({ message: 'Unexpected error' }, { status: 500 })),
    )
    renderPage()
    const dialog = await openStock()

    expect(await within(dialog).findByText('Could not load the history: Unexpected error')).toBeInTheDocument()
  })
})

const IMAGE_URL = 'http://localhost:9090/inventory-images/items/1/abc.png?X-Amz-Signature=read'
const UPLOAD_URL = 'http://localhost:9090/inventory-images/items/1/new.png?X-Amz-Signature=write'

describe('ItemsPage images', () => {
  it('shows a thumbnail for items that have an image', async () => {
    serveItems([{ ...PAPER, imageUrl: IMAGE_URL }, CABLE])
    renderPage('VIEWER')

    expect(await screen.findByRole('img', { name: 'PAP-A4' })).toHaveAttribute('src', IMAGE_URL)
    expect(within(rowFor('CBL-USB')).queryByRole('img')).not.toBeInTheDocument()
  })
})

describe('ItemsPage Stock image', () => {
  const png = () => new File(['png-bytes'], 'paper.png', { type: 'image/png' })

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  function serveHistory() {
    server.use(http.get('/api/items/1/movements', () => HttpResponse.json([])))
  }

  async function openStock(user = userEvent) {
    await user.click(await screen.findByRole('button', { name: 'Stock PAP-A4' }))
    return screen.findByRole('dialog', { name: 'Stock: PAP-A4' })
  }

  it('shows the item image in the dialog', async () => {
    serveItems([{ ...PAPER, imageUrl: IMAGE_URL }])
    serveHistory()
    renderPage()
    const dialog = await openStock()

    expect(within(dialog).getByRole('img', { name: 'PAP-A4' })).toHaveAttribute('src', IMAGE_URL)
  })

  it('offers an image only when adding stock', async () => {
    serveItems([PAPER])
    serveHistory()
    renderPage()
    const dialog = await openStock()

    expect(within(dialog).getByLabelText('Image (optional)')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('radio', { name: 'Out' }))
    expect(within(dialog).queryByLabelText('Image (optional)')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('radio', { name: 'Adjust' }))
    expect(within(dialog).queryByLabelText('Image (optional)')).not.toBeInTheDocument()
  })

  it('previews the chosen image and can drop it again', async () => {
    serveItems([PAPER])
    serveHistory()
    renderPage()
    const dialog = await openStock()

    await userEvent.upload(within(dialog).getByLabelText('Image (optional)'), png())

    expect(within(dialog).getByRole('img', { name: 'Preview of paper.png' })).toHaveAttribute('src', 'blob:preview')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove chosen image' }))
    expect(within(dialog).queryByRole('img', { name: /Preview/ })).not.toBeInTheDocument()
  })

  it('rejects files that are not JPEG, PNG or WebP, or larger than 2 MB', async () => {
    const user = userEvent.setup({ applyAccept: false })
    serveItems([PAPER])
    serveHistory()
    renderPage()
    const dialog = await openStock(user)
    const input = within(dialog).getByLabelText('Image (optional)')

    await user.upload(input, new File(['gif'], 'anim.gif', { type: 'image/gif' }))
    expect(within(dialog).getByText('Choose a JPEG, PNG or WebP image')).toBeInTheDocument()

    await user.upload(input, new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }))
    expect(within(dialog).getByText('The image must be 2 MB or smaller')).toBeInTheDocument()
    expect(within(dialog).queryByRole('img', { name: /Preview/ })).not.toBeInTheDocument()
  })

  it('uploads the image to storage, then records the stock with it', async () => {
    const calls = []
    serveItems([PAPER])
    serveHistory()
    server.use(
      http.post('/api/items/1/image/upload-url', async ({ request }) => {
        calls.push(['upload-url', await request.json()])
        return HttpResponse.json({
          uploadUrl: UPLOAD_URL,
          key: 'items/1/new.png',
          contentType: 'image/png',
          expiresAt: '2026-11-05T10:10:00Z',
        })
      }),
      http.put('http://localhost:9090/inventory-images/items/1/new.png', async ({ request }) => {
        calls.push(['storage', request.headers.get('Content-Type'), await request.text()])
        return new HttpResponse(null, { status: 200 })
      }),
      http.post('/api/items/1/movements', async ({ request }) => {
        calls.push(['movement', await request.json()])
        return HttpResponse.json(
          {
            movement: { ...NEWER, id: 30, type: 'IN', quantityChange: 10, quantityBefore: 40, quantityAfter: 50 },
            item: { ...PAPER, quantity: 50, version: 2, imageUrl: IMAGE_URL },
          },
          { status: 201 },
        )
      }),
    )
    renderPage()
    const dialog = await openStock()

    await userEvent.upload(within(dialog).getByLabelText('Image (optional)'), png())
    await userEvent.type(within(dialog).getByLabelText(/^Quantity/), '10')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record' }))

    expect(await within(dialog).findByText('Stock updated: 40 → 50')).toBeInTheDocument()
    expect(calls).toEqual([
      ['upload-url', { contentType: 'image/png', size: 9 }],
      ['storage', 'image/png', 'png-bytes'],
      ['movement', { type: 'IN', quantity: 10, reason: null, imageKey: 'items/1/new.png' }],
    ])
    expect(within(dialog).getByRole('img', { name: 'PAP-A4' })).toHaveAttribute('src', IMAGE_URL)
    expect(within(dialog).queryByRole('img', { name: /Preview/ })).not.toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }))
    expect(within(rowFor('PAP-A4')).getByRole('img', { name: 'PAP-A4' })).toHaveAttribute('src', IMAGE_URL)
  })

  it('does not record the stock when the image upload fails', async () => {
    let recorded = false
    serveItems([PAPER])
    serveHistory()
    server.use(
      http.post('/api/items/1/image/upload-url', () =>
        HttpResponse.json({ uploadUrl: UPLOAD_URL, key: 'items/1/new.png', contentType: 'image/png' }),
      ),
      http.put('http://localhost:9090/inventory-images/items/1/new.png', () => new HttpResponse(null, { status: 500 })),
      http.post('/api/items/1/movements', () => {
        recorded = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openStock()

    await userEvent.upload(within(dialog).getByLabelText('Image (optional)'), png())
    await userEvent.type(within(dialog).getByLabelText(/^Quantity/), '10')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Could not upload the image. The stock was not changed.',
    )
    expect(recorded).toBe(false)
  })
})

describe('ItemsPage Add item image', () => {
  const NEW_UPLOAD_URL = 'http://localhost:9090/inventory-images/new-items/abc.png?X-Amz-Signature=write'
  const png = () => new File(['png-bytes'], 'pen.png', { type: 'image/png' })

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('offers an image when adding an item but not when editing', async () => {
    serveItems([PAPER])
    renderPage()

    let dialog = await openAddItem()
    expect(within(dialog).getByLabelText('Image (optional)')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    await userEvent.click(screen.getByRole('button', { name: 'Edit PAP-A4' }))
    dialog = await screen.findByRole('dialog', { name: 'Edit item' })
    expect(within(dialog).queryByLabelText('Image (optional)')).not.toBeInTheDocument()
  })

  it('uploads the image, then creates the item with it', async () => {
    const calls = []
    serveItems([])
    server.use(
      http.post('/api/items/image/upload-url', async ({ request }) => {
        calls.push(['upload-url', await request.json()])
        return HttpResponse.json({ uploadUrl: NEW_UPLOAD_URL, key: 'new-items/abc.png', contentType: 'image/png' })
      }),
      http.put('http://localhost:9090/inventory-images/new-items/abc.png', async ({ request }) => {
        calls.push(['storage', request.headers.get('Content-Type'), await request.text()])
        return new HttpResponse(null, { status: 200 })
      }),
      http.post('/api/items', async ({ request }) => {
        calls.push(['create', await request.json()])
        return HttpResponse.json({ ...PAPER, imageUrl: IMAGE_URL }, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openAddItem()

    await fillForm(dialog, VALID)
    await userEvent.upload(within(dialog).getByLabelText('Image (optional)'), png())
    expect(within(dialog).getByRole('img', { name: 'Preview of pen.png' })).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('img', { name: 'PAP-A4' })).toHaveAttribute('src', IMAGE_URL)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(calls).toEqual([
      ['upload-url', { contentType: 'image/png', size: 9 }],
      ['storage', 'image/png', 'png-bytes'],
      [
        'create',
        {
          sku: 'pap-a4',
          name: 'A4 paper',
          category: 'Stationery',
          quantity: 40,
          reorderLevel: 10,
          unitPrice: 4.5,
          imageKey: 'new-items/abc.png',
        },
      ],
    ])
  })

  it('does not create the item when the image upload fails', async () => {
    let created = false
    serveItems([])
    server.use(
      http.post('/api/items/image/upload-url', () =>
        HttpResponse.json({ uploadUrl: NEW_UPLOAD_URL, key: 'new-items/abc.png', contentType: 'image/png' }),
      ),
      http.put('http://localhost:9090/inventory-images/new-items/abc.png', () => new HttpResponse(null, { status: 500 })),
      http.post('/api/items', () => {
        created = true
        return HttpResponse.json(PAPER, { status: 201 })
      }),
    )
    renderPage()
    const dialog = await openAddItem()

    await fillForm(dialog, VALID)
    await userEvent.upload(within(dialog).getByLabelText('Image (optional)'), png())
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Could not upload the image. The item was not created.',
    )
    expect(created).toBe(false)
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('does not upload anything when the form is invalid', async () => {
    serveItems([])
    renderPage()
    const dialog = await openAddItem()

    await userEvent.upload(within(dialog).getByLabelText('Image (optional)'), png())
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(within(dialog).getByText('Name is required')).toBeInTheDocument()
  })
})
