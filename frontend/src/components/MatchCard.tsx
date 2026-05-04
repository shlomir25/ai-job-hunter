import { Link } from 'react-router-dom'
import type { MatchView } from '../api/types.ts'

interface Props {
  match: MatchView
}

export default function MatchCard({ match }: Props) {
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
