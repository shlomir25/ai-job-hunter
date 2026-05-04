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
