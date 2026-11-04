import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { useState } from 'react'

// The same rules as the API, so most mistakes are caught before a request is sent.
const SKU_PATTERN = /^[A-Za-z0-9._-]+$/
const WHOLE_NUMBER = /^\d+$/
const PRICE = /^\d{1,8}(\.\d{1,2})?$/

const EMPTY = { sku: '', name: '', category: '', quantity: '', reorderLevel: '', unitPrice: '' }

function validate(values) {
  const errors = {}
  const sku = values.sku.trim()
  if (!sku) {
    errors.sku = 'SKU is required'
  } else if (sku.length > 50) {
    errors.sku = 'Use at most 50 characters'
  } else if (!SKU_PATTERN.test(sku)) {
    errors.sku = "SKU may contain only letters, digits, '.', '_' and '-'"
  }
  if (!values.name.trim()) {
    errors.name = 'Name is required'
  } else if (values.name.trim().length > 200) {
    errors.name = 'Use at most 200 characters'
  }
  if (!values.category.trim()) {
    errors.category = 'Category is required'
  } else if (values.category.trim().length > 100) {
    errors.category = 'Use at most 100 characters'
  }
  for (const field of ['quantity', 'reorderLevel']) {
    if (!WHOLE_NUMBER.test(values[field].trim())) {
      errors[field] = 'Enter a whole number of 0 or more'
    }
  }
  if (!PRICE.test(values.unitPrice.trim())) {
    errors.unitPrice = 'Enter a price of 0 or more with up to 2 decimals'
  }
  return errors
}

function toRequestBody(values) {
  return {
    sku: values.sku.trim(),
    name: values.name.trim(),
    category: values.category.trim(),
    quantity: Number(values.quantity),
    reorderLevel: Number(values.reorderLevel),
    unitPrice: Number(values.unitPrice),
  }
}

const FIELDS = [
  { name: 'sku', label: 'SKU', helperText: 'Letters, digits, ".", "_" and "-"', autoFocus: true },
  { name: 'name', label: 'Name' },
  { name: 'category', label: 'Category' },
  { name: 'quantity', label: 'Quantity', inputMode: 'numeric' },
  { name: 'reorderLevel', label: 'Reorder level', inputMode: 'numeric' },
  { name: 'unitPrice', label: 'Unit price', inputMode: 'decimal' },
]

/**
 * Collects a new item. `onSave` receives the request body and should throw an error with a message on failure.
 */
function ItemFormDialog({ onSave, onClose }) {
  const [values, setValues] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function handleChange(event) {
    setValues({ ...values, [event.target.name]: event.target.value })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const newErrors = validate(values)
    setErrors(newErrors)
    setServerError('')
    if (Object.keys(newErrors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await onSave(toRequestBody(values))
    } catch (error) {
      setServerError(error.message)
      setSubmitting(false)
    }
  }

  return (
    <Dialog open onClose={onClose} aria-labelledby="item-form-title" fullWidth maxWidth="sm">
      <Box component="form" noValidate onSubmit={handleSubmit}>
        <DialogTitle id="item-form-title">Add item</DialogTitle>
        <DialogContent>
          {serverError && (
            <Alert severity="error" sx={{ mb: 1 }}>
              {serverError}
            </Alert>
          )}
          {FIELDS.map(({ name, label, helperText, autoFocus, inputMode }) => (
            <TextField
              key={name}
              id={name}
              name={name}
              label={label}
              value={values[name]}
              onChange={handleChange}
              error={Boolean(errors[name])}
              helperText={errors[name] ?? helperText}
              required
              fullWidth
              margin="dense"
              autoFocus={autoFocus}
              slotProps={inputMode ? { htmlInput: { inputMode } } : undefined}
            />
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            Save
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}

export default ItemFormDialog
