import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useEffect, useEffectEvent, useState } from 'react'
import useAuth from '../auth/useAuth'
import PageTitle from '../components/PageTitle'
import SummaryCard from '../components/SummaryCard'

// A fixed locale keeps the numbers the same on every machine.
const whole = new Intl.NumberFormat('en-US')
const money = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const TYPE_LABELS = { IN: 'In', OUT: 'Out', ADJUST: 'Adjust' }

function formatChange(change) {
  return change > 0 ? `+${change}` : String(change)
}

function Section({ title, children }) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        {title}
      </Typography>
      {children}
    </Paper>
  )
}

function LowStockTable({ items }) {
  if (items.length === 0) {
    return <Typography color="text.secondary">Nothing is low on stock.</Typography>
  }
  return (
    <Table aria-label="Low-stock items" size="small">
      <TableHead>
        <TableRow>
          <TableCell>SKU</TableCell>
          <TableCell>Name</TableCell>
          <TableCell align="right">Quantity / reorder level</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.id}>
            <TableCell>{item.sku}</TableCell>
            <TableCell>{item.name}</TableCell>
            <TableCell align="right">{`${item.quantity} / ${item.reorderLevel}`}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function RecentMovementsTable({ movements }) {
  if (movements.length === 0) {
    return <Typography color="text.secondary">No movements yet.</Typography>
  }
  return (
    <Table aria-label="Recent movements" size="small">
      <TableHead>
        <TableRow>
          <TableCell>When</TableCell>
          <TableCell>SKU</TableCell>
          <TableCell>Type</TableCell>
          <TableCell align="right">Change</TableCell>
          <TableCell>By</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {movements.map((movement) => (
          <TableRow key={movement.id}>
            <TableCell>{new Date(movement.createdAt).toLocaleString()}</TableCell>
            <TableCell>{movement.sku}</TableCell>
            <TableCell>{TYPE_LABELS[movement.type]}</TableCell>
            <TableCell align="right">{formatChange(movement.quantityChange)}</TableCell>
            <TableCell>{movement.createdBy}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function Summary({ summary }) {
  return (
    <>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 2, mb: 3 }}>
        <SummaryCard title="Items" value={whole.format(summary.totalItems)} />
        <SummaryCard title="Low stock" value={whole.format(summary.lowStockCount)} alert={summary.lowStockCount > 0} />
        <SummaryCard title="Units in stock" value={whole.format(summary.totalUnits)} />
        <SummaryCard title="Stock value" value={money.format(summary.stockValue)} />
        <SummaryCard title="Movements (24 h)" value={whole.format(summary.movementsLast24Hours)} />
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
        <Section title="Low stock">
          <LowStockTable items={summary.lowStockItems} />
        </Section>
        <Section title="Recent movements">
          <RecentMovementsTable movements={summary.recentMovements} />
        </Section>
      </Box>
    </>
  )
}

function DashboardPage() {
  const { request } = useAuth()
  const [state, setState] = useState({ status: 'loading', summary: null })
  const [attempt, setAttempt] = useState(0)

  const fetchSummary = useEffectEvent(() => request('/dashboard'))

  useEffect(() => {
    let ignore = false
    fetchSummary().then(
      (summary) => !ignore && setState({ status: 'ready', summary }),
      (error) => !ignore && setState({ status: 'error', message: error.message, summary: null }),
    )
    return () => {
      ignore = true
    }
  }, [attempt])

  function retry() {
    setState({ status: 'loading', summary: null })
    setAttempt(attempt + 1)
  }

  return (
    <>
      <PageTitle>Dashboard</PageTitle>
      {state.status === 'loading' && <CircularProgress aria-label="Loading dashboard" />}
      {state.status === 'error' && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={retry}>
              Retry
            </Button>
          }
        >
          Could not load the dashboard: {state.message}
        </Alert>
      )}
      {state.status === 'ready' && <Summary summary={state.summary} />}
    </>
  )
}

export default DashboardPage
