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
