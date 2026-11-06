// The same rules as the API (ItemDetails, A40).
const PINCODE = /^[1-9]\d{5}$/

export function parsePincodes(text) {
  return [...new Set(text.split(/[\s,]+/).filter(Boolean))]
}

/**
 * Checks the optional details. Returns { attributes, pincodes } error messages; missing keys mean no error.
 */
export function validateDetails({ attributes, pincodes }) {
  const errors = {}
  const seen = new Map()
  for (const attribute of attributes) {
    const name = attribute.name.trim()
    const value = attribute.value.trim()
    if (!name || !value) {
      errors.attributes = 'Every attribute needs a name and a value'
      break
    }
    if (name.includes(':')) {
      errors.attributes = "Attribute names cannot contain ':'"
      break
    }
    if (name.length > 50 || value.length > 100) {
      errors.attributes = 'Use at most 50 characters for a name and 100 for a value'
      break
    }
    const first = seen.get(name.toLowerCase())
    if (first) {
      errors.attributes = `${first} is given more than once`
      break
    }
    seen.set(name.toLowerCase(), name)
  }
  const invalid = parsePincodes(pincodes).find((pincode) => !PINCODE.test(pincode))
  if (invalid) {
    errors.pincodes = `${invalid} is not a 6-digit pincode`
  }
  return errors
}

export function toDetailsBody({ attributes, pincodes }) {
  return {
    attributes: attributes.map(({ name, value }) => ({ name: name.trim(), value: value.trim() })),
    deliveryPincodes: parsePincodes(pincodes),
  }
}
