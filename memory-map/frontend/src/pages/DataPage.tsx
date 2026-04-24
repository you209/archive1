import { useEffect, useState } from 'react'

type Props = { title: string; endpoint: () => Promise<any[]>; columns: string[] }

export default function DataPage({ title, endpoint, columns }: Props) {
  const [rows, setRows] = useState<any[]>([])

  useEffect(() => {
    endpoint().then(setRows)
  }, [endpoint])

  return (
    <section className="card">
      <h2 className="text-lg font-semibold mb-3">{title}</h2>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead><tr>{columns.map(col => <th key={col} className="text-left p-2">{col}</th>)}</tr></thead>
          <tbody>
            {rows.map((row, idx) => (
              <tr key={idx} className="border-t border-slate-800">{columns.map(col => <td key={col} className="p-2">{row[col.toLowerCase()] ?? row[col] ?? '-'}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
