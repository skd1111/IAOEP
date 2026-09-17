import { NavLink } from 'react-router-dom';
import { Activity, Search, BarChart3, Database, GitCompareArrows, ShieldCheck, GitCommit, Shield, TrendingUp, AlertTriangle, Cog, Sparkles } from 'lucide-react';
import { cn } from '@/lib/utils';

const nav = [
  { to: '/dashboard', label: 'Dashboard', icon: Activity },
  { to: '/traces', label: 'Traces', icon: Search },
  { to: '/datasets', label: 'Datasets', icon: Database },
  { to: '/evaluations', label: 'Evaluations', icon: BarChart3 },
  { to: '/regression', label: 'Regression', icon: GitCompareArrows },
  { to: '/approvals', label: 'Approvals', icon: ShieldCheck },
  { to: '/evolution', label: 'Evolution', icon: GitCommit },
  { to: '/federation', label: 'Federation', icon: Shield },
  { to: '/federation/timeline', label: 'Timeline', icon: TrendingUp },
  { to: '/federation/alerts', label: 'Alerts', icon: AlertTriangle },
];

const highlightNav = [
  { to: '/vs', label: 'Why IAOEP?', icon: Sparkles },
  { to: '/settings', label: 'Settings', icon: Cog },
];

export function Sidebar() {
  return (
    <aside className="w-60 border-r border-border bg-card flex flex-col">
      <div className="h-14 px-5 flex items-center border-b border-border">
        <div className="font-semibold text-base">IAOEP</div>
        <div className="ml-auto text-xs text-muted-foreground">v0.1.0</div>
      </div>
      <nav className="flex-1 p-3 space-y-1">
        {nav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
              )
            }
          >
            <item.icon className="w-4 h-4" />
            {item.label}
          </NavLink>
        ))}

        {/* 分隔线 + 高亮入口 */}
        <div className="border-t border-border my-2 pt-2 space-y-1">
          {highlightNav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors',
                  isActive
                    ? 'bg-purple-500/20 text-purple-400'
                    : 'text-purple-400/70 hover:bg-purple-500/10 hover:text-purple-400'
                )
              }
            >
              <item.icon className="w-4 h-4" />
              {item.label}
            </NavLink>
          ))}
        </div>
      </nav>
      <div className="p-3 border-t border-border">
        <button className="flex items-center gap-3 px-3 py-2 rounded-md text-sm text-muted-foreground hover:bg-accent w-full">
          <Cog className="w-4 h-4" />
          Settings
        </button>
        <div className="mt-3 px-3 text-xs text-muted-foreground">
          Intelligent Agent Observation<br />& Evaluation Platform
        </div>
      </div>
    </aside>
  );
}
