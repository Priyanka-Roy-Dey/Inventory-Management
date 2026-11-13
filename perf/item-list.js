// k6 load test of the item list (INV-30, A9: p95 < 500 ms with about 10,000 items).
//
//   k6 run -e BASE_URL=https://<id>.cloudfront.net -e TOKEN=<access token> [-e VUS=5] perf/item-list.js
//
// Read-only: it only lists, searches and opens items. The access token lasts 15 minutes; the test takes 4.
import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = __ENV.BASE_URL
const TOKEN = __ENV.TOKEN
// Users browsing at once, each without pauses (-e VUS=5). The default of 20 is far more than the sandbox's real traffic.
const VUS = Number(__ENV.VUS || 20)

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: VUS },
        { duration: '3m', target: VUS },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // A9, per kind of request, so a slow search cannot hide behind fast page loads.
    'http_req_duration{name:list}': ['p(95)<500'],
    'http_req_duration{name:search}': ['p(95)<500'],
    'http_req_duration{name:filter}': ['p(95)<500'],
    'http_req_duration{name:sort}': ['p(95)<500'],
    'http_req_duration{name:deep-page}': ['p(95)<500'],
    'http_req_duration{name:item}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
}

const SEARCHES = ['pen', 'cable', 'PERF-04', 'blue', 'recycled']
const CATEGORIES = ['Perf Stationery', 'Perf Cables', 'Perf Tools']
const SORTS = ['name,desc', 'quantity,asc', 'unitPrice,desc']

export function setup() {
  if (!BASE_URL || !TOKEN) {
    throw new Error('Set BASE_URL and TOKEN, for example -e BASE_URL=https://... -e TOKEN=...')
  }
}

function pick(values) {
  return values[Math.floor(Math.random() * values.length)]
}

function get(path, name) {
  const response = http.get(`${BASE_URL}/api/items${path}`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
    tags: { name },
  })
  check(response, { [`${name} is 200`]: (r) => r.status === 200 })
  return response
}

export default function () {
  const first = get('?page=0&size=20', 'list')
  get(`?search=${encodeURIComponent(pick(SEARCHES))}&size=20`, 'search')
  get(`?category=${encodeURIComponent(pick(CATEGORIES))}&size=20`, 'filter')
  get(`?sort=${pick(SORTS)}&size=20`, 'sort')
  // Pages 300-399 of 20 items are items 6,000-8,000: an offset deep into the 10,000.
  get(`?page=${300 + Math.floor(Math.random() * 100)}&size=20`, 'deep-page')

  const items = first.status === 200 ? first.json('items') : []
  if (items && items.length > 0) {
    get(`/${pick(items).id}`, 'item')
  }
}
