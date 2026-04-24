import { useEffect, useState } from 'react'
import { api, base } from '../services/api'
import type { Photo } from '../types'

export default function PhotosPage() {
  const [photos, setPhotos] = useState<Photo[]>([])
  const [people, setPeople] = useState<{ id: number; name: string }[]>([])
  const [personId, setPersonId] = useState('')
  const [year, setYear] = useState('')

  const load = () => {
    api.allPhotos({ person_id: personId ? Number(personId) : undefined, year: year || undefined }).then(setPhotos)
  }

  useEffect(() => {
    api.people().then(setPeople)
    load()
  }, [])

  return (
    <section className="card space-y-4">
      <h2 className="text-lg font-semibold">Photos Library</h2>
      <div className="flex flex-wrap gap-2 text-sm">
        <select className="rounded bg-slate-800 p-2" value={personId} onChange={e => setPersonId(e.target.value)}>
          <option value="">All people</option>
          {people.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <input className="rounded bg-slate-800 p-2" placeholder="Year (e.g., 1998)" value={year} onChange={e => setYear(e.target.value)} />
        <button className="rounded bg-violet-600 px-3" onClick={load}>Filter</button>
      </div>
      <div className="grid md:grid-cols-3 gap-3">
        {photos.map(photo => (
          <article key={photo.id} className="rounded-xl border border-slate-700 p-2 text-xs space-y-2">
            <img src={`${base}${photo.media_url}`} alt={photo.original_name} className="w-full h-40 object-cover rounded" />
            <div className="font-medium">{photo.original_name}</div>
            <div>{photo.confirmed_year || photo.suggested_year || 'Unknown Year'} • {photo.confirmed_event || photo.suggested_event || 'Unknown Event'}</div>
            <div>{photo.people.map(p => p.name).join(', ') || 'No people yet'}</div>
          </article>
        ))}
      </div>
    </section>
  )
}
