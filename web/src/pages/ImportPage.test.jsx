import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import AuthProvider from '../auth/AuthProvider'
import { server } from '../test/server'
import ImportPage from './ImportPage'

const csv = (content = 'sku,name\n', name = 'items.csv') => new File([content], name, { type: 'text/csv' })

function renderPage() {
  sessionStorage.setItem(
    'inventory.session',
    JSON.stringify({
      accessToken: 'token-1',
      refreshToken: 'refresh-1',
      user: { username: 'staff', role: 'STAFF', mustChangePassword: false },
    }),
  )
  render(
    <AuthProvider>
      <MemoryRouter>
        <ImportPage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('ImportPage', () => {
  it('explains the file format and offers a sample', () => {
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Import items' })).toBeInTheDocument()
    const columns = screen.getByRole('table', { name: 'Required columns' })
    for (const column of ['sku', 'name', 'category', 'quantity', 'reorder_level', 'unit_price']) {
      expect(within(columns).getByText(column)).toBeInTheDocument()
    }
    expect(screen.getByRole('link', { name: 'Download sample CSV' })).toHaveAttribute('href', '/sample-import.csv')
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled()
  })

  it('refuses files that are not CSV or are larger than 10 MB', async () => {
    const user = userEvent.setup({ applyAccept: false })
    renderPage()
    const input = screen.getByLabelText('CSV file')

    await user.upload(input, new File(['x'], 'items.xlsx', { type: 'application/vnd.ms-excel' }))
    expect(screen.getByText('Choose a .csv file')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled()

    await user.upload(input, csv('x'.repeat(10 * 1024 * 1024 + 1)))
    expect(screen.getByText('The file must be 10 MB or smaller')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled()
  })

  it('uploads the file and shows what was imported and which rows failed', async () => {
    let uploaded
    server.use(
      http.post('/api/items/import', async ({ request }) => {
        uploaded = await (await request.formData()).get('file').text()
        return HttpResponse.json({
          rows: 6,
          imported: 3,
          created: 2,
          updated: 1,
          restored: 0,
          errors: [
            { row: 6, sku: 'BAD-1', message: 'quantity: must be a whole number of 0 or more' },
            { row: 7, sku: null, message: 'sku: is required' },
          ],
        })
      }),
    )
    renderPage()

    await userEvent.upload(screen.getByLabelText('CSV file'), csv('sku,name\nPAP-A4,A4 paper\n'))
    expect(screen.getByText('items.csv')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Import' }))

    expect(await screen.findByText('Imported 3 items: 2 created, 1 updated, 0 restored.')).toBeInTheDocument()
    expect(uploaded).toBe('sku,name\nPAP-A4,A4 paper\n')
    expect(screen.getByText('2 of 6 rows were not imported. Fix them and import the file again.')).toBeInTheDocument()
    const errors = screen.getByRole('table', { name: 'Rows not imported' })
    const rows = within(errors).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('6')).toBeInTheDocument()
    expect(within(rows[1]).getByText('BAD-1')).toBeInTheDocument()
    expect(within(rows[1]).getByText('quantity: must be a whole number of 0 or more')).toBeInTheDocument()
    expect(within(rows[2]).getByText('—')).toBeInTheDocument()
  })

  it('shows a clean result when every row is imported', async () => {
    server.use(
      http.post('/api/items/import', () =>
        HttpResponse.json({ rows: 2, imported: 2, created: 2, updated: 0, restored: 0, errors: [] }),
      ),
    )
    renderPage()

    await userEvent.upload(screen.getByLabelText('CSV file'), csv())
    await userEvent.click(screen.getByRole('button', { name: 'Import' }))

    expect(await screen.findByText('Imported 2 items: 2 created, 0 updated, 0 restored.')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Rows not imported' })).not.toBeInTheDocument()
  })

  it('shows why a whole file was rejected', async () => {
    server.use(
      http.post('/api/items/import', () =>
        HttpResponse.json({ message: 'Missing columns: reorder_level, unit_price' }, { status: 400 }),
      ),
    )
    renderPage()

    await userEvent.upload(screen.getByLabelText('CSV file'), csv())
    await userEvent.click(screen.getByRole('button', { name: 'Import' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Missing columns: reorder_level, unit_price')
    expect(screen.getByRole('button', { name: 'Import' })).toBeEnabled()
  })
})
