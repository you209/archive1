import type { Photo } from '../types'

const base = 'http://localhost:8000'

export const api = {
  async upload(files: File[]) {
    const form = new FormData()
    files.forEach(file => form.append('files', file))
    const res = await fetch(`${base}/photos/upload`, { method: 'POST', body: form })
    if (!res.ok) throw new Error('Upload failed')
    return res.json()
  },
  importFolder: (source_folder: string) =>
    fetch(`${base}/photos/import-folder`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source_folder })
    }).then(r => r.json()),
  reviewQueue: (): Promise<Photo[]> => fetch(`${base}/photos/review`).then(r => r.json()),
  importQueue: (): Promise<Photo[]> => fetch(`${base}/photos/import-queue`).then(r => r.json()),
  allPhotos: (params?: { person_id?: number; year?: string }): Promise<Photo[]> => {
    const query = new URLSearchParams()
    if (params?.person_id) query.set('person_id', String(params.person_id))
    if (params?.year) query.set('year', params.year)
    const suffix = query.toString() ? `?${query}` : ''
    return fetch(`${base}/photos/all${suffix}`).then(r => r.json())
  },
  timeline: () => fetch(`${base}/meta/timeline`).then(r => r.json()),
  people: () => fetch(`${base}/people`).then(r => r.json()),
  personDetail: (personId: number) => fetch(`${base}/people/${personId}`).then(r => r.json()),
  upsertPerson: (payload: unknown) =>
    fetch(`${base}/people`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(r => r.json()),
  addRelation: (payload: unknown) =>
    fetch(`${base}/people/relations`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(r => r.json()),
  familyTree: () => fetch(`${base}/people/tree`).then(r => r.json()),
  events: () => fetch(`${base}/meta/events`).then(r => r.json()),
  map: () => fetch(`${base}/meta/map`).then(r => r.json()),
  faceGroups: () => fetch(`${base}/photos/face-groups`).then(r => r.json()),
  queueFaceGroups: () => fetch(`${base}/photos/jobs/face-groups`, { method: 'POST' }).then(r => r.json()),
  jobStatus: (jobId: string) => fetch(`${base}/photos/jobs/${jobId}`).then(r => r.json()),
  exportFolders: (commit = false) =>
    fetch(`${base}/photos/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ commit })
    }).then(r => r.json()),
  queueExport: (commit = false) =>
    fetch(`${base}/photos/jobs/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ commit })
    }).then(r => r.json()),
  answer: (payload: unknown) =>
    fetch(`${base}/photos/answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(r => r.json()),
  batchAnswer: (payload: unknown) =>
    fetch(`${base}/photos/answer/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(r => r.json()),
  comparePeople: (payload: { person_a: string; person_b: string; same_person: boolean }) =>
    fetch(`${base}/photos/compare-people`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(r => r.json()),
  previewMerge: (payload: { person_a: string; person_b: string; same_person: boolean }) =>
    fetch(`${base}/photos/compare-people/preview`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(r => r.json()),
  undoMerge: () => fetch(`${base}/photos/compare-people/undo`, { method: 'POST' }).then(r => r.json()),
  confidenceSettings: () => fetch(`${base}/meta/settings/confidence`).then(r => r.json()),
  saveConfidenceSettings: (payload: { min_confidence_review: number; min_confidence_import_queue: number }) =>
    fetch(`${base}/meta/settings/confidence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(r => r.json())
}

export { base }
