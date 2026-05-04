import { useEffect, useState } from 'react'
import { listMatches } from '../api/client.ts'
import MatchCard from '../components/MatchCard.tsx'
import type { MatchView } from '../api/types.ts'

type State =
  | { status: 'loading' }
  | { status: 'ok'; data: MatchView[] }
  | { status: 'error'; error: string }

export default function ReviewQueue() {
  const [state, setState] = useState<State>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    listMatches()
      .then(data => { if (!cancelled) setState({ status: 'ok', data }) })
      .catch((err: Error) => { if (!cancelled) setState({ status: 'error', error: err.message }) })
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
