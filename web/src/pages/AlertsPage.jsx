import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControlLabel,
  MenuItem,
  Paper,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useEffectEvent, useState } from 'react'
import useAuth from '../auth/useAuth'
import PageTitle from '../components/PageTitle'

const PAGE_SIZE = 20
const SEARCH_DELAY_MS = 300

// Why the item became low (INV-10, A37).
const CAUSES = {
  STOCK_MOVEMENT: 'Stock movement',
  CREATED: 'Created already low',
  REORDER_LEVEL_RAISED: 'Reorder level raised',
}

const INITIAL_FILTERS = { search: '', cause: '', stillLow: false, page: 0 }

function toQuery(filters) {
  const params = new URLSearchParams({ page: String(filters.page), size: String(PAGE_SIZE) })
  if (filters.search) {
    params.set('search', filters.search)
  }
  if (filters.cause) {
    params.set('cause', filters.cause)
  }
  if (filters.stillLow) {
    params.set('stillLow', 'true')
  }
  return params.toString()
}

function AlertsTable({ alerts }) {
  return (
    <TableContainer component={Paper}>
      <Table aria-label="Low-stock alerts" size="small">
        <TableHead>
          <TableRow>
            <TableCell>When</TableCell>
            <TableCell>SKU</TableCell>
            <TableCell>Item</TableCell>
            <TableCell align="right">Quantity / reorder level</TableCell>
            <TableCell>Cause</TableCell>
            <TableCell>Now</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {alerts.map((alert) => (
            <TableRow key={alert.id}>
              <TableCell>{new Date(alert.occurredAt).toLocaleString()}</TableCell>
              <TableCell>{alert.sku}</TableCell>
              <TableCell>{alert.itemName}</TableCell>
              <TableCell align="right">{`${alert.quantity} / ${alert.reorderLevel}`}</TableCell>
              <TableCell>{CAUSES[alert.cause] ?? alert.cause}</TableCell>
              <TableCell>
                {alert.stillLow ? (
                  <Chip label="Still low" color="warning" size="small" />
                ) : (
                  <Chip label={`Restocked (${alert.currentQuantity})`} color="success" size="small" variant="outlined" />
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

/**
 * Low-stock alerts, newest first. Read from the API for now (A41); INV-19 moves them to DynamoDB.
 */
function AlertsPage() {
  const { request } = useAuth()
  // `alerts` is always an array, so every read is safe whatever the status.
  const [state, setState] = useState({ status: 'loading', alerts: [], totalItems: 0 })
  const [attempt, setAttempt] = useState(0)
  const [filters, setFilters] = useState(INITIAL_FILTERS)
  const [searchText, setSearchText] = useState('')
  const query = toQuery(filters)

  const fetchAlerts = useEffectEvent((alertsQuery) => request(`/alerts?${alertsQuery}`))

  useEffect(() => {
    let ignore = false
    fetchAlerts(query).then(
      (page) => !ignore && setState({ status: 'ready', alerts: page.alerts, totalItems: page.totalItems }),
      (error) => !ignore && setState({ status: 'error', message: error.message, alerts: [], totalItems: 0 }),
    )
    return () => {
      ignore = true
    }
  }, [query, attempt])

  // Any change except moving between pages starts again on the first page.
  function changeFilters(changes) {
    setFilters({ ...filters, page: 0, ...changes })
    setState({ status: 'loading', alerts: [], totalItems: 0 })
  }

  const applySearch = useEffectEvent((search) => {
    if (search !== filters.search) {
      changeFilters({ search })
    }
  })

  useEffect(() => {
    const timer = setTimeout(() => applySearch(searchText.trim()), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [searchText])

  function retry() {
    setState({ status: 'loading', alerts: [], totalItems: 0 })
    setAttempt(attempt + 1)
  }

  const filtering = Boolean(filters.search || filters.cause || filters.stillLow)

  return (
    <>
      <PageTitle>Alerts</PageTitle>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        An alert is recorded when an item drops to or below its reorder level.
      </Typography>
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center', mb: 2 }}>
        <TextField
          type="search"
          label="Search SKU or name"
          size="small"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          sx={{ minWidth: 240 }}
        />
        <TextField
          select
          label="Cause"
          size="small"
          value={filters.cause}
          onChange={(event) => changeFilters({ cause: event.target.value })}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All causes</MenuItem>
          {Object.entries(CAUSES).map(([value, label]) => (
            <MenuItem key={value} value={value}>
              {label}
            </MenuItem>
          ))}
        </TextField>
        <FormControlLabel
          control={
            <Switch checked={filters.stillLow} onChange={(event) => changeFilters({ stillLow: event.target.checked })} />
          }
          label="Still low only"
        />
      </Box>
      {state.status === 'loading' && <CircularProgress aria-label="Loading alerts" />}
      {state.status === 'error' && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={retry}>
              Retry
            </Button>
          }
        >
          Could not load alerts: {state.message}
        </Alert>
      )}
      {state.status === 'ready' && state.alerts.length === 0 && (
        <Typography>
          {filtering ? 'No alerts match your filters.' : 'No low-stock alerts yet. Everything is above its reorder level.'}
        </Typography>
      )}
      {state.status === 'ready' && state.alerts.length > 0 && <AlertsTable alerts={state.alerts} />}
      {state.status === 'ready' && state.totalItems > PAGE_SIZE && (
        <TablePagination
          component="div"
          count={state.totalItems}
          page={filters.page}
          rowsPerPage={PAGE_SIZE}
          rowsPerPageOptions={[]}
          onPageChange={(event, page) => {
            setFilters({ ...filters, page })
            setState({ status: 'loading', alerts: [], totalItems: 0 })
          }}
        />
      )}
    </>
  )
}

export default AlertsPage
