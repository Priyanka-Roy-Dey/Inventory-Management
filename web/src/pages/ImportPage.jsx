import {
  Alert,
  Box,
  Button,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import useAuth from '../auth/useAuth'
import PageTitle from '../components/PageTitle'

// The same limits as the API (A6, A8).
const MAX_BYTES = 10 * 1024 * 1024

const COLUMNS = [
  ['sku', 'Letters, digits, ".", "_" and "-". An existing SKU updates that item; a deleted one is restored.'],
  ['name', 'Up to 200 characters.'],
  ['category', 'Up to 100 characters.'],
  ['quantity', 'Whole number, 0 or more. A change is recorded as a stock adjustment.'],
  ['reorder_level', 'Whole number, 0 or more.'],
  ['unit_price', 'Number, 0 or more, at most 2 decimals (for example 4.50).'],
]

function fileProblem(file) {
  if (!file.name.toLowerCase().endsWith('.csv')) {
    return 'Choose a .csv file'
  }
  if (file.size > MAX_BYTES) {
    return 'The file must be 10 MB or smaller'
  }
  return null
}

function ColumnsHelp() {
  return (
    <TableContainer component={Paper} variant="outlined" sx={{ mb: 2 }}>
      <Table aria-label="Required columns" size="small">
        <TableHead>
          <TableRow>
            <TableCell>Column</TableCell>
            <TableCell>What to put in it</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {COLUMNS.map(([column, help]) => (
            <TableRow key={column}>
              <TableCell>
                <code>{column}</code>
              </TableCell>
              <TableCell>{help}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function Report({ report }) {
  return (
    <Box sx={{ mt: 3 }}>
      <Alert severity="success" sx={{ mb: 2 }}>
        {`Imported ${report.imported} items: ${report.created} created, ${report.updated} updated, ${report.restored} restored.`}
      </Alert>
      {report.errors.length > 0 && (
        <>
          <Alert severity="warning" sx={{ mb: 2 }}>
            {`${report.errors.length} of ${report.rows} rows were not imported. Fix them and import the file again.`}
          </Alert>
          <TableContainer component={Paper}>
            <Table aria-label="Rows not imported" size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Row</TableCell>
                  <TableCell>SKU</TableCell>
                  <TableCell>Problem</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.errors.map((error) => (
                  <TableRow key={error.row}>
                    <TableCell>{error.row}</TableCell>
                    <TableCell>{error.sku ?? '—'}</TableCell>
                    <TableCell>{error.message}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Box>
  )
}

/**
 * Staff and Admin (see App). Creates, updates or restores items by SKU from a CSV file (A5–A8, A42).
 */
function ImportPage() {
  const { request } = useAuth()
  const [file, setFile] = useState(null)
  const [problem, setProblem] = useState(null)
  const [importing, setImporting] = useState(false)
  const [report, setReport] = useState(null)
  const [error, setError] = useState('')

  function choose(event) {
    const chosen = event.target.files[0]
    // Clear the input so choosing the same file again still triggers a change.
    event.target.value = ''
    if (!chosen) {
      return
    }
    const found = fileProblem(chosen)
    setProblem(found)
    setFile(found ? null : chosen)
    setReport(null)
    setError('')
  }

  async function upload() {
    const form = new FormData()
    form.append('file', file)
    setImporting(true)
    setError('')
    setReport(null)
    try {
      setReport(await request('/items/import', { method: 'POST', body: form }))
    } catch (e) {
      setError(e.message)
    }
    setImporting(false)
  }

  return (
    <>
      <PageTitle>Import items</PageTitle>
      <Typography sx={{ mb: 2 }}>
        Upload a CSV file (up to 10 MB) with a header row and these columns, in any order. Rows with a problem are
        listed below and skipped; the others are imported.{' '}
        <Link href="/sample-import.csv" download>
          Download sample CSV
        </Link>
      </Typography>
      <ColumnsHelp />
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Button component="label" variant="outlined">
          Choose file
          <input hidden type="file" accept=".csv,text/csv" aria-label="CSV file" onChange={choose} />
        </Button>
        <Typography color={problem ? 'error' : 'text.secondary'}>{problem ?? file?.name ?? 'No file chosen'}</Typography>
        <Button variant="contained" onClick={upload} disabled={!file || importing}>
          Import
        </Button>
        {importing && <Typography>Importing…</Typography>}
      </Box>
      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}
      {report && <Report report={report} />}
    </>
  )
}

export default ImportPage
