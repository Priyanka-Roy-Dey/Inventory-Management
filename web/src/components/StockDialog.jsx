import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  FormLabel,
  Paper,
  Radio,
  RadioGroup,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useEffectEvent, useState } from 'react'
import { ApiError } from '../api/client'

const TYPES = [
  { value: 'IN', label: 'In', helperText: 'Units received' },
  { value: 'OUT', label: 'Out', helperText: 'Units issued' },
  { value: 'ADJUST', label: 'Adjust', helperText: 'Signed correction, for example 5 or -3' },
]
const TYPE_LABELS = Object.fromEntries(TYPES.map(({ value, label }) => [value, label]))

// The same limits as the API (A34, A36).
const MAX_QUANTITY = 1_000_000
const MAX_REASON = 500
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_IMAGE_BYTES = 2 * 1024 * 1024

function imageProblem(file) {
  if (!IMAGE_TYPES.includes(file.type)) {
    return 'Choose a JPEG, PNG or WebP image'
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return 'The image must be 2 MB or smaller'
  }
  return null
}

function validate({ type, quantity, reason }) {
  const errors = {}
  const value = quantity.trim()
  const number = Number(value)
  if (type === 'ADJUST') {
    if (!/^-?\d+$/.test(value) || number === 0) {
      errors.quantity = 'Enter a change such as 5 or -3, not 0'
    }
    if (!reason.trim()) {
      errors.reason = 'A reason is required for an adjustment'
    }
  } else if (!/^\d+$/.test(value) || number < 1) {
    errors.quantity = 'Enter a whole number of 1 or more'
  }
  if (!errors.quantity && Math.abs(number) > MAX_QUANTITY) {
    errors.quantity = 'Use at most 1,000,000'
  }
  if (reason.trim().length > MAX_REASON) {
    errors.reason = `Use at most ${MAX_REASON} characters`
  }
  return errors
}

function formatChange(change) {
  return change > 0 ? `+${change}` : String(change)
}

function totalOf(movements, type) {
  return movements.filter((movement) => movement.type === type).reduce((sum, m) => sum + m.quantityChange, 0)
}

function SummaryCard({ title, value, status }) {
  return (
    <Paper variant="outlined" role="group" aria-label={title} sx={{ p: 1.5 }}>
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

// Totals come from the full history, so they are only shown once it has loaded.
function SummaryCards({ item, history }) {
  const ready = history.status === 'ready'
  const total = (type) => (ready ? formatChange(totalOf(history.movements, type)) : '–')
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 1.5, mb: 2 }}>
      <SummaryCard
        title="In stock"
        value={item.quantity}
        status={
          item.lowStock ? (
            <Chip label="Low stock" color="warning" size="small" />
          ) : (
            <Chip label="OK" color="success" size="small" variant="outlined" />
          )
        }
      />
      <SummaryCard title="Reorder level" value={item.reorderLevel} />
      <SummaryCard title="Received" value={total('IN')} />
      <SummaryCard title="Issued" value={total('OUT')} />
      <SummaryCard title="Adjusted" value={total('ADJUST')} />
    </Box>
  )
}

function History({ history }) {
  if (history.status === 'loading') {
    return <CircularProgress size={24} aria-label="Loading history" />
  }
  if (history.status === 'error') {
    return <Alert severity="warning">Could not load the history: {history.message}</Alert>
  }
  if (history.movements.length === 0) {
    return <Typography color="text.secondary">No movements yet.</Typography>
  }
  return (
    <Table aria-label="Stock history" size="small">
      <TableHead>
        <TableRow>
          <TableCell>When</TableCell>
          <TableCell>Type</TableCell>
          <TableCell align="right">Change</TableCell>
          <TableCell>Quantity</TableCell>
          <TableCell>Reason</TableCell>
          <TableCell>By</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {history.movements.map((movement) => (
          <TableRow key={movement.id}>
            <TableCell>{new Date(movement.createdAt).toLocaleString()}</TableCell>
            <TableCell>{TYPE_LABELS[movement.type]}</TableCell>
            <TableCell align="right">{formatChange(movement.quantityChange)}</TableCell>
            <TableCell>{`${movement.quantityBefore} → ${movement.quantityAfter}`}</TableCell>
            <TableCell>{movement.reason}</TableCell>
            <TableCell>{movement.createdBy}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// Lets the user add an image when adding stock. Only checks the file; uploading happens on Record.
function ImagePicker({ image, error, onChoose, onRemove }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', mt: 1 }}>
      <Button component="label" variant="outlined" size="small">
        {image ? 'Choose another image' : 'Choose image'}
        <input
          hidden
          type="file"
          accept={IMAGE_TYPES.join(',')}
          aria-label="Image (optional)"
          onChange={onChoose}
        />
      </Button>
      {image && (
        <>
          <Box
            component="img"
            src={image.previewUrl}
            alt={`Preview of ${image.file.name}`}
            sx={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 1 }}
          />
          <Button size="small" aria-label="Remove chosen image" onClick={onRemove}>
            Remove
          </Button>
        </>
      )}
      <Typography variant="body2" color={error ? 'error' : 'text.secondary'}>
        {error ?? 'Optional image for the item: JPEG, PNG or WebP, up to 2 MB'}
      </Typography>
    </Box>
  )
}

/**
 * Records stock movements for `item` and shows its history. `onRecord` sends a movement and returns
 * `{ movement, item }`; `loadHistory` returns the movements, newest first; `onUploadImage` uploads a file and
 * returns its key. All three throw an error with a message.
 */
function StockDialog({ item, onRecord, loadHistory, onUploadImage, onClose }) {
  const [values, setValues] = useState({ type: 'IN', quantity: '', reason: '' })
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [history, setHistory] = useState({ status: 'loading', movements: [] })
  const [image, setImage] = useState(null)
  const [imageError, setImageError] = useState(null)
  const [uploading, setUploading] = useState(false)

  const fetchHistory = useEffectEvent(() => loadHistory())

  useEffect(() => {
    let ignore = false
    fetchHistory().then(
      (movements) => !ignore && setHistory({ status: 'ready', movements }),
      (error) => !ignore && setHistory({ status: 'error', message: error.message, movements: [] }),
    )
    return () => {
      ignore = true
    }
  }, [])

  // Frees the preview's memory when it is replaced or the dialog closes.
  useEffect(() => {
    return () => {
      if (image) {
        URL.revokeObjectURL(image.previewUrl)
      }
    }
  }, [image])

  function handleChange(event) {
    setValues({ ...values, [event.target.name]: event.target.value })
    // Only stock coming in can bring an image.
    if (event.target.name === 'type' && event.target.value !== 'IN') {
      clearImage()
    }
  }

  function handleImage(event) {
    const file = event.target.files[0]
    // Clear the input so choosing the same file again still triggers a change.
    event.target.value = ''
    if (!file) {
      return
    }
    const problem = imageProblem(file)
    setImageError(problem)
    setImage(problem ? null : { file, previewUrl: URL.createObjectURL(file) })
  }

  function clearImage() {
    setImage(null)
    setImageError(null)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const newErrors = validate(values)
    setErrors(newErrors)
    setServerError('')
    setSuccess('')
    if (Object.keys(newErrors).length > 0) {
      return
    }

    setSubmitting(true)
    const body = { type: values.type, quantity: Number(values.quantity.trim()), reason: values.reason.trim() || null }
    if (values.type === 'IN' && image) {
      setUploading(true)
      try {
        body.imageKey = await onUploadImage(image.file)
      } catch (error) {
        // The API explains its own refusals; a storage failure only says it failed.
        setServerError(
          error instanceof ApiError ? error.message : 'Could not upload the image. The stock was not changed.',
        )
        setUploading(false)
        setSubmitting(false)
        return
      }
      setUploading(false)
    }
    try {
      const { movement } = await onRecord(body)
      setSuccess(`Stock updated: ${movement.quantityBefore} → ${movement.quantityAfter}`)
      setHistory({ status: 'ready', movements: [movement, ...history.movements] })
      setValues({ ...values, quantity: '', reason: '' })
      clearImage()
    } catch (error) {
      setServerError(error.message)
    }
    setSubmitting(false)
  }

  const typeHelp = TYPES.find(({ value }) => value === values.type).helperText

  return (
    <Dialog open onClose={onClose} aria-labelledby="stock-dialog-title" fullWidth maxWidth="md">
      <DialogTitle id="stock-dialog-title">Stock: {item.sku}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
          {item.imageUrl && (
            <Box
              component="img"
              src={item.imageUrl}
              alt={item.sku}
              sx={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 1 }}
            />
          )}
          <Typography color="text.secondary">
            {item.name} · {item.category}
          </Typography>
        </Box>
        <SummaryCards item={item} history={history} />
        {serverError && (
          <Alert severity="error" sx={{ mb: 1 }}>
            {serverError}
          </Alert>
        )}
        {success && (
          <Alert severity="success" sx={{ mb: 1 }}>
            {success}
          </Alert>
        )}
        <Box component="form" noValidate onSubmit={handleSubmit} sx={{ mb: 3 }}>
          <FormControl>
            <FormLabel id="movement-type-label">Type</FormLabel>
            <RadioGroup row aria-labelledby="movement-type-label" name="type" value={values.type} onChange={handleChange}>
              {TYPES.map(({ value, label }) => (
                <FormControlLabel key={value} value={value} control={<Radio />} label={label} />
              ))}
            </RadioGroup>
          </FormControl>
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start' }}>
            <TextField
              id="movement-quantity"
              name="quantity"
              label="Quantity"
              value={values.quantity}
              onChange={handleChange}
              error={Boolean(errors.quantity)}
              helperText={errors.quantity ?? typeHelp}
              required
              margin="dense"
              autoFocus
              slotProps={{ htmlInput: { inputMode: values.type === 'ADJUST' ? 'text' : 'numeric' } }}
            />
            <TextField
              id="movement-reason"
              name="reason"
              label="Reason"
              value={values.reason}
              onChange={handleChange}
              error={Boolean(errors.reason)}
              helperText={errors.reason ?? (values.type === 'ADJUST' ? 'Required' : 'Optional')}
              required={values.type === 'ADJUST'}
              margin="dense"
              sx={{ flex: 1, minWidth: 220 }}
            />
            <Button type="submit" variant="contained" disabled={submitting} sx={{ mt: 2 }}>
              Record
            </Button>
          </Box>
          {values.type === 'IN' && (
            <ImagePicker image={image} error={imageError} onChoose={handleImage} onRemove={clearImage} />
          )}
          {uploading && (
            <Typography variant="body2" sx={{ mt: 1 }}>
              Uploading image…
            </Typography>
          )}
        </Box>
        <Divider sx={{ mb: 2 }} />
        <Typography variant="h6" component="h2" gutterBottom>
          History
        </Typography>
        <History history={history} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}

export default StockDialog
