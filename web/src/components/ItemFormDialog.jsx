import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { useState } from 'react'
import { ApiError } from '../api/client'
import ImagePicker from './ImagePicker'
import useImageChoice from './useImageChoice'

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

function fromItem(item) {
  return {
    sku: item.sku,
    name: item.name,
    category: item.category,
    quantity: String(item.quantity),
    reorderLevel: String(item.reorderLevel),
    unitPrice: Number(item.unitPrice).toFixed(2),
  }
}

// Editing sends only the editable fields and the version it started from (A33).
function toUpdateBody(values, version) {
  return {
    name: values.name.trim(),
    category: values.category.trim(),
    reorderLevel: Number(values.reorderLevel),
    unitPrice: Number(values.unitPrice),
    version,
  }
}

function toCreateBody(values) {
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
  {
    name: 'sku',
    label: 'SKU',
    helperText: 'Letters, digits, ".", "_" and "-"',
    readOnlyText: 'Cannot be changed',
  },
  { name: 'name', label: 'Name' },
  { name: 'category', label: 'Category' },
  {
    name: 'quantity',
    label: 'Quantity',
    inputMode: 'numeric',
    readOnlyText: 'Changed through stock movements',
  },
  { name: 'reorderLevel', label: 'Reorder level', inputMode: 'numeric' },
  { name: 'unitPrice', label: 'Unit price', inputMode: 'decimal' },
]

/**
 * Adds a new item, or edits `item` when given. `onSave` receives the request body and throws an error with a
 * message (and `status`) on failure. When editing, `onReload` returns the latest copy of the item. When adding,
 * `onUploadImage` uploads the chosen image and returns its key (A36).
 */
function ItemFormDialog({ item, onSave, onReload, onUploadImage, onClose }) {
  const editing = Boolean(item)
  const [values, setValues] = useState(editing ? fromItem(item) : EMPTY)
  const [version, setVersion] = useState(item?.version)
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const imageChoice = useImageChoice()

  function handleChange(event) {
    setValues({ ...values, [event.target.name]: event.target.value })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const newErrors = validate(values)
    setErrors(newErrors)
    setServerError(null)
    if (Object.keys(newErrors).length > 0) {
      return
    }

    setSubmitting(true)
    const body = editing ? toUpdateBody(values, version) : toCreateBody(values)
    if (!editing && imageChoice.image) {
      try {
        body.imageKey = await onUploadImage(imageChoice.image.file)
      } catch (error) {
        // The API explains its own refusals; a storage failure only says it failed.
        const message =
          error instanceof ApiError ? error.message : 'Could not upload the image. The item was not created.'
        setServerError({ message, changedElsewhere: false })
        setSubmitting(false)
        return
      }
    }
    try {
      await onSave(body)
    } catch (error) {
      setServerError({ message: error.message, changedElsewhere: editing && error.status === 409 })
      setSubmitting(false)
    }
  }

  // Someone else saved first: start again from their version.
  async function reload() {
    try {
      const latest = await onReload()
      setValues(fromItem(latest))
      setVersion(latest.version)
      setErrors({})
      setServerError(null)
    } catch (error) {
      setServerError({ message: error.message, changedElsewhere: false })
    }
  }

  return (
    <Dialog open onClose={onClose} aria-labelledby="item-form-title" fullWidth maxWidth="sm">
      <Box component="form" noValidate onSubmit={handleSubmit}>
        <DialogTitle id="item-form-title">{editing ? 'Edit item' : 'Add item'}</DialogTitle>
        <DialogContent>
          {serverError && (
            <Alert
              severity="error"
              sx={{ mb: 1 }}
              action={
                serverError.changedElsewhere && (
                  <Button color="inherit" size="small" onClick={reload}>
                    Reload
                  </Button>
                )
              }
            >
              {serverError.message}
            </Alert>
          )}
          {FIELDS.map(({ name, label, helperText, readOnlyText, inputMode }, index) => {
            const readOnly = editing && Boolean(readOnlyText)
            return (
              <TextField
                key={name}
                id={name}
                name={name}
                label={label}
                value={values[name]}
                onChange={handleChange}
                error={Boolean(errors[name])}
                helperText={errors[name] ?? (readOnly ? readOnlyText : helperText)}
                disabled={readOnly}
                required={!readOnly}
                fullWidth
                margin="dense"
                autoFocus={editing ? name === 'name' : index === 0}
                slotProps={inputMode ? { htmlInput: { inputMode } } : undefined}
              />
            )
          })}
          {!editing && <ImagePicker choice={imageChoice} hint="Optional: JPEG, PNG or WebP, up to 2 MB" />}
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
