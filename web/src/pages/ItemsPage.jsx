import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useEffect, useEffectEvent, useState } from 'react'
import { CAN_DELETE_ITEMS, CAN_EDIT_ITEMS } from '../auth/roles'
import useAuth from '../auth/useAuth'
import DeleteItemDialog from '../components/DeleteItemDialog'
import ItemFormDialog from '../components/ItemFormDialog'
import StockDialog from '../components/StockDialog'
import PageTitle from '../components/PageTitle'

function formatPrice(price) {
  return Number(price).toFixed(2)
}

function bySku(a, b) {
  return a.sku.localeCompare(b.sku)
}

// Images are added with stock (A36); here they are only shown.
function ItemThumbnail({ item }) {
  return item.imageUrl ? (
    <Avatar variant="rounded" src={item.imageUrl} alt={item.sku} sx={{ width: 40, height: 40 }} />
  ) : (
    <Avatar variant="rounded" sx={{ width: 40, height: 40, bgcolor: 'grey.200', color: 'text.secondary' }}>
      {item.sku.charAt(0)}
    </Avatar>
  )
}

function ItemsTable({ items, onStock, onEdit, onDelete }) {
  const hasActions = Boolean(onStock || onEdit || onDelete)
  return (
    <TableContainer component={Paper}>
      <Table aria-label="Items" size="small">
        <TableHead>
          <TableRow>
            <TableCell>Image</TableCell>
            <TableCell>SKU</TableCell>
            <TableCell>Name</TableCell>
            <TableCell>Category</TableCell>
            <TableCell align="right">Quantity</TableCell>
            <TableCell align="right">Reorder level</TableCell>
            <TableCell align="right">Unit price</TableCell>
            <TableCell>Status</TableCell>
            {hasActions && <TableCell align="right">Actions</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((item) => (
            <TableRow key={item.id}>
              <TableCell>
                <ItemThumbnail item={item} />
              </TableCell>
              <TableCell>{item.sku}</TableCell>
              <TableCell>{item.name}</TableCell>
              <TableCell>{item.category}</TableCell>
              <TableCell align="right">{item.quantity}</TableCell>
              <TableCell align="right">{item.reorderLevel}</TableCell>
              <TableCell align="right">{formatPrice(item.unitPrice)}</TableCell>
              <TableCell>{item.lowStock && <Chip label="Low stock" color="warning" size="small" />}</TableCell>
              {hasActions && (
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {onStock && (
                    <Button size="small" aria-label={`Stock ${item.sku}`} onClick={() => onStock(item)}>
                      Stock
                    </Button>
                  )}
                  {onEdit && (
                    <Button size="small" aria-label={`Edit ${item.sku}`} onClick={() => onEdit(item)}>
                      Edit
                    </Button>
                  )}
                  {onDelete && (
                    <Button
                      size="small"
                      color="error"
                      aria-label={`Delete ${item.sku}`}
                      onClick={() => onDelete(item)}
                    >
                      Delete
                    </Button>
                  )}
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function ItemsPage() {
  const { user, request } = useAuth()
  // `items` is always an array, so every read is safe whatever the status.
  const [state, setState] = useState({ status: 'loading', items: [] })
  const [attempt, setAttempt] = useState(0)
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [stocking, setStocking] = useState(null)
  const canEdit = CAN_EDIT_ITEMS.includes(user.role)
  const canDelete = CAN_DELETE_ITEMS.includes(user.role)

  const fetchItems = useEffectEvent(() => request('/items'))

  useEffect(() => {
    let ignore = false
    fetchItems().then(
      (items) => !ignore && setState({ status: 'ready', items }),
      (error) => !ignore && setState({ status: 'error', message: error.message, items: [] }),
    )
    return () => {
      ignore = true
    }
  }, [attempt])

  function retry() {
    setState({ status: 'loading', items: [] })
    setAttempt(attempt + 1)
  }

  async function createItem(body) {
    const created = await request('/items', { method: 'POST', body })
    setState({ status: 'ready', items: [...state.items, created].sort(bySku) })
    setAdding(false)
  }

  // These take the item rather than reading editing/deleting, which are null while no dialog is open.
  async function updateItem(item, body) {
    const updated = await request(`/items/${item.id}`, { method: 'PUT', body })
    setState({ status: 'ready', items: state.items.map((each) => (each.id === updated.id ? updated : each)) })
    setEditing(null)
  }

  async function recordMovement(item, body) {
    const result = await request(`/items/${item.id}/movements`, { method: 'POST', body })
    setState({
      status: 'ready',
      items: state.items.map((each) => (each.id === result.item.id ? result.item : each)),
    })
    setStocking(result.item)
    return result
  }

  // Asks the API where to put the file, then uploads it straight to storage (A36). Returns the key to send with
  // the stock-in.
  async function uploadImage(item, file) {
    const upload = await request(`/items/${item.id}/image/upload-url`, {
      method: 'POST',
      body: { contentType: file.type, size: file.size },
    })
    const response = await fetch(upload.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': upload.contentType },
      body: file,
    })
    if (!response.ok) {
      throw new Error(`Storage returned ${response.status}`)
    }
    return upload.key
  }

  function loadHistory(item) {
    return request(`/items/${item.id}/movements`)
  }

  function reloadItem(item) {
    return request(`/items/${item.id}`)
  }

  async function deleteItem(item) {
    await request(`/items/${item.id}`, { method: 'DELETE' })
    setState({ status: 'ready', items: state.items.filter((each) => each.id !== item.id) })
    setDeleting(null)
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <PageTitle>Items</PageTitle>
        {canEdit && (
          <Button variant="contained" onClick={() => setAdding(true)}>
            Add item
          </Button>
        )}
      </Box>
      {state.status === 'loading' && <CircularProgress aria-label="Loading items" />}
      {state.status === 'error' && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={retry}>
              Retry
            </Button>
          }
        >
          Could not load items: {state.message}
        </Alert>
      )}
      {state.status === 'ready' && state.items.length === 0 && <Typography>No items yet.</Typography>}
      {state.status === 'ready' && state.items.length > 0 && (
        <ItemsTable
          items={state.items}
          onStock={canEdit ? setStocking : undefined}
          onEdit={canEdit ? setEditing : undefined}
          onDelete={canDelete ? setDeleting : undefined}
        />
      )}
      {adding && <ItemFormDialog onSave={createItem} onClose={() => setAdding(false)} />}
      {editing && (
        <ItemFormDialog
          item={editing}
          onSave={(body) => updateItem(editing, body)}
          onReload={() => reloadItem(editing)}
          onClose={() => setEditing(null)}
        />
      )}
      {stocking && (
        <StockDialog
          item={stocking}
          onRecord={(body) => recordMovement(stocking, body)}
          onUploadImage={(file) => uploadImage(stocking, file)}
          loadHistory={() => loadHistory(stocking)}
          onClose={() => setStocking(null)}
        />
      )}
      {deleting && (
        <DeleteItemDialog item={deleting} onConfirm={() => deleteItem(deleting)} onClose={() => setDeleting(null)} />
      )}
    </>
  )
}

export default ItemsPage
