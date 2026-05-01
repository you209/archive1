import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../services/api'

type Person = { id: number; name: string; birth_year?: string; death_year?: string; home_place?: string; notes?: string }

export default function PeoplePage() {
  const [people, setPeople] = useState<Person[]>([])
  const [name, setName] = useState('')
  const [birthYear, setBirthYear] = useState('')
  const [deathYear, setDeathYear] = useState('')
  const [homePlace, setHomePlace] = useState('')
  const [notes, setNotes] = useState('')
  const [status, setStatus] = useState('')

  const load = () => api.people().then(setPeople)

  useEffect(() => {
    load()
  }, [])

  const savePerson = async () => {
    if (!name.trim()) return
    await api.upsertPerson({ name, birth_year: birthYear || null, death_year: deathYear || null, home_place: homePlace || null, notes: notes || null })
    setStatus('Person profile saved.')
    setName('')
    setBirthYear('')
    setDeathYear('')
    setHomePlace('')
    setNotes('')
    load()
  }

  return (
    <section className="card space-y-4">
      <h2 className="text-lg font-semibold">People Profiles</h2>
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-slate-700 p-3 space-y-2 text-sm">
          <h3 className="font-medium">Create / update person profile</h3>
          <input className="w-full rounded bg-slate-800 p-2" placeholder="Full name" value={name} onChange={e => setName(e.target.value)} />
          <div className="grid grid-cols-2 gap-2">
            <input className="w-full rounded bg-slate-800 p-2" placeholder="Birth year" value={birthYear} onChange={e => setBirthYear(e.target.value)} />
            <input className="w-full rounded bg-slate-800 p-2" placeholder="Death year" value={deathYear} onChange={e => setDeathYear(e.target.value)} />
          </div>
          <input className="w-full rounded bg-slate-800 p-2" placeholder="Home place" value={homePlace} onChange={e => setHomePlace(e.target.value)} />
          <textarea className="w-full rounded bg-slate-800 p-2" placeholder="Bio / notes" value={notes} onChange={e => setNotes(e.target.value)} />
          <button className="rounded bg-violet-600 px-3 py-2" onClick={savePerson}>Save profile</button>
          {status && <p className="text-xs text-emerald-300">{status}</p>}
        </div>
        <div className="rounded-xl border border-slate-700 p-3">
          <h3 className="font-medium mb-2">Profiles database</h3>
          <ul className="text-sm space-y-2 max-h-80 overflow-auto">
            {people.map(person => (
              <li key={person.id} className="rounded bg-slate-900/60 p-2">
                <Link className="font-medium text-violet-300 hover:underline" to={`/people/${person.id}`}>{person.name}</Link>
                <div className="text-xs text-slate-300">{person.birth_year || '?'} - {person.death_year || ''}</div>
                <div className="text-xs text-slate-300">{person.home_place || 'Unknown place'}</div>
                {person.notes && <div className="text-xs text-slate-400">{person.notes}</div>}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
