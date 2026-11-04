import {
  Alert,
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
import { CAN_EDIT_ITEMS } from '../auth/roles'
import useAuth from '../auth/useAuth'
import ItemFormDialog from '../components/ItemFormDialog'
import PageTitle from '../components/PageTitle'

function formatPrice(price) {
  return Number(price).toFixed(2)
}

function bySku(a, b) {
  return a.sku.localeCompare(b.sku)
}

function ItemsTable({ items }) {
  return (
    <TableContainer component={Paper}>
      <Table aria-label="Items" size="small">
        <TableHead>
          <TableRow>
            <TableCell>SKU</TableCell>
            <TableCell>Name</TableCell>
            <TableCell>Category</TableCell>
            <TableCell align="right">Quantity</TableCell>
            <TableCell align="right">Reorder level</TableCell>
            <TableCell align="right">Unit price</TableCell>
            <TableCell>Status</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((item) => (
            <TableRow key={item.id}>
              <TableCell>{item.sku}</TableCell>
              <TableCell>{item.name}</TableCell>
              <TableCell>{item.category}</TableCell>
              <TableCell align="right">{item.quantity}</TableCell>
              <TableCell align="right">{item.reorderLevel}</TableCell>
              <TableCell align="right">{formatPrice(item.unitPrice)}</TableCell>
              <TableCell>{item.lowStock && <Chip label="Low stock" color="warning" size="small" />}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function ItemsPage() {
  const { user, request } = useAuth()
  const [state, setState] = useState({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [adding, setAdding] = useState(false)
  const canEdit = CAN_EDIT_ITEMS.includes(user.role)

  const fetchItems = useEffectEvent(() => request('/items'))

  useEffect(() => {
    let ignore = false
    fetchItems().then(
      (items) => !ignore && setState({ status: 'ready', items }),
      (error) => !ignore && setState({ status: 'error', message: error.message }),
    )
    return () => {
      ignore = true
    }
  }, [attempt])

  function retry() {
    setState({ status: 'loading' })
    setAttempt(attempt + 1)
  }

  async function createItem(body) {
    const created = await request('/items', { method: 'POST', body })
    setState({ status: 'ready', items: [...(state.items ?? []), created].sort(bySku) })
    setAdding(false)
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
      {state.status === 'ready' && state.items.length > 0 && <ItemsTable items={state.items} />}
      {adding && <ItemFormDialog onSave={createItem} onClose={() => setAdding(false)} />}
    </>
  )
}

export default ItemsPage
