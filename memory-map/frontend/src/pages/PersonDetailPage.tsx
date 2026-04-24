import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, base } from '../services/api'

export default function PersonDetailPage() {
  const { personId } = useParams()
  const [detail, setDetail] = useState<any>(null)

  useEffect(() => {
    if (!personId) return
    api.personDetail(Number(personId)).then(setDetail)
  }, [personId])

  if (!detail) return <section className="card">Loading person profile...</section>

  return (
    <section className="card space-y-4">
      <h2 className="text-lg font-semibold">{detail.profile.name}</h2>
      <p className="text-sm text-slate-300">{detail.profile.birth_year || '?'} - {detail.profile.death_year || 'present'} · {detail.profile.home_place || 'Unknown place'}</p>
      {detail.profile.notes && <p className="text-sm text-slate-300">{detail.profile.notes}</p>}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-slate-700 p-3">
          <h3 className="font-medium mb-2">Relatives</h3>
          <ul className="text-sm space-y-1">
            {detail.relatives.map((relative: any) => (
              <li key={`${relative.id}-${relative.relation}`}>{relative.name} · {relative.relation}</li>
            ))}
          </ul>
        </div>
        <div className="rounded-xl border border-slate-700 p-3">
          <h3 className="font-medium mb-2">Places</h3>
          <ul className="text-sm space-y-1">
            {detail.places.map((place: any) => (
              <li key={place.place}>{place.place} ({place.count})</li>
            ))}
          </ul>
        </div>
      </div>

      <div className="rounded-xl border border-slate-700 p-3">
        <h3 className="font-medium mb-2">Photo timeline</h3>
        <ul className="text-sm space-y-1 mb-3">
          {detail.timeline.map((item: any) => (
            <li key={item.year}>{item.year}: {item.count}</li>
          ))}
        </ul>
        <div className="grid gap-2 md:grid-cols-3">
          {detail.photos.map((photo: any) => (
            <article key={photo.id} className="rounded bg-slate-900/60 p-2 text-xs">
              <img src={`${base}${photo.media_url}`} alt={photo.original_name} className="h-28 w-full object-cover rounded mb-2" />
              <div>{photo.original_name}</div>
              <div className="text-slate-400">{photo.year} · {photo.place}</div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
