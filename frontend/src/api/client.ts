import type {
  CvListItem,
  CvUploadResponse,
  DraftedEmail,
  MatchView,
  QueueCounts,
  SendRequest,
} from './types.ts'

async function jsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  if (response.status === 204) return null as T
  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (null as T)
}

export async function listMatches(): Promise<MatchView[]> {
  return jsonOrThrow(await fetch('/api/matches'))
}

export async function getMatch(id: number | string): Promise<MatchView> {
  return jsonOrThrow(await fetch(`/api/matches/${id}`))
}

export async function draftMatch(id: number | string): Promise<DraftedEmail> {
  return jsonOrThrow(await fetch(`/api/matches/${id}/draft`, { method: 'POST' }))
}

export async function sendMatch(
  id: number | string,
  { subject, body }: SendRequest,
): Promise<void> {
  return jsonOrThrow(await fetch(`/api/matches/${id}/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ subject, body }),
  }))
}

export async function skipMatch(id: number | string): Promise<void> {
  return jsonOrThrow(await fetch(`/api/matches/${id}/skip`, { method: 'POST' }))
}

export async function listCvs(): Promise<CvListItem[]> {
  return jsonOrThrow(await fetch('/api/cv'))
}

export async function uploadCv(file: File, label = 'default'): Promise<CvUploadResponse> {
  const form = new FormData()
  form.append('file', file)
  form.append('label', label)
  return jsonOrThrow(await fetch('/api/cv', { method: 'POST', body: form }))
}

export async function getDashboard(): Promise<QueueCounts> {
  return jsonOrThrow(await fetch('/api/admin/queue/counts'))
}
