import { useEffect, useState } from 'react'
import { getDashboard } from '../api/client.ts'
import type { QueueCounts } from '../api/types.ts'

export default function Dashboard() {
  const [counts, setCounts] = useState<QueueCounts | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getDashboard().then(setCounts).catch((e: Error) => setError(e.message))
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
