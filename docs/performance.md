# Performance and accessibility (INV-30)

Measured against assumption A9 in the [design note](design-note.md#3-assumption-log): Lighthouse accessibility ≥ 90 and performance ≥ 80, and an item list p95 under 500 ms with about 10,000 items.

## Results

_Measured on 13 November 2026 against the sandbox (`eu-north-1`), with about 10,000 items: API tasks of 0.5 vCPU and 1 GB, autoscaling 1–3; RDS `db.t4g.micro`._

**Summary:** with 10,000 items, every request type stays well under 500 ms at p95 once the API has enough tasks. One task alone could not serve 20 users who never pause: its CPU reached 100% and five request types missed the target. The data and the queries were not the bottleneck: the database CPU stayed at 13%, and fetching one item by id was as slow as searching.

### Item list with 10,000 items (k6, 20 users without pauses)

Four runs of 4 minutes each, one after the other, while autoscaling added tasks after the first run.

| Request | Run 1: 1 task | Run 2: 3 tasks, just started | Run 3: 2–3 tasks | Run 4: 3 tasks, warmed up | Target |
|---|---|---|---|---|---|
| First page (`list`) | 704 ms ✗ | 341 ms | 233 ms | 68 ms | < 500 ms |
| Search by SKU or name (`search`) | 788 ms ✗ | 386 ms | 271 ms | 72 ms | < 500 ms |
| Filter by category (`filter`) | 787 ms ✗ | 385 ms | 272 ms | 69 ms | < 500 ms |
| Sort by name, quantity or price (`sort`) | 710 ms ✗ | 360 ms | 268 ms | 74 ms | < 500 ms |
| Page 300–399, items 6,000–8,000 (`deep-page`) | 596 ms ✗ | 284 ms | 193 ms | 66 ms | < 500 ms |
| One item (`item`) | 499 ms | 264 ms | 192 ms | 62 ms | < 500 ms |
| Requests (failed) | 16,050 (0) | 41,670 (0) | 60,072 (0) | 159,804 (0) | < 1% failed |
| Requests per second | 67 | 174 | 250 | 666 | |

The table shows p95 times. During run 1, the busiest minute had the API task at 100% CPU and the database at 13%.

What this means:

- **A9 holds when the API has capacity.** With 10,000 items, deep pages, search and sorting cost about the same as the first page; none of them needs a new index.
- **One 0.5 vCPU task handles about 65 requests per second.** Above that, requests queue: run 1's median was 200 ms, but its fastest requests took 11 ms.
- **Autoscaling fixes it, but not straight away (A46).** It scales on 60% CPU and needs a few minutes to start a task. The extra tasks arrived after most of run 1, and run 2 is slower than run 4 while the new tasks' JVMs warm up. A sudden spike is served by one task until then. If that matters, set `MinTasks` to 2.
- **The load is far above real use.** 20 users who never pause send hundreds of requests per second; the sandbox normally serves a handful of people.

### Lighthouse (Chrome DevTools, desktop)

| Page | Performance (≥ 80) | Accessibility (≥ 90) | Best practices | Notes |
|---|---|---|---|---|
| Sign-in (`/login`) | _to fill_ | _to fill_ | _to fill_ | |
| Items (`/items`, signed in) | 94 ✓ | 96 ✓ | 92 | SEO 90 |

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

`-e VUS=5` changes the number of users (20 by default).

### 3. Lighthouse

Chrome DevTools → **Lighthouse**, mode **Navigation**, device **Desktop**, categories **Performance**, **Accessibility** and **Best practices**. For the Items page, sign in first and untick **Clear storage**, so the session in `sessionStorage` survives the reload.

## Cleaning up the seed

The seeded items stay in the database until they are deleted. To hide them from the demo, filter by a non-`Perf` category, or delete them as Admin. Deleting is a soft delete (A7), so their SKUs stay reserved, and importing the seed again restores them.
