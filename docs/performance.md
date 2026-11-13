# Performance and accessibility (INV-30)

Measured against assumption A9 in the [design note](design-note.md#3-assumption-log): Lighthouse accessibility ≥ 90 and performance ≥ 80, and an item list p95 under 500 ms with about 10,000 items.

## Results

_Measured on 13 November 2026 against the sandbox (`eu-north-1`): 1 API task (0.5 vCPU, 1 GB), autoscaling 1–3; RDS `db.t4g.micro`._

### Item list with 10,000 items (k6)

| Request | p95 | Target | Result |
|---|---|---|---|
| First page (`list`) | _to fill_ | < 500 ms | |
| Search by SKU or name (`search`) | _to fill_ | < 500 ms | |
| Filter by category (`filter`) | _to fill_ | < 500 ms | |
| Sort by name, quantity or price (`sort`) | _to fill_ | < 500 ms | |
| Page 300–399, items 6,000–8,000 (`deep-page`) | _to fill_ | < 500 ms | |
| One item (`item`) | _to fill_ | < 500 ms | |
| Failed requests | _to fill_ | < 1% | |

### Lighthouse (Chrome DevTools, desktop)

| Page | Performance (≥ 80) | Accessibility (≥ 90) | Best practices | Notes |
|---|---|---|---|---|
| Sign-in (`/login`) | _to fill_ | _to fill_ | _to fill_ | |
| Items (`/items`, signed in) | _to fill_ | _to fill_ | _to fill_ | |

## How it was measured

### 1. Seed 10,000 items

`perf/generate_seed.py` writes the same 10,000 items every time: SKUs `PERF-00001` to `PERF-10000`, 8 categories starting `Perf `, random names, prices and quantities. Every item starts above its reorder level, because each item created low raises a low-stock alert and an email (A14).

```bash
python3 perf/generate_seed.py > seed.csv
```

The seed was imported through the **Import** page in 5 files of 2,000 rows. CloudFront waits at most 30 seconds for an answer, and one synchronous import of 10,000 rows (A42) could take longer.

### 2. Load test (k6)

`perf/item-list.js` is read-only. Each of 20 virtual users, with no pauses, loads the first page, searches, filters by category, sorts, opens a page deep in the list, then opens one item. It ramps up for 30 seconds, holds for 3 minutes and ramps down for 30 seconds. Each kind of request has its own p95 threshold, so a slow search cannot hide behind fast page loads.

It runs from CloudShell in `eu-north-1`, through CloudFront, so the time includes CloudFront, the load balancer, the API and the database. Network distance to the user is mostly left out.

```bash
k6 run -e BASE_URL=https://<id>.cloudfront.net -e TOKEN=<access token> perf/item-list.js
```

### 3. Lighthouse

Chrome DevTools → **Lighthouse**, mode **Navigation**, device **Desktop**, categories **Performance**, **Accessibility** and **Best practices**. For the Items page, sign in first and untick **Clear storage**, so the session in `sessionStorage` survives the reload.

## Cleaning up the seed

The seeded items stay in the database until they are deleted. To hide them from the demo, filter by a non-`Perf` category, or delete them as Admin. Deleting is a soft delete (A7), so their SKUs stay reserved, and importing the seed again restores them.
