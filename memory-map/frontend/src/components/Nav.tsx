import { NavLink } from 'react-router-dom'

const links = [
  'Upload',
  'Review',
  'Import Queue',
  'Photos',
  'Timeline',
  'People',
  'Family Tree',
  'Events',
  'Map',
  'Export Folders',
  'Settings'
]

export default function Nav() {
  return (
    <nav className="card sticky top-2 z-10">
      <h1 className="text-xl font-semibold mb-3">Memory Map</h1>
      <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
        {links.map(link => {
          const to = `/${link.toLowerCase().replaceAll(' ', '-')}`
          return (
            <NavLink
              key={link}
              to={to}
              className={({ isActive }) =>
                `rounded-xl px-3 py-2 text-sm transition ${isActive ? 'bg-violet-500 text-white' : 'bg-slate-800/70 hover:bg-slate-700'}`
              }
            >
              {link}
            </NavLink>
          )
        })}
      </div>
    </nav>
  )
}
