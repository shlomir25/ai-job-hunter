# Plan 6 — Frontend + Israeli Scrapers (AllJobs, JobMaster)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the React review-queue UI and add HTTP scrapers for AllJobs and JobMaster (Israeli job sites). End state: full v1 — a browser at `http://localhost:5173` that lets you review matches, edit drafts, send, and skip; plus two more sources feeding the pipeline alongside the IMAP alerts.

**Architecture:**
- New `frontend/` directory with a Vite + React app (JavaScript per user preference). State is local-component-level — no Redux. API calls go to `http://localhost:8080` in dev (CORS configured) or a same-origin path in production.
- Scrapers extend `backend/ingestion`. New `HttpScraper` interface, abstract `BaseHttpScraper` with shared sentinel-field validation (per spec §8.3), and two concrete implementations using Jsoup. Scrapers reuse the existing `IngestionService` pipeline via a small adapter.

**Reference spec:** `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` §3 (architecture, React UI), §5.3 (user flow / endpoints), §8.3 (scraper resilience), §10 (deployment), §12.2 (source list).

**Depends on:** Plans 1-5.

---

## File Structure

**`frontend/` (new):**
- `package.json`
- `vite.config.js`
- `index.html`
- `src/main.jsx`
- `src/App.jsx`
- `src/api/client.js`
- `src/pages/ReviewQueue.jsx`
- `src/pages/MatchDetail.jsx`
- `src/pages/CvUpload.jsx`
- `src/pages/Dashboard.jsx`
- `src/components/MatchCard.jsx`
- `src/components/DraftEditor.jsx`
- `src/styles.css`
- `src/__tests__/ReviewQueue.test.jsx`
- `src/__tests__/MatchDetail.test.jsx`
- `src/__tests__/CvUpload.test.jsx`
- `src/__tests__/api-client.test.js`
- `src/test-setup.js` — MSW server setup
- `e2e/smoke.spec.js` — Playwright

**`backend/ingestion/` (extended):**
- `src/main/kotlin/com/jobhunter/ingestion/scraper/HttpScraper.kt` — interface
- `src/main/kotlin/com/jobhunter/ingestion/scraper/BaseHttpScraper.kt` — abstract base with sentinel check
- `src/main/kotlin/com/jobhunter/ingestion/scraper/AllJobsScraper.kt`
- `src/main/kotlin/com/jobhunter/ingestion/scraper/JobMasterScraper.kt`
- `src/main/kotlin/com/jobhunter/ingestion/scraper/HttpScraperRegistry.kt`
- `src/main/kotlin/com/jobhunter/ingestion/service/ScraperIngestionService.kt`
- `src/test/resources/scrapers/{alljobs,jobmaster}/sample-1.html`
- `src/test/kotlin/com/jobhunter/ingestion/scraper/AllJobsScraperTest.kt`
- `src/test/kotlin/com/jobhunter/ingestion/scraper/JobMasterScraperTest.kt`
- `src/test/kotlin/com/jobhunter/ingestion/service/ScraperIngestionServiceTest.kt`

**`backend/ingestion/.../service/SourceConfigSeeder.kt`** (modify): add scraper sources.

**Root** (new): `package.json` script for built frontend → Spring static resources.

---

## Task 1: Bootstrap the React frontend (Vite + Vitest + RTL + MSW)

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.jsx`
- Create: `frontend/src/App.jsx`
- Create: `frontend/src/styles.css`
- Create: `frontend/src/test-setup.js`
- Create: `.gitignore` updates

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "ai-job-hunter-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "e2e": "playwright test"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^7.1.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "@vitejs/plugin-react": "^4.3.4",
    "jsdom": "^25.0.1",
    "msw": "^2.7.0",
    "vite": "^6.0.7",
    "vitest": "^2.1.8",
    "@playwright/test": "^1.49.1"
  }
}
```

- [ ] **Step 2: Create `frontend/vite.config.js`**

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.js'],
    globals: true,
  },
})
```

- [ ] **Step 3: Create `frontend/index.html`**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AI Job Hunter</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

- [ ] **Step 4: Create `frontend/src/main.jsx`**

```jsx
import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './styles.css'

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
)
```

- [ ] **Step 5: Create `frontend/src/App.jsx`** (placeholder; expanded in later tasks)

```jsx
import { Link, Route, Routes } from 'react-router-dom'
import ReviewQueue from './pages/ReviewQueue.jsx'
import MatchDetail from './pages/MatchDetail.jsx'
import CvUpload from './pages/CvUpload.jsx'
import Dashboard from './pages/Dashboard.jsx'

export default function App() {
  return (
    <div className="app">
      <nav className="nav">
        <Link to="/">Review queue</Link>
        <Link to="/cv">CV</Link>
        <Link to="/dashboard">Dashboard</Link>
      </nav>
      <Routes>
        <Route path="/" element={<ReviewQueue />} />
        <Route path="/matches/:id" element={<MatchDetail />} />
        <Route path="/cv" element={<CvUpload />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </div>
  )
}
```

- [ ] **Step 6: Create `frontend/src/styles.css`**

```css
* { box-sizing: border-box; }
body { font-family: system-ui, sans-serif; margin: 0; padding: 0; background: #fafafa; color: #222; }
.app { max-width: 960px; margin: 0 auto; padding: 1rem; }
.nav { display: flex; gap: 1rem; padding: 0.5rem 0; margin-bottom: 1rem; border-bottom: 1px solid #ddd; }
.nav a { color: #06f; text-decoration: none; }
.match-card { background: white; padding: 1rem; margin-bottom: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }
.match-card h3 { margin: 0 0 0.25rem; }
.match-card .score { font-size: 0.9em; color: #666; }
.draft-editor textarea { width: 100%; min-height: 200px; font-family: inherit; padding: 0.5rem; }
.button { padding: 0.5rem 1rem; border: 1px solid #06f; background: #06f; color: white; cursor: pointer; border-radius: 4px; }
.button.secondary { background: white; color: #06f; }
.button:disabled { opacity: 0.5; cursor: not-allowed; }
```

- [ ] **Step 7: Create `frontend/src/test-setup.js`**

```js
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'

export const handlers = []
export const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

export { http, HttpResponse }
```

- [ ] **Step 8: Update root `.gitignore`**

Add:
```gitignore
frontend/node_modules/
frontend/dist/
frontend/.vitest-cache/
frontend/test-results/
```

- [ ] **Step 9: Install and verify**

```bash
cd frontend && npm install
npm run build
```

Expected: builds successfully (will fail because `pages/*.jsx` don't exist yet — create empty stubs):

```bash
mkdir -p frontend/src/pages frontend/src/components frontend/src/api frontend/src/__tests__ frontend/e2e
cat > frontend/src/pages/ReviewQueue.jsx <<'EOF'
export default function ReviewQueue() { return <div>Review queue</div> }
EOF
cat > frontend/src/pages/MatchDetail.jsx <<'EOF'
export default function MatchDetail() { return <div>Match detail</div> }
EOF
cat > frontend/src/pages/CvUpload.jsx <<'EOF'
export default function CvUpload() { return <div>CV upload</div> }
EOF
cat > frontend/src/pages/Dashboard.jsx <<'EOF'
export default function Dashboard() { return <div>Dashboard</div> }
EOF
```

Re-run `npm run build` — should now succeed.

- [ ] **Step 10: Commit**

```bash
git add frontend .gitignore
git commit -m "chore: bootstrap React frontend with Vite, Vitest, MSW, Playwright"
```

---

## Task 2: API client + test

**Files:**
- Create: `frontend/src/api/client.js`
- Create: `frontend/src/__tests__/api-client.test.js`

- [ ] **Step 1: Write the failing test**

```js
import { describe, it, expect } from 'vitest'
import { server, http, HttpResponse } from '../test-setup.js'
import { listMatches, getMatch, draftMatch, sendMatch, skipMatch, listCvs, uploadCv, getDashboard } from '../api/client.js'

describe('api client', () => {
  it('listMatches returns array of matches', async () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([
      { id: 1, llmScore: 80, posting: { title: 'Backend' } },
    ])))
    const matches = await listMatches()
    expect(matches).toHaveLength(1)
    expect(matches[0].id).toBe(1)
  })

  it('getMatch returns single match', async () => {
    server.use(http.get('/api/matches/42', () => HttpResponse.json({ id: 42 })))
    const match = await getMatch(42)
    expect(match.id).toBe(42)
  })

  it('draftMatch posts to /draft', async () => {
    server.use(http.post('/api/matches/7/draft', () => HttpResponse.json({ subject: 'S', body: 'B' })))
    const drafted = await draftMatch(7)
    expect(drafted.subject).toBe('S')
  })

  it('sendMatch posts subject and body', async () => {
    let receivedBody
    server.use(http.post('/api/matches/9/send', async ({ request }) => {
      receivedBody = await request.json()
      return new HttpResponse(null, { status: 200 })
    }))
    await sendMatch(9, { subject: 'X', body: 'Y' })
    expect(receivedBody).toEqual({ subject: 'X', body: 'Y' })
  })

  it('skipMatch posts to /skip', async () => {
    let called = false
    server.use(http.post('/api/matches/3/skip', () => { called = true; return new HttpResponse(null, { status: 200 }) }))
    await skipMatch(3)
    expect(called).toBe(true)
  })

  it('uploadCv sends multipart', async () => {
    server.use(http.post('/api/cv', () => HttpResponse.json({ id: 1, label: 'default', parsedTextLength: 100, skills: [] })))
    const file = new File(['pdf'], 'cv.pdf', { type: 'application/pdf' })
    const result = await uploadCv(file, 'default')
    expect(result.id).toBe(1)
  })

  it('listCvs returns array', async () => {
    server.use(http.get('/api/cv', () => HttpResponse.json([])))
    expect(await listCvs()).toEqual([])
  })

  it('getDashboard returns object', async () => {
    server.use(http.get('/api/admin/queue/counts', () => HttpResponse.json({ INGESTED: 1, MATCHED: 5 })))
    expect(await getDashboard()).toEqual({ INGESTED: 1, MATCHED: 5 })
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend && npm test
```

Expected: FAIL — module does not exist.

- [ ] **Step 3: Implement `src/api/client.js`**

```js
async function jsonOrThrow(response) {
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  if (response.status === 204) return null
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

export async function listMatches() {
  return jsonOrThrow(await fetch('/api/matches'))
}

export async function getMatch(id) {
  return jsonOrThrow(await fetch(`/api/matches/${id}`))
}

export async function draftMatch(id) {
  return jsonOrThrow(await fetch(`/api/matches/${id}/draft`, { method: 'POST' }))
}

export async function sendMatch(id, { subject, body }) {
  return jsonOrThrow(await fetch(`/api/matches/${id}/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ subject, body }),
  }))
}

export async function skipMatch(id) {
  return jsonOrThrow(await fetch(`/api/matches/${id}/skip`, { method: 'POST' }))
}

export async function listCvs() {
  return jsonOrThrow(await fetch('/api/cv'))
}

export async function uploadCv(file, label = 'default') {
  const form = new FormData()
  form.append('file', file)
  form.append('label', label)
  return jsonOrThrow(await fetch('/api/cv', { method: 'POST', body: form }))
}

export async function getDashboard() {
  return jsonOrThrow(await fetch('/api/admin/queue/counts'))
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd frontend && npm test
```

Expected: PASS — all eight tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api frontend/src/__tests__/api-client.test.js
git commit -m "feat(fe): add API client with tests"
```

---

## Task 3: `MatchCard` component + `ReviewQueue` page + tests

**Files:**
- Create: `frontend/src/components/MatchCard.jsx`
- Modify: `frontend/src/pages/ReviewQueue.jsx`
- Create: `frontend/src/__tests__/ReviewQueue.test.jsx`

- [ ] **Step 1: Write the failing test**

```jsx
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import ReviewQueue from '../pages/ReviewQueue.jsx'

describe('ReviewQueue', () => {
  it('renders the loading state initially', () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([])))
    render(<MemoryRouter><ReviewQueue /></MemoryRouter>)
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('renders matches when loaded', async () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([
      { id: 1, llmScore: 85, cosineSimilarity: 0.78,
        posting: { title: 'Backend Engineer', company: 'Acme', location: 'Tel Aviv', contactEmail: 'jobs@acme.com' } },
      { id: 2, llmScore: 70, cosineSimilarity: 0.65,
        posting: { title: 'DevOps', company: 'Beta', location: 'Remote', contactEmail: 'careers@beta.com' } },
    ])))
    render(<MemoryRouter><ReviewQueue /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument())
    expect(screen.getByText('DevOps')).toBeInTheDocument()
    expect(screen.getByText(/85/)).toBeInTheDocument()
  })

  it('shows empty state when no matches', async () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([])))
    render(<MemoryRouter><ReviewQueue /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText(/no matches/i)).toBeInTheDocument())
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend && npm test -- ReviewQueue
```

Expected: FAIL.

- [ ] **Step 3: Implement `MatchCard.jsx`**

```jsx
import { Link } from 'react-router-dom'

export default function MatchCard({ match }) {
  const { posting, llmScore, cosineSimilarity, id } = match
  return (
    <div className="match-card">
      <h3>
        <Link to={`/matches/${id}`}>{posting.title || '(no title)'}</Link>
      </h3>
      <div>{posting.company || '(unknown company)'} · {posting.location || ''}</div>
      <div className="score">
        Score: {llmScore ?? '—'} · cosine: {cosineSimilarity?.toFixed(2)} ·
        {posting.contactEmail ? ` ✉ ${posting.contactEmail}` : ' no email'}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Implement `ReviewQueue.jsx`**

```jsx
import { useEffect, useState } from 'react'
import { listMatches } from '../api/client.js'
import MatchCard from '../components/MatchCard.jsx'

export default function ReviewQueue() {
  const [state, setState] = useState({ status: 'loading', data: [], error: null })

  useEffect(() => {
    let cancelled = false
    listMatches()
      .then(data => { if (!cancelled) setState({ status: 'ok', data, error: null }) })
      .catch(err => { if (!cancelled) setState({ status: 'error', data: [], error: err.message }) })
    return () => { cancelled = true }
  }, [])

  if (state.status === 'loading') return <div>Loading…</div>
  if (state.status === 'error') return <div>Error: {state.error}</div>
  if (state.data.length === 0) return <div>No matches yet — check back later.</div>

  return (
    <div>
      <h2>Review queue ({state.data.length})</h2>
      {state.data.map(m => <MatchCard key={m.id} match={m} />)}
    </div>
  )
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd frontend && npm test -- ReviewQueue
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/MatchCard.jsx \
        frontend/src/pages/ReviewQueue.jsx \
        frontend/src/__tests__/ReviewQueue.test.jsx
git commit -m "feat(fe): add ReviewQueue page and MatchCard"
```

---

## Task 4: `MatchDetail` page with `DraftEditor` + tests

**Files:**
- Create: `frontend/src/components/DraftEditor.jsx`
- Modify: `frontend/src/pages/MatchDetail.jsx`
- Create: `frontend/src/__tests__/MatchDetail.test.jsx`

- [ ] **Step 1: Write the failing test**

```jsx
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import MatchDetail from '../pages/MatchDetail.jsx'

const renderAt = (id) => render(
  <MemoryRouter initialEntries={[`/matches/${id}`]}>
    <Routes><Route path="/matches/:id" element={<MatchDetail />} /></Routes>
  </MemoryRouter>,
)

describe('MatchDetail', () => {
  it('shows posting info', async () => {
    server.use(http.get('/api/matches/1', () => HttpResponse.json({
      id: 1, llmScore: 85, cosineSimilarity: 0.78,
      reasoning: '{"strengths":["Kotlin"],"gaps":[],"summary":"strong"}',
      posting: { id: 10, title: 'Backend', company: 'Acme', requirements: 'Kotlin', contactEmail: 'jobs@acme.com' },
    })))
    renderAt(1)
    await waitFor(() => expect(screen.getByText('Backend')).toBeInTheDocument())
    expect(screen.getByText(/Kotlin/)).toBeInTheDocument()
  })

  it('clicking Draft fetches a draft and shows editor', async () => {
    server.use(
      http.get('/api/matches/1', () => HttpResponse.json({
        id: 1, posting: { title: 'Backend', contactEmail: 'jobs@acme.com' },
      })),
      http.post('/api/matches/1/draft', () => HttpResponse.json({
        subject: 'Application for Backend',
        body: 'I would like to apply.',
      })),
    )
    renderAt(1)
    await waitFor(() => expect(screen.getByText('Backend')).toBeInTheDocument())
    fireEvent.click(screen.getByText(/draft/i))
    await waitFor(() => expect(screen.getByDisplayValue('Application for Backend')).toBeInTheDocument())
    expect(screen.getByDisplayValue('I would like to apply.')).toBeInTheDocument()
  })

  it('clicking Send posts subject + body', async () => {
    let captured
    server.use(
      http.get('/api/matches/1', () => HttpResponse.json({
        id: 1, posting: { title: 'Backend', contactEmail: 'jobs@acme.com' },
      })),
      http.post('/api/matches/1/draft', () => HttpResponse.json({
        subject: 'S', body: 'B',
      })),
      http.post('/api/matches/1/send', async ({ request }) => {
        captured = await request.json()
        return new HttpResponse(null, { status: 200 })
      }),
    )
    renderAt(1)
    await waitFor(() => screen.getByText('Backend'))
    fireEvent.click(screen.getByText(/draft/i))
    await waitFor(() => screen.getByDisplayValue('S'))
    fireEvent.click(screen.getByText(/^send$/i))
    await waitFor(() => expect(captured).toEqual({ subject: 'S', body: 'B' }))
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend && npm test -- MatchDetail
```

Expected: FAIL.

- [ ] **Step 3: Implement `DraftEditor.jsx`**

```jsx
import { useState } from 'react'

export default function DraftEditor({ initial, onSend, onCancel, sending }) {
  const [subject, setSubject] = useState(initial.subject)
  const [body, setBody] = useState(initial.body)
  return (
    <div className="draft-editor">
      <label>
        Subject
        <input value={subject} onChange={e => setSubject(e.target.value)} style={{ width: '100%' }} />
      </label>
      <label>
        Body
        <textarea value={body} onChange={e => setBody(e.target.value)} />
      </label>
      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
        <button className="button" disabled={sending} onClick={() => onSend({ subject, body })}>
          {sending ? 'Sending…' : 'Send'}
        </button>
        <button className="button secondary" onClick={onCancel} disabled={sending}>Cancel</button>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Implement `MatchDetail.jsx`**

```jsx
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getMatch, draftMatch, sendMatch, skipMatch } from '../api/client.js'
import DraftEditor from '../components/DraftEditor.jsx'

export default function MatchDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [match, setMatch] = useState(null)
  const [error, setError] = useState(null)
  const [draft, setDraft] = useState(null)
  const [drafting, setDrafting] = useState(false)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    let cancelled = false
    getMatch(id)
      .then(m => { if (!cancelled) setMatch(m) })
      .catch(e => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [id])

  const onDraft = async () => {
    setDrafting(true); setError(null)
    try { setDraft(await draftMatch(id)) }
    catch (e) { setError(e.message) }
    finally { setDrafting(false) }
  }

  const onSend = async ({ subject, body }) => {
    setSending(true); setError(null)
    try {
      await sendMatch(id, { subject, body })
      navigate('/')
    } catch (e) { setError(e.message); setSending(false) }
  }

  const onSkip = async () => {
    if (!confirm('Skip this match?')) return
    try { await skipMatch(id); navigate('/') } catch (e) { setError(e.message) }
  }

  if (error) return <div>Error: {error}</div>
  if (!match) return <div>Loading…</div>

  const reasoning = match.reasoning ? JSON.parse(match.reasoning) : null

  return (
    <div>
      <h2>{match.posting.title}</h2>
      <div><strong>Company:</strong> {match.posting.company || '—'}</div>
      <div><strong>Location:</strong> {match.posting.location || '—'}</div>
      <div><strong>Contact:</strong> {match.posting.contactEmail || '— (no auto-send possible)'}</div>
      <div><strong>Score:</strong> {match.llmScore ?? '—'} (cosine: {match.cosineSimilarity?.toFixed(2)})</div>
      {reasoning && (
        <div style={{ margin: '1rem 0' }}>
          <strong>Strengths:</strong> {reasoning.strengths?.join(', ') || '—'}<br />
          <strong>Gaps:</strong> {reasoning.gaps?.join(', ') || '—'}<br />
          <em>{reasoning.summary}</em>
        </div>
      )}
      <details>
        <summary>Job description</summary>
        <p>{match.posting.description || '—'}</p>
        <p><strong>Requirements:</strong> {match.posting.requirements || '—'}</p>
      </details>

      {!draft ? (
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
          <button className="button" onClick={onDraft} disabled={drafting || !match.posting.contactEmail}>
            {drafting ? 'Drafting…' : 'Draft cover letter'}
          </button>
          <button className="button secondary" onClick={onSkip}>Skip</button>
        </div>
      ) : (
        <DraftEditor initial={draft} onSend={onSend} onCancel={() => setDraft(null)} sending={sending} />
      )}
    </div>
  )
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd frontend && npm test -- MatchDetail
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/DraftEditor.jsx \
        frontend/src/pages/MatchDetail.jsx \
        frontend/src/__tests__/MatchDetail.test.jsx
git commit -m "feat(fe): add MatchDetail page with DraftEditor"
```

---

## Task 5: `CvUpload` and `Dashboard` pages + tests

**Files:**
- Modify: `frontend/src/pages/CvUpload.jsx`
- Modify: `frontend/src/pages/Dashboard.jsx`
- Create: `frontend/src/__tests__/CvUpload.test.jsx`
- Create: `frontend/src/__tests__/Dashboard.test.jsx`

- [ ] **Step 1: Write CvUpload test**

```jsx
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import CvUpload from '../pages/CvUpload.jsx'

describe('CvUpload', () => {
  it('shows existing CVs', async () => {
    server.use(http.get('/api/cv', () => HttpResponse.json([
      { id: 1, label: 'default', fileName: 'cv.pdf', isActive: true, createdAt: '2026-04-29T10:00:00Z' },
    ])))
    render(<MemoryRouter><CvUpload /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('cv.pdf')).toBeInTheDocument())
  })

  it('uploads a file and refreshes list', async () => {
    let listCalls = 0
    server.use(
      http.get('/api/cv', () => { listCalls++; return HttpResponse.json([]) }),
      http.post('/api/cv', () => HttpResponse.json({ id: 5, label: 'default', parsedTextLength: 100, skills: [] })),
    )
    render(<MemoryRouter><CvUpload /></MemoryRouter>)
    await waitFor(() => expect(listCalls).toBeGreaterThan(0))

    const file = new File(['pdf'], 'me.pdf', { type: 'application/pdf' })
    const input = screen.getByLabelText(/file/i)
    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.click(screen.getByText(/upload/i))

    await waitFor(() => expect(listCalls).toBeGreaterThan(1))  // refreshed
  })
})
```

- [ ] **Step 2: Implement `CvUpload.jsx`**

```jsx
import { useEffect, useState } from 'react'
import { listCvs, uploadCv } from '../api/client.js'

export default function CvUpload() {
  const [cvs, setCvs] = useState([])
  const [file, setFile] = useState(null)
  const [label, setLabel] = useState('default')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const refresh = async () => setCvs(await listCvs())

  useEffect(() => { refresh().catch(e => setError(e.message)) }, [])

  const onUpload = async () => {
    if (!file) return
    setBusy(true); setError(null)
    try { await uploadCv(file, label); setFile(null); await refresh() }
    catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  return (
    <div>
      <h2>CV</h2>
      <div>
        <label>
          File
          <input type="file" accept=".pdf,.docx"
                 onChange={e => setFile(e.target.files[0] || null)} />
        </label>
        <label style={{ marginLeft: '1rem' }}>
          Label
          <input value={label} onChange={e => setLabel(e.target.value)} />
        </label>
        <button className="button" onClick={onUpload} disabled={busy || !file}>
          {busy ? 'Uploading…' : 'Upload'}
        </button>
      </div>
      {error && <div style={{ color: 'red', marginTop: '0.5rem' }}>{error}</div>}
      <h3>Existing CVs</h3>
      <ul>
        {cvs.map(c => (
          <li key={c.id}>
            {c.fileName} <em>({c.label})</em> {c.isActive && <strong>· active</strong>}
          </li>
        ))}
      </ul>
    </div>
  )
}
```

- [ ] **Step 3: Write Dashboard test**

```jsx
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import Dashboard from '../pages/Dashboard.jsx'

describe('Dashboard', () => {
  it('renders queue counts', async () => {
    server.use(http.get('/api/admin/queue/counts', () => HttpResponse.json({
      INGESTED: 1, PARSED: 2, EMBEDDED: 0, MATCHED: 5, FAILED: 0, IRRELEVANT: 3, OUT_OF_SCOPE: 4, CLASSIFIED: 1,
    })))
    render(<MemoryRouter><Dashboard /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('5')).toBeInTheDocument())
    expect(screen.getByText(/MATCHED/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Implement `Dashboard.jsx`**

```jsx
import { useEffect, useState } from 'react'
import { getDashboard } from '../api/client.js'

export default function Dashboard() {
  const [counts, setCounts] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getDashboard().then(setCounts).catch(e => setError(e.message))
  }, [])

  if (error) return <div>Error: {error}</div>
  if (!counts) return <div>Loading…</div>

  return (
    <div>
      <h2>Dashboard</h2>
      <table>
        <thead><tr><th>State</th><th>Count</th></tr></thead>
        <tbody>
          {Object.entries(counts).map(([state, n]) => (
            <tr key={state}><td>{state}</td><td>{n}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 5: Run all frontend tests**

```bash
cd frontend && npm test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/CvUpload.jsx \
        frontend/src/pages/Dashboard.jsx \
        frontend/src/__tests__/CvUpload.test.jsx \
        frontend/src/__tests__/Dashboard.test.jsx
git commit -m "feat(fe): add CvUpload and Dashboard pages"
```

---

## Task 6: Playwright smoke test

**Files:**
- Create: `frontend/playwright.config.js`
- Create: `frontend/e2e/smoke.spec.js`

- [ ] **Step 1: Create `playwright.config.js`**

```js
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://localhost:5173' },
  webServer: {
    command: 'npm run dev',
    port: 5173,
    reuseExistingServer: !process.env.CI,
  },
})
```

- [ ] **Step 2: Install Playwright browsers (one-time)**

```bash
cd frontend && npx playwright install chromium
```

- [ ] **Step 3: Write the smoke test**

```js
import { test, expect } from '@playwright/test'

test('app loads and shows the review queue route', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('Review queue', { exact: false })).toBeVisible()
})
```

- [ ] **Step 4: Run the smoke test**

This requires the backend stack to be running, but since the page only renders even with a 500 from the API (showing "Error: …"), we don't need backend up. The dev server proxies `/api` to localhost:8080; if 8080 is down, the page shows an error state, which still satisfies "app loaded."

```bash
cd frontend && npm run e2e
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/playwright.config.js frontend/e2e
git commit -m "test(fe): add Playwright smoke test"
```

---

## Task 7: HTTP scraper interface and base class

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/HttpScraper.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/BaseHttpScraper.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/HttpScraperRegistry.kt`

- [ ] **Step 1: Create `HttpScraper` interface**

```kotlin
package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft

interface HttpScraper {
    val sourceCode: String
    fun scrape(config: Map<String, String>): List<ParsedPostingDraft>
}
```

- [ ] **Step 2: Create `BaseHttpScraper` with sentinel check**

```kotlin
package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

abstract class BaseHttpScraper : HttpScraper {

    abstract fun fetchPages(config: Map<String, String>): List<Document>
    abstract fun parsePage(doc: Document): List<ParsedPostingDraft>

    override fun scrape(config: Map<String, String>): List<ParsedPostingDraft> {
        val all = fetchPages(config).flatMap { parsePage(it) }
        // Sentinel field check (per spec §8.3): the first parsed posting must have non-null
        // title AND company, otherwise abort the run.
        if (all.isNotEmpty()) {
            val first = all.first()
            require(!first.title.isNullOrBlank() && !first.company.isNullOrBlank()) {
                "Scraper $sourceCode produced posting with null title or company; selectors likely broken"
            }
        }
        return all
    }

    protected fun fetchHtml(url: String, userAgent: String = DEFAULT_UA): Document =
        Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(15_000)
            .get()

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
    }
}
```

- [ ] **Step 3: Create `HttpScraperRegistry`**

```kotlin
package com.jobhunter.ingestion.scraper

import org.springframework.stereotype.Component

@Component
class HttpScraperRegistry(private val scrapers: List<HttpScraper>) {
    fun byCode(code: String): HttpScraper? = scrapers.firstOrNull { it.sourceCode == code }
}
```

- [ ] **Step 4: Verify build**

```bash
./gradlew :backend:ingestion:compileKotlin
```

Expected: success.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper
git commit -m "feat: add HttpScraper interface, base class, and registry"
```

---

## Task 8: AllJobs scraper + fixture test

**Files:**
- Create: `backend/ingestion/src/test/resources/scrapers/alljobs/sample-1.html`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/AllJobsScraper.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/scraper/AllJobsScraperTest.kt`

- [ ] **Step 1: Create AllJobs fixture (representative)**

`backend/ingestion/src/test/resources/scrapers/alljobs/sample-1.html`:

```html
<!DOCTYPE html>
<html>
<body>
<div class="job-content-top">
  <a class="job-content-top-title" href="/job/123">Senior Backend Engineer</a>
  <span class="job-content-top-employer">Acme Robotics</span>
  <span class="job-content-top-location">Tel Aviv</span>
</div>
<div class="job-content-top">
  <a class="job-content-top-title" href="/job/456">DevOps Engineer</a>
  <span class="job-content-top-employer">Beta Cloud</span>
  <span class="job-content-top-location">Remote</span>
</div>
</body>
</html>
```

(Real site selectors may differ. When they change, fetch a fresh fixture, run the test, and update the scraper.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.ingestion.scraper

import org.junit.jupiter.api.Test
import org.jsoup.Jsoup
import kotlin.test.assertEquals

class AllJobsScraperTest {

    private val scraper = object : AllJobsScraper() {
        // Override fetchPages to use the fixture instead of the network.
        override fun fetchPages(config: Map<String, String>) = listOf(
            Jsoup.parse(
                javaClass.getResourceAsStream("/scrapers/alljobs/sample-1.html")!!
                    .bufferedReader().readText(),
            ),
        )
    }

    @Test
    fun `parses two postings from fixture`() {
        val results = scraper.scrape(emptyMap())
        assertEquals(2, results.size)
        assertEquals("Senior Backend Engineer", results[0].title)
        assertEquals("Acme Robotics", results[0].company)
        assertEquals("123", results[0].externalId)
    }

    @Test
    fun `sentinel check fails when first posting missing company`() {
        val brokenScraper = object : AllJobsScraper() {
            override fun fetchPages(config: Map<String, String>) = listOf(
                Jsoup.parse("""<div class="job-content-top">
                    <a class="job-content-top-title" href="/job/x">title</a>
                    </div>"""),
            )
        }
        try {
            brokenScraper.scrape(emptyMap())
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.scraper.AllJobsScraperTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `AllJobsScraper`**

```kotlin
package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.Instant

@Component
open class AllJobsScraper : BaseHttpScraper() {
    override val sourceCode = "SCRAPER_ALLJOBS"

    override fun fetchPages(config: Map<String, String>): List<Document> {
        val baseUrl = config["url"] ?: "https://www.alljobs.co.il/SearchResultsGuest.aspx"
        return listOf(fetchHtml(baseUrl))
    }

    override fun parsePage(doc: Document): List<ParsedPostingDraft> {
        val cards = doc.select("div.job-content-top")
        return cards.mapNotNull { card ->
            val link = card.selectFirst("a.job-content-top-title") ?: return@mapNotNull null
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            val id = ID_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
            ParsedPostingDraft(
                externalId = id,
                sourceUrl = href.ifBlank { null }?.let { if (it.startsWith("http")) it else "https://www.alljobs.co.il$it" },
                rawText = card.text(),
                rawHtml = card.outerHtml(),
                title = link.text().ifBlank { null },
                company = card.selectFirst(".job-content-top-employer")?.text()?.ifBlank { null },
                location = card.selectFirst(".job-content-top-location")?.text()?.ifBlank { null },
                postedAt = Instant.now(),
            )
        }
    }

    companion object {
        private val ID_PATTERN = Regex("/job/(\\d+)")
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.scraper.AllJobsScraperTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/ingestion/src/test/resources/scrapers/alljobs \
        backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/AllJobsScraper.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/scraper/AllJobsScraperTest.kt
git commit -m "feat: add AllJobs scraper with fixture-based test"
```

---

## Task 9: JobMaster scraper + fixture test

**Files:**
- Create: `backend/ingestion/src/test/resources/scrapers/jobmaster/sample-1.html`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/JobMasterScraper.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/scraper/JobMasterScraperTest.kt`

Same shape as AllJobs.

- [ ] **Step 1: Create fixture**

`backend/ingestion/src/test/resources/scrapers/jobmaster/sample-1.html`:

```html
<!DOCTYPE html>
<html>
<body>
<div class="job_card">
  <a class="job_title" href="/job/show.aspx?id=4242">Senior Platform Engineer</a>
  <div class="company_name">Cloud Co</div>
  <div class="location">Tel Aviv</div>
</div>
<div class="job_card">
  <a class="job_title" href="/job/show.aspx?id=5151">Backend Engineer</a>
  <div class="company_name">Acme</div>
  <div class="location">Remote</div>
</div>
</body>
</html>
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.ingestion.scraper

import org.junit.jupiter.api.Test
import org.jsoup.Jsoup
import kotlin.test.assertEquals

class JobMasterScraperTest {

    private val scraper = object : JobMasterScraper() {
        override fun fetchPages(config: Map<String, String>) = listOf(
            Jsoup.parse(
                javaClass.getResourceAsStream("/scrapers/jobmaster/sample-1.html")!!
                    .bufferedReader().readText(),
            ),
        )
    }

    @Test
    fun `parses postings`() {
        val results = scraper.scrape(emptyMap())
        assertEquals(2, results.size)
        assertEquals("Senior Platform Engineer", results[0].title)
        assertEquals("Cloud Co", results[0].company)
        assertEquals("4242", results[0].externalId)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.scraper.JobMasterScraperTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `JobMasterScraper`**

```kotlin
package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.Instant

@Component
open class JobMasterScraper : BaseHttpScraper() {
    override val sourceCode = "SCRAPER_JOBMASTER"

    override fun fetchPages(config: Map<String, String>): List<Document> {
        val baseUrl = config["url"] ?: "https://www.jobmaster.co.il/jobs/"
        return listOf(fetchHtml(baseUrl))
    }

    override fun parsePage(doc: Document): List<ParsedPostingDraft> {
        val cards = doc.select("div.job_card")
        return cards.mapNotNull { card ->
            val link = card.selectFirst("a.job_title") ?: return@mapNotNull null
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            val id = ID_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
            ParsedPostingDraft(
                externalId = id,
                sourceUrl = if (href.startsWith("http")) href else "https://www.jobmaster.co.il$href",
                rawText = card.text(),
                rawHtml = card.outerHtml(),
                title = link.text().ifBlank { null },
                company = card.selectFirst(".company_name")?.text()?.ifBlank { null },
                location = card.selectFirst(".location")?.text()?.ifBlank { null },
                postedAt = Instant.now(),
            )
        }
    }

    companion object {
        private val ID_PATTERN = Regex("[?&]id=(\\d+)")
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.scraper.JobMasterScraperTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/ingestion/src/test/resources/scrapers/jobmaster \
        backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scraper/JobMasterScraper.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/scraper/JobMasterScraperTest.kt
git commit -m "feat: add JobMaster scraper with fixture-based test"
```

---

## Task 10: `ScraperIngestionService` and source seed update

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/service/ScraperIngestionService.kt`
- Modify: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/service/SourceConfigSeeder.kt`
- Modify: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scheduler/IngestionScheduler.kt`

The service is a thin wrapper around `IngestionService`'s upsert path, using a scraper instead of an IMAP client.

- [ ] **Step 1: Implement `ScraperIngestionService`**

```kotlin
package com.jobhunter.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueNotifier
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.scraper.HttpScraperRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class ScraperIngestionService(
    private val sources: JobSourceRepository,
    private val postings: JobPostingRepository,
    private val queue: ProcessingQueueRepository,
    private val scrapers: HttpScraperRegistry,
    private val notifier: QueueNotifier,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun runSource(sourceCode: String): IngestionRunResult {
        val source = sources.findByCode(sourceCode)
            ?: error("Unknown source code: $sourceCode")
        val scraper = scrapers.byCode(sourceCode)
            ?: error("No scraper registered for source $sourceCode")

        var created = 0
        try {
            val cfg: Map<String, String> = mapper.readValue(source.config)
            val drafts = scraper.scrape(cfg)
            for (draft in drafts) {
                if (postings.findBySourceIdAndExternalId(source.id!!, draft.externalId) != null) continue
                val saved = postings.save(toEntity(source.id!!, draft))
                queue.save(ProcessingQueueRow(jobPostingId = saved.id!!, state = QueueState.INGESTED))
                created += 1
            }
            source.lastRunAt = Instant.now()
            source.lastRunStatus = "OK"
            source.lastRunError = null
        } catch (e: Exception) {
            log.warn(e) { "Scraper run failed for $sourceCode" }
            source.lastRunAt = Instant.now()
            source.lastRunStatus = "FAILED"
            source.lastRunError = e.message?.take(2000)
            throw e
        } finally {
            sources.save(source)
        }
        if (created > 0) notifier.notify("queue_event")
        return IngestionRunResult(emailsFetched = 0, postingsCreated = created)
    }

    private fun toEntity(sourceId: Long, draft: ParsedPostingDraft): JobPosting = JobPosting(
        sourceId = sourceId,
        externalId = draft.externalId,
        rawText = draft.rawText,
        rawHtml = draft.rawHtml,
        sourceUrl = draft.sourceUrl,
        title = draft.title,
        company = draft.company,
        location = draft.location,
        postedAt = draft.postedAt,
        capturedAt = Instant.now(),
    )
}
```

- [ ] **Step 2: Update `SourceConfigSeeder` to add scraper sources**

Modify the seeds list:

```kotlin
val seeds = listOf(
    "IMAP_LINKEDIN_ALERTS"  to """{"fromFilter":"@linkedin.com","folder":"INBOX"}""",
    "IMAP_INDEED_ALERTS"    to """{"fromFilter":"@indeed.com","folder":"INBOX"}""",
    "IMAP_GLASSDOOR_ALERTS" to """{"fromFilter":"@glassdoor.com","folder":"INBOX"}""",
    "SCRAPER_ALLJOBS"       to """{"url":"https://www.alljobs.co.il/SearchResultsGuest.aspx"}""",
    "SCRAPER_JOBMASTER"     to """{"url":"https://www.jobmaster.co.il/jobs/"}""",
)
val typeFor = mapOf(
    "IMAP_LINKEDIN_ALERTS" to com.jobhunter.core.domain.SourceType.IMAP,
    "IMAP_INDEED_ALERTS" to com.jobhunter.core.domain.SourceType.IMAP,
    "IMAP_GLASSDOOR_ALERTS" to com.jobhunter.core.domain.SourceType.IMAP,
    "SCRAPER_ALLJOBS" to com.jobhunter.core.domain.SourceType.SCRAPER,
    "SCRAPER_JOBMASTER" to com.jobhunter.core.domain.SourceType.SCRAPER,
)
for ((code, config) in seeds) {
    if (sources.findByCode(code) == null) {
        sources.save(JobSource(
            code = code,
            type = typeFor[code]!!,
            enabled = true,
            config = config,
        ))
        log.info { "Seeded JobSource $code" }
    }
}
```

- [ ] **Step 3: Update `IngestionScheduler` to run scrapers too**

Add a `ScraperIngestionService` dependency and a separate `@Scheduled` method:

```kotlin
@Component
class IngestionScheduler(
    private val sources: JobSourceRepository,
    private val service: IngestionService,
    private val scraperService: ScraperIngestionService,
    private val props: IngestionProperties,
) {
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT30S")
    fun runImap() {
        if (props.username.isBlank() || props.password.isBlank()) {
            log.info { "IMAP credentials not configured; skipping IMAP run" }
            return
        }
        for (src in sources.findByEnabledTrue().filter { it.type == com.jobhunter.core.domain.SourceType.IMAP }) {
            try {
                val result = service.runSource(
                    sourceCode = src.code,
                    host = props.host, port = props.port,
                    username = props.username, password = props.password,
                    maxMessages = props.maxMessagesPerRun,
                )
                log.info { "IMAP ${src.code}: fetched ${result.emailsFetched}, created ${result.postingsCreated}" }
            } catch (e: Exception) {
                log.warn(e) { "IMAP ${src.code} failed" }
            }
        }
    }

    @Scheduled(fixedDelayString = "PT60M", initialDelayString = "PT2M")
    fun runScrapers() {
        for (src in sources.findByEnabledTrue().filter { it.type == com.jobhunter.core.domain.SourceType.SCRAPER }) {
            try {
                val result = scraperService.runSource(src.code)
                log.info { "Scraper ${src.code}: created ${result.postingsCreated}" }
            } catch (e: Exception) {
                log.warn(e) { "Scraper ${src.code} failed" }
            }
        }
    }
}
```

The `IngestionSchedulerTest` from Plan 2 must be updated to construct with `scraperService = mockk(relaxed = true)`. Update that test accordingly.

- [ ] **Step 4: Run all backend tests**

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion
git commit -m "feat: add ScraperIngestionService and wire AllJobs/JobMaster sources"
```

---

## Task 11: Production build (React → Spring static resources)

**Files:**
- Create: `frontend/build-and-copy.sh`
- Modify: `backend/app/build.gradle.kts` to depend on the frontend build (optional)

- [ ] **Step 1: Create `frontend/build-and-copy.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "Building React app..."
npm run build

DEST="../backend/app/src/main/resources/static"
echo "Copying dist/ to $DEST"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -R dist/* "$DEST/"

echo "Done. Run './gradlew :backend:app:bootJar' to package the unified jar."
```

```bash
chmod +x frontend/build-and-copy.sh
```

- [ ] **Step 2: Verify the script works**

```bash
./frontend/build-and-copy.sh
```

Expected: copies `frontend/dist/*` into `backend/app/src/main/resources/static/`.

- [ ] **Step 3: Verify Spring Boot serves the index**

Add `backend/app/src/main/resources/static/` to `.gitignore` (the React build output is regenerated, no need to commit):

```gitignore
backend/app/src/main/resources/static/
```

Boot the app:

```bash
docker compose up -d postgres ollama
./gradlew :backend:app:bootRun
# In another terminal:
curl -I http://localhost:8080/
```

Expected: 200 OK with HTML content.

- [ ] **Step 4: Commit**

```bash
git add frontend/build-and-copy.sh .gitignore
git commit -m "chore: add script to build React and copy into Spring static resources"
```

---

## Task 12: Final README and tag

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew test
cd frontend && npm test
```

Expected: ALL tests pass.

- [ ] **Step 2: Update `README.md` with the full picture**

```markdown
# AI Job Hunter

Personal job-search and CV-matching system. Local-only, Hebrew + English, Kotlin + Spring Boot backend, React frontend.

See `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` for the full design.

## What it does

1. Pulls job postings from your Gmail job-alert emails (LinkedIn, Indeed, Glassdoor) and from AllJobs / JobMaster scrapers.
2. Parses them, classifies into categories, embeds with bge-m3.
3. Matches each posting against your CV using cosine + LLM rerank.
4. Surfaces top matches in a React review queue.
5. On click: drafts a language-matched cover letter; you review/edit; one click sends with your CV attached.

## Stack

Kotlin · Spring Boot 3.4 · Spring AI 1.0 · Postgres 16 + pgvector · Ollama (aya-expanse:32b + bge-m3) · React 19 · Vite · Vitest · Playwright · GreenMail · Testcontainers

## Dev setup

1. `docker compose up -d postgres ollama`
2. Pull models (one-time, ~22 GB):
   ```bash
   docker exec jobhunter-ollama ollama pull bge-m3
   docker exec jobhunter-ollama ollama pull aya-expanse:32b
   ```
3. Configure secrets — copy `backend/app/src/main/resources/application-local.yml.example` to `application-local.yml`, fill in IMAP + SMTP credentials.
4. Run backend in IntelliJ (`JobHunterApplication.kt`) or via `./gradlew :backend:app:bootRun`.
5. Run frontend: `cd frontend && npm install && npm run dev`.
6. Open `http://localhost:5173`.

## Tests

Backend: `./gradlew test`
Frontend: `cd frontend && npm test`
Smoke E2E: `cd frontend && npm run e2e`

## Production / always-on

```bash
./frontend/build-and-copy.sh
./gradlew :backend:app:bootJar
java -jar backend/app/build/libs/app.jar
```

Now both UI and API are served from `http://localhost:8080`.
```

- [ ] **Step 3: Commit and tag**

```bash
git add README.md
git commit -m "docs: full README for v1"
git tag v1
git tag plan-6-complete
```

---

## End of Plan 6

**Full v1 system shipped:**

- [x] React review queue UI at `http://localhost:5173` (dev) / `http://localhost:8080` (prod).
- [x] Match list, match detail with draft editor, CV upload, dashboard.
- [x] Vitest unit tests + Playwright smoke test all pass.
- [x] AllJobs and JobMaster scrapers feed the same pipeline as IMAP.
- [x] Sentinel-field check guards against silent scraper breakage.
- [x] Production build script bundles React into Spring's static resources.
- [x] One unified jar runs the entire stack.

**Beyond v1:** see spec §11 (Open Questions / Future Work) — bounce handling, daily digest email, multi-CV ranking, rate-limited sending.
