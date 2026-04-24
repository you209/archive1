import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

type Node = { id: number; label: string }
type Edge = { from: number; to: number; relation: string }

export default function FamilyTreePage() {
  const [nodes, setNodes] = useState<Node[]>([])
  const [edges, setEdges] = useState<Edge[]>([])
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [relation, setRelation] = useState('parent')

  const load = () => {
    api.familyTree().then(data => {
      setNodes(data.nodes || [])
      setEdges(data.edges || [])
    })
  }

  useEffect(() => {
    load()
  }, [])

  const saveRelation = async () => {
    if (!from || !to) return
    await api.addRelation({ from_person_id: Number(from), to_person_id: Number(to), relation_type: relation })
    load()
  }

  const positions = useMemo(() => {
    const radius = 170
    const centerX = 230
    const centerY = 220
    return Object.fromEntries(
      nodes.map((n, idx) => {
        const angle = (idx / Math.max(nodes.length, 1)) * Math.PI * 2
        return [n.id, { x: centerX + Math.cos(angle) * radius, y: centerY + Math.sin(angle) * radius }]
      })
    ) as Record<number, { x: number; y: number }>
  }, [nodes])

  const resolve = (id: number) => nodes.find(n => n.id === id)?.label || `#${id}`

  return (
    <section className="card space-y-4">
      <h2 className="text-lg font-semibold">Family Tree</h2>
      <div className="rounded-xl border border-slate-700 p-3 text-sm space-y-2">
        <h3 className="font-medium">Add relationship</h3>
        <div className="grid md:grid-cols-3 gap-2">
          <select className="rounded bg-slate-800 p-2" value={from} onChange={e => setFrom(e.target.value)}>
            <option value="">From person</option>
            {nodes.map(n => <option key={n.id} value={n.id}>{n.label}</option>)}
          </select>
          <select className="rounded bg-slate-800 p-2" value={to} onChange={e => setTo(e.target.value)}>
            <option value="">To person</option>
            {nodes.map(n => <option key={n.id} value={n.id}>{n.label}</option>)}
          </select>
          <select className="rounded bg-slate-800 p-2" value={relation} onChange={e => setRelation(e.target.value)}>
            <option value="parent">Parent</option>
            <option value="child">Child</option>
            <option value="spouse">Spouse</option>
            <option value="sibling">Sibling</option>
          </select>
        </div>
        <button className="rounded bg-violet-600 px-3 py-2" onClick={saveRelation}>Save relation</button>
      </div>

      <div className="rounded-xl border border-slate-700 p-3 overflow-auto">
        <svg width="460" height="440" viewBox="0 0 460 440" className="mx-auto">
          {edges.map((e, idx) => {
            const a = positions[e.from]
            const b = positions[e.to]
            if (!a || !b) return null
            return (
              <g key={idx}>
                <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke="#64748b" strokeWidth="1.5" />
                <text x={(a.x + b.x) / 2} y={(a.y + b.y) / 2} fill="#cbd5e1" fontSize="10">{e.relation}</text>
              </g>
            )
          })}
          {nodes.map(n => {
            const p = positions[n.id]
            if (!p) return null
            return (
              <g key={n.id}>
                <circle cx={p.x} cy={p.y} r="24" fill="#4f46e5" stroke="#a5b4fc" />
                <text x={p.x} y={p.y + 4} textAnchor="middle" fill="white" fontSize="10">{n.label.slice(0, 10)}</text>
              </g>
            )
          })}
        </svg>
      </div>

      <div className="rounded-xl border border-slate-700 p-3 text-sm">
        <h3 className="font-medium mb-2">Relations</h3>
        <ul className="space-y-1">
          {edges.map((e, idx) => <li key={idx}>{resolve(e.from)} — {e.relation} → {resolve(e.to)}</li>)}
        </ul>
      </div>
    </section>
  )
}
