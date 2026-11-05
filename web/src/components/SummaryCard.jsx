import { Paper, Typography } from '@mui/material'

/**
 * A small labelled figure. `alert` draws attention to it (for example, items that are low on stock).
 */
function SummaryCard({ title, value, status, alert = false }) {
  return (
    <Paper
      variant="outlined"
      role="group"
      aria-label={title}
      data-alert={String(alert)}
      sx={{ p: 1.5, borderColor: alert ? 'warning.main' : undefined, borderWidth: alert ? 2 : undefined }}
    >
      <Typography variant="body2" color="text.secondary">
        {title}
      </Typography>
      <Typography variant="h5" component="p">
        {value}
      </Typography>
      {status}
    </Paper>
  )
}

export default SummaryCard
