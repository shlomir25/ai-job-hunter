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
