import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Users, Target, Building2, Contact2, KanbanSquare, Handshake,
  CheckSquare, Phone, Mail, Megaphone, FileText, Briefcase, CalendarDays, MessageSquare,
  BarChart3, Search, Settings, UsersRound, Tags, Trophy, Bot, ScrollText,
  LogOut, Plus, Database, Menu, X, FolderOpen,
} from 'lucide-react'
import  { useAuth }  from '@/stores/auth'
import { Avatar } from '@/components/ui/Misc'
import { GlobalSearch } from './GlobalSearch'
import { NotificationsBell } from './NotificationsBell'
import { cn } from '@/lib/utils'
import { useQueryClient } from '@tanstack/react-query'

interface NavItem { to: string; label: string; icon: typeof LayoutDashboard; permission?: string; badge?: string }

const mainNav: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/my-day', label: 'My Day', icon: CalendarDays },
  { to: '/leads', label: 'Leads', icon: Users },
  { to: '/pipeline', label: 'Pipeline', icon: KanbanSquare },
  { to: '/deals', label: 'Deals', icon: Handshake },
  { to: '/tasks', label: 'Tasks', icon: CheckSquare },
  { to: '/calls', label: 'Calls', icon: Phone, permission: 'CALL_VIEW' },
  { to: '/chat', label: 'Team Chat', icon: MessageSquare },
  { to: '/companies', label: 'Companies', icon: Building2 },
  { to: '/contacts', label: 'Contacts', icon: Contact2 },
  { to: '/clients', label: 'Clients', icon: Briefcase, permission: 'CLIENT_VIEW' },
]

const engageNav: NavItem[] = [
  { to: '/emails', label: 'Emails', icon: Mail },
  { to: '/campaigns', label: 'Campaigns', icon: Megaphone, permission: 'CAMPAIGN_VIEW' },
  { to: '/meetings', label: 'Meetings', icon: CalendarDays },
  { to: '/proposals', label: 'Proposals', icon: FileText, permission: 'PROPOSAL_VIEW' },
]

const insightNav: NavItem[] = [
  { to: '/reports', label: 'Reports', icon: BarChart3, permission: 'REPORT_VIEW' },
  { to: '/documents', label: 'Documents', icon: FolderOpen },
]

const adminNav: NavItem[] = [
  { to: '/admin/users', label: 'Users', icon: UsersRound, permission: 'USER_VIEW' },
  { to: '/admin/teams', label: 'Teams', icon: UsersRound, permission: 'TEAM_VIEW' },
  { to: '/admin/roles', label: 'Roles & Permissions', icon: ScrollText, permission: 'ROLE_VIEW' },
  { to: '/admin/pipelines', label: 'Pipelines & Stages', icon: KanbanSquare, permission: 'PIPELINE_VIEW' },
  { to: '/admin/tags-sources', label: 'Tags & Sources', icon: Tags, permission: 'TAG_VIEW' },
  { to: '/admin/custom-fields', label: 'Custom Fields', icon: Database, permission: 'SETTINGS_UPDATE' },
  { to: '/admin/scoring', label: 'Lead Scoring', icon: Trophy, permission: 'SCORING_VIEW' },
  { to: '/admin/automations', label: 'Automations', icon: Bot, permission: 'AUTOMATION_VIEW' },
  { to: '/admin/audit', label: 'Audit Log', icon: ScrollText, permission: 'AUDIT_VIEW' },
  { to: '/admin/settings', label: 'Org Settings', icon: Settings, permission: 'SETTINGS_VIEW' },
]

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)

  const handleLogout = async () => {
    await logout()
    qc.clear()
    navigate('/login')
  }

  const NavSection = ({ title, items }: { title: string; items: NavItem[] }) => {
    const { can } = useAuth()
    const visible = items.filter((i) => !i.permission || can(i.permission))
    if (visible.length === 0) return null
    return (
      <div className="mt-5">
        <p className="px-3 text-[11px] font-bold uppercase tracking-wider text-slate-500">{title}</p>
        <div className="mt-1 space-y-0.5">
          {visible.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) =>
                cn('flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors', isActive ? 'bg-blue-600/15 text-white' : 'text-slate-300 hover:bg-white/5 hover:text-white')
              }
            >
              <item.icon className="h-4 w-4 shrink-0" />
              {item.label}
            </NavLink>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar */}
      <aside className={cn('fixed inset-y-0 left-0 z-40 w-64 shrink-0 overflow-y-auto bg-slate-900 px-3 py-4 transition-transform lg:static lg:translate-x-0', mobileOpen ? 'translate-x-0' : '-translate-x-full')}>
        <div className="flex items-center justify-between px-2">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-sm font-extrabold text-white">N</div>
            <div>
              <p className="text-sm font-bold text-white">Nexus CRM</p>
              <p className="text-[11px] text-slate-400">{user?.organizationName ?? 'Sales OS'}</p>
            </div>
          </div>
          <button className="text-slate-400 lg:hidden" onClick={() => setMobileOpen(false)} aria-label="Close menu"><X className="h-5 w-5" /></button>
        </div>
        <NavSection title="Workspace" items={mainNav} />
        <NavSection title="Engage" items={engageNav} />
        <NavSection title="Insights" items={insightNav} />
        <NavSection title="Administration" items={adminNav} />
      </aside>
      {mobileOpen && <div className="fixed inset-0 z-30 bg-slate-900/50 lg:hidden" onClick={() => setMobileOpen(false)} />}

      {/* Main */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center gap-3 border-b border-slate-200 bg-white px-4">
          <button className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 lg:hidden" onClick={() => setMobileOpen(true)} aria-label="Open menu">
            <Menu className="h-5 w-5" />
          </button>
          <button
            onClick={() => setSearchOpen(true)}
            className="flex h-9 w-full max-w-md items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm text-slate-400 hover:border-slate-300"
          >
            <Search className="h-4 w-4" />
            Search leads, companies, contacts…
            <kbd className="ml-auto hidden rounded border border-slate-200 bg-white px-1.5 text-[10px] text-slate-400 sm:block">⌘K</kbd>
          </button>
          <div className="ml-auto flex items-center gap-1.5">
            <QuickCreate />
            <NotificationsBell />
            <UserMenu name={user?.displayName} email={user?.email} roles={user?.roles} onLogout={handleLogout} />
          </div>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto bg-slate-100 p-4 lg:p-6">
          <Outlet />
        </main>
      </div>

      {searchOpen && <GlobalSearch open={searchOpen} onClose={() => setSearchOpen(false)} />}
    </div>
  )
}

function UserMenu({ name, email, roles, onLogout }: { name?: string; email?: string; roles?: string[]; onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="relative">
      <button onClick={() => setOpen(!open)} className="flex items-center gap-2 rounded-lg p-1 hover:bg-slate-100" aria-label="Account menu">
        <Avatar name={name} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-2 w-56 rounded-xl border border-slate-200 bg-white py-1 shadow-lg">
            <div className="border-b border-slate-100 px-4 py-3">
              <p className="truncate text-sm font-semibold text-slate-800">{name}</p>
              <p className="truncate text-xs text-slate-500">{email}</p>
              <div className="mt-1.5 flex flex-wrap gap-1">
                {roles?.map((r) => (
                  <span key={r} className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600">{r.replace('_', ' ')}</span>
                ))}
              </div>
            </div>
            <button onClick={onLogout} className="flex w-full items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">
              <LogOut className="h-4 w-4" /> Sign out
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function QuickCreate() {
  const navigate = useNavigate()
  const { can } = useAuth()
  const [open, setOpen] = useState(false)
  const items = [
    { label: 'Lead', to: '/leads?new=1', show: can('LEAD_CREATE') },
    { label: 'Company', to: '/companies?new=1', show: can('COMPANY_CREATE') },
    { label: 'Contact', to: '/contacts?new=1', show: can('CONTACT_CREATE') },
    { label: 'Deal', to: '/deals?new=1', show: can('DEAL_CREATE') },
    { label: 'Task', to: '/tasks?new=1', show: true },
    { label: 'Log call', to: '/calls?new=1', show: can('CALL_CREATE') },
  ].filter((i) => i.show)

  return (
    <div className="relative">
      <button onClick={() => setOpen(!open)} className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-blue-600 px-3 text-sm font-medium text-white hover:bg-blue-700">
        <Plus className="h-4 w-4" /> <span className="hidden sm:inline">Create</span>
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-2 w-40 rounded-xl border border-slate-200 bg-white py-1 shadow-lg">
            {items.map((i) => (
              <button key={i.label} onClick={() => { setOpen(false); navigate(i.to) }} className="block w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50">
                {i.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
