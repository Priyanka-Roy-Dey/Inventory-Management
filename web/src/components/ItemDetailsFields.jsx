import { Box, Button, IconButton, TextField, Typography } from '@mui/material'

/**
 * Optional attributes (Colour: Blue) and the pincodes the item is delivered to.
 */
function ItemDetailsFields({ attributes, pincodes, errors, onChange }) {
  function setAttribute(index, field, text) {
    onChange({ attributes: attributes.map((each, i) => (i === index ? { ...each, [field]: text } : each)) })
  }

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle2">Attributes</Typography>
      {attributes.map((attribute, index) => (
        <Box key={index} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
          <TextField
            label="Name"
            placeholder="Colour"
            size="small"
            margin="dense"
            value={attribute.name}
            onChange={(event) => setAttribute(index, 'name', event.target.value)}
            slotProps={{ htmlInput: { 'aria-label': `Attribute ${index + 1} name` } }}
          />
          <TextField
            label="Value"
            placeholder="Blue"
            size="small"
            margin="dense"
            value={attribute.value}
            onChange={(event) => setAttribute(index, 'value', event.target.value)}
            slotProps={{ htmlInput: { 'aria-label': `Attribute ${index + 1} value` } }}
            sx={{ flex: 1 }}
          />
          <IconButton
            aria-label={`Remove attribute ${index + 1}`}
            onClick={() => onChange({ attributes: attributes.filter((_, i) => i !== index) })}
          >
            ×
          </IconButton>
        </Box>
      ))}
      {errors.attributes && (
        <Typography variant="body2" color="error">
          {errors.attributes}
        </Typography>
      )}
      <Button size="small" onClick={() => onChange({ attributes: [...attributes, { name: '', value: '' }] })}>
        Add attribute
      </Button>
      <TextField
        label="Delivery pincodes"
        value={pincodes}
        onChange={(event) => onChange({ pincodes: event.target.value })}
        error={Boolean(errors.pincodes)}
        helperText={errors.pincodes ?? 'Optional. 6-digit pincodes separated by commas or spaces; empty means anywhere'}
        fullWidth
        margin="dense"
      />
    </Box>
  )
}

export default ItemDetailsFields
