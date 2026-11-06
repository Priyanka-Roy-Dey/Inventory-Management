import {
  Alert,
  Avatar,
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
  TableSortLabel,
  TextField,
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

const PAGE_SIZE = 20
const SEARCH_DELAY_MS = 300
const INITIAL_FILTERS = {
  search: '',
  category: '',
  lowStock: false,
  minPrice: '',
  maxPrice: '',
  attributeName: '',
  attributeValue: '',
  pincode: '',
  sort: 'sku',
  direction: 'asc',
  page: 0,
}
const PRICE = /^\d+(\.\d{1,2})?$/
const PINCODE = /^[1-9]\d{5}$/

// Typed filters are applied after a pause, and only once they make sense (INV-35).
function draftProblems(drafts) {
  const problems = {}
  for (const field of ['minPrice', 'maxPrice']) {
    if (drafts[field] && !PRICE.test(drafts[field])) {
      problems[field] = 'Enter a price'
    }
  }
  if (!problems.minPrice && !problems.maxPrice && drafts.minPrice && drafts.maxPrice) {
    if (Number(drafts.minPrice) > Number(drafts.maxPrice)) {
      problems.maxPrice = 'Min price is above max price'
    }
  }
  if (drafts.pincode && !PINCODE.test(drafts.pincode)) {
    problems.pincode = 'Enter 6 digits'
  }
  return problems
}

// The API's list parameters (INV-11); empty filters are left out.
function toQuery(filters) {
  const params = new URLSearchParams({
    page: String(filters.page),
    size: String(PAGE_SIZE),
    sort: `${filters.sort},${filters.direction}`,
  })
  if (filters.search) {
    params.set('search', filters.search)
  }
  if (filters.category) {
    params.set('category', filters.category)
  }
  if (filters.lowStock) {
    params.set('lowStock', 'true')
  }
  for (const field of ['minPrice', 'maxPrice', 'pincode']) {
    if (filters[field]) {
      params.set(field, filters[field])
    }
  }
  if (filters.attributeName && filters.attributeValue) {
    params.set('attribute', `${filters.attributeName}:${filters.attributeValue}`)
  }
  return params.toString()
}

function SortableHeader({ field, label, filters, onSort, align }) {
  const active = filters.sort === field
  return (
    <TableCell align={align} sortDirection={active ? filters.direction : false}>
      <TableSortLabel active={active} direction={active ? filters.direction : 'asc'} onClick={() => onSort(field)}>
        {label}
      </TableSortLabel>
    </TableCell>
  )
}

function Filters({ drafts, problems, onDraft, filters, categories, attributeOptions, onChange }) {
  const values = attributeOptions.find((option) => option.name === filters.attributeName)?.values ?? []
  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start', mb: 2 }}>
      <TextField
        type="search"
        label="Search SKU or name"
        size="small"
        value={drafts.search}
        onChange={(event) => onDraft({ search: event.target.value })}
        sx={{ minWidth: 240 }}
      />
      <TextField
        select
        label="Category"
        size="small"
        value={filters.category}
        onChange={(event) => onChange({ category: event.target.value })}
        sx={{ minWidth: 180 }}
      >
        <MenuItem value="">All categories</MenuItem>
        {categories.map((category) => (
          <MenuItem key={category} value={category}>
            {category}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        label="Attribute"
        size="small"
        value={filters.attributeName}
        onChange={(event) => onChange({ attributeName: event.target.value, attributeValue: '' })}
        sx={{ minWidth: 150 }}
      >
        <MenuItem value="">Any attribute</MenuItem>
        {attributeOptions.map((option) => (
          <MenuItem key={option.name} value={option.name}>
            {option.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        label="Value"
        size="small"
        value={filters.attributeValue}
        disabled={!filters.attributeName}
        onChange={(event) => onChange({ attributeValue: event.target.value })}
        sx={{ minWidth: 130 }}
      >
        <MenuItem value="">Any value</MenuItem>
        {values.map((value) => (
          <MenuItem key={value} value={value}>
            {value}
          </MenuItem>
        ))}
      </TextField>
      {[
        ['minPrice', 'Min price'],
        ['maxPrice', 'Max price'],
      ].map(([field, label]) => (
        <TextField
          key={field}
          label={label}
          size="small"
          value={drafts[field]}
          onChange={(event) => onDraft({ [field]: event.target.value.trim() })}
          error={Boolean(problems[field])}
          helperText={problems[field]}
          slotProps={{ htmlInput: { inputMode: 'decimal' } }}
          sx={{ width: 120 }}
        />
      ))}
      <TextField
        label="Delivers to pincode"
        size="small"
        value={drafts.pincode}
        onChange={(event) => onDraft({ pincode: event.target.value.trim() })}
        error={Boolean(problems.pincode)}
        helperText={problems.pincode}
        slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 6 } }}
        sx={{ width: 170 }}
      />
      <FormControlLabel
        control={
          <Switch checked={filters.lowStock} onChange={(event) => onChange({ lowStock: event.target.checked })} />
        }
        label="Low stock only"
      />
    </Box>
  )
}

// The first two pincodes, then "+N"; all of them on hover.
function DeliveryPincodes({ pincodes }) {
  if (!pincodes || pincodes.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        Anywhere
      </Typography>
    )
  }
  const extra = pincodes.length > 2 ? ` +${pincodes.length - 2}` : ''
  return (
    <Typography variant="body2" title={pincodes.join(', ')}>
      {pincodes.slice(0, 2).join(', ') + extra}
    </Typography>
  )
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

function ItemsTable({ items, filters, onSort, onStock, onEdit, onDelete }) {
  const hasActions = Boolean(onStock || onEdit || onDelete)
  const sortable = { filters, onSort }
  return (
    <TableContainer component={Paper}>
      <Table aria-label="Items" size="small">
        <TableHead>
          <TableRow>
            <TableCell>Image</TableCell>
            <SortableHeader field="sku" label="SKU" {...sortable} />
            <SortableHeader field="name" label="Name" {...sortable} />
            <SortableHeader field="category" label="Category" {...sortable} />
            <SortableHeader field="quantity" label="Quantity" align="right" {...sortable} />
            <TableCell align="right">Reorder level</TableCell>
            <SortableHeader field="unitPrice" label="Unit price" align="right" {...sortable} />
            <TableCell>Delivers to</TableCell>
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
              <TableCell>
                {item.name}
                {item.attributes?.length > 0 && (
                  <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.5 }}>
                    {item.attributes.map((attribute) => (
                      <Chip
                        key={attribute.name}
                        label={`${attribute.name}: ${attribute.value}`}
                        size="small"
                        variant="outlined"
                      />
                    ))}
                  </Box>
                )}
              </TableCell>
              <TableCell>{item.category}</TableCell>
              <TableCell align="right">{item.quantity}</TableCell>
              <TableCell align="right">{item.reorderLevel}</TableCell>
              <TableCell align="right">{formatPrice(item.unitPrice)}</TableCell>
              <TableCell>
                <DeliveryPincodes pincodes={item.deliveryPincodes} />
              </TableCell>
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
  const [state, setState] = useState({ status: 'loading', items: [], totalItems: 0 })
  const [attempt, setAttempt] = useState(0)
  const [filters, setFilters] = useState(INITIAL_FILTERS)
  // What the user is typing; applied to `filters` after a pause, once valid.
  const [drafts, setDrafts] = useState({ search: '', minPrice: '', maxPrice: '', pincode: '' })
  const [categories, setCategories] = useState([])
  const [attributeOptions, setAttributeOptions] = useState([])
  const problems = draftProblems(drafts)
  const query = toQuery(filters)
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [stocking, setStocking] = useState(null)
  const canEdit = CAN_EDIT_ITEMS.includes(user.role)
  const canDelete = CAN_DELETE_ITEMS.includes(user.role)

  const fetchItems = useEffectEvent((itemsQuery) => request(`/items?${itemsQuery}`))
  const fetchCategories = useEffectEvent(() => request('/items/categories'))
  const fetchAttributeOptions = useEffectEvent(() => request('/items/attributes'))

  useEffect(() => {
    let ignore = false
    fetchItems(query).then(
      (page) => !ignore && setState({ status: 'ready', items: page.items, totalItems: page.totalItems }),
      (error) => !ignore && setState({ status: 'error', message: error.message, items: [], totalItems: 0 }),
    )
    return () => {
      ignore = true
    }
  }, [query, attempt])

  // Only fill the filter dropdowns, so a failure just leaves them with "All" / "Any".
  useEffect(() => {
    let ignore = false
    fetchCategories().then(
      (list) => !ignore && setCategories(list),
      () => {},
    )
    fetchAttributeOptions().then(
      (list) => !ignore && setAttributeOptions(list),
      () => {},
    )
    return () => {
      ignore = true
    }
  }, [])

  const applyDrafts = useEffectEvent((typed) => {
    const found = draftProblems(typed)
    const changes = {}
    for (const field of ['search', 'minPrice', 'maxPrice', 'pincode']) {
      const value = field === 'search' ? typed.search.trim() : typed[field]
      // A price range that cannot match is not applied, so both price fields wait for each other.
      const blocked = found[field] || (field.endsWith('Price') && (found.minPrice || found.maxPrice))
      if (!blocked && value !== filters[field]) {
        changes[field] = value
      }
    }
    if (Object.keys(changes).length > 0) {
      changeFilters(changes)
    }
  })

  // Waits until the user stops typing, so a word sends one request rather than one per letter.
  useEffect(() => {
    const timer = setTimeout(() => applyDrafts(drafts), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [drafts])

  // Any change except moving between pages starts again on the first page.
  function changeFilters(changes) {
    setFilters({ ...filters, page: 0, ...changes })
    setState({ status: 'loading', items: [], totalItems: 0 })
  }

  function sortBy(field) {
    const direction = filters.sort === field && filters.direction === 'asc' ? 'desc' : 'asc'
    changeFilters({ sort: field, direction })
  }

  function retry() {
    setState({ status: 'loading', items: [], totalItems: 0 })
    setAttempt(attempt + 1)
  }

  const filtering = Boolean(
    filters.search ||
      filters.category ||
      filters.lowStock ||
      filters.minPrice ||
      filters.maxPrice ||
      filters.attributeValue ||
      filters.pincode,
  )

  async function createItem(body) {
    const created = await request('/items', { method: 'POST', body })
    setState({ status: 'ready', items: [...state.items, created].sort(bySku), totalItems: state.totalItems + 1 })
    setAdding(false)
  }

  // These take the item rather than reading editing/deleting, which are null while no dialog is open.
  async function updateItem(item, body) {
    const updated = await request(`/items/${item.id}`, { method: 'PUT', body })
    setState({ ...state, items: state.items.map((each) => (each.id === updated.id ? updated : each)) })
    setEditing(null)
  }

  async function recordMovement(item, body) {
    const result = await request(`/items/${item.id}/movements`, { method: 'POST', body })
    setState({ ...state, items: state.items.map((each) => (each.id === result.item.id ? result.item : each)) })
    setStocking(result.item)
    return result
  }

  // Asks the API where to put the file, then uploads it straight to storage (A36). Returns the key to send with
  // the request that attaches it: a stock-in, or creating the item.
  async function uploadImage(uploadUrlPath, file) {
    const upload = await request(uploadUrlPath, {
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

  function uploadStockImage(item, file) {
    return uploadImage(`/items/${item.id}/image/upload-url`, file)
  }

  function loadHistory(item) {
    return request(`/items/${item.id}/movements`)
  }

  function reloadItem(item) {
    return request(`/items/${item.id}`)
  }

  async function deleteItem(item) {
    await request(`/items/${item.id}`, { method: 'DELETE' })
    setState({
      ...state,
      items: state.items.filter((each) => each.id !== item.id),
      totalItems: state.totalItems - 1,
    })
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
      <Filters
        drafts={drafts}
        problems={problems}
        onDraft={(changes) => setDrafts({ ...drafts, ...changes })}
        filters={filters}
        categories={categories}
        attributeOptions={attributeOptions}
        onChange={changeFilters}
      />
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
      {state.status === 'ready' && state.items.length === 0 && (
        <Typography>{filtering ? 'No items match your search.' : 'No items yet.'}</Typography>
      )}
      {state.status === 'ready' && state.items.length > 0 && (
        <ItemsTable
          items={state.items}
          filters={filters}
          onSort={sortBy}
          onStock={canEdit ? setStocking : undefined}
          onEdit={canEdit ? setEditing : undefined}
          onDelete={canDelete ? setDeleting : undefined}
        />
      )}
      {state.status === 'ready' && state.totalItems > PAGE_SIZE && (
        <TablePagination
          component="div"
          count={state.totalItems}
          page={filters.page}
          rowsPerPage={PAGE_SIZE}
          rowsPerPageOptions={[]}
          onPageChange={(event, page) => {
            setFilters({ ...filters, page })
            setState({ status: 'loading', items: [], totalItems: 0 })
          }}
        />
      )}
      {adding && (
        <ItemFormDialog
          onSave={createItem}
          onUploadImage={(file) => uploadImage('/items/image/upload-url', file)}
          onClose={() => setAdding(false)}
        />
      )}
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
          onUploadImage={(file) => uploadStockImage(stocking, file)}
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
