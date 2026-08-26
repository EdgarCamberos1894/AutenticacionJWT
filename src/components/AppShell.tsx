import { useEffect, useState, type PropsWithChildren } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Activity, FlaskConical, KeyRound, LayoutDashboard, LogOut, Menu, Moon, RefreshCw, RotateCcwKey, ShieldCheck, Sun, X } from 'lucide-react';
import { API_BASE_URL } from '../lib/api';
import { useAuth } from '../lib/auth';
import { cn, SecondaryButton, Spinner, StatusPill } from './ui';

const THEME_KEY = 'authlab.theme';
type Theme = 'light' | 'dark';
function initialTheme(): Theme {
  const saved = localStorage.getItem(THEME_KEY);
  if (saved === 'light' || saved === 'dark') return saved;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}
function useTheme() {
  const [theme, setTheme] = useState<Theme>(initialTheme);
  useEffect(() => { document.documentElement.classList.toggle('dark', theme === 'dark'); localStorage.setItem(THEME_KEY, theme); }, [theme]);
  return { theme, toggle: () => setTheme((current) => current === 'dark' ? 'light' : 'dark') };
}

const navItems = [
  { to: '/app', label: 'Resumen', icon: LayoutDashboard, end: true },
  { to: '/app/sessions', label: 'Sesiones', icon: ShieldCheck },
  { to: '/app/tokens', label: 'Tokens', icon: KeyRound },
  { to: '/app/recovery', label: 'Recovery', icon: RotateCcwKey },
  { to: '/app/lab', label: 'Security Lab', icon: FlaskConical },
  { to: '/app/activity', label: 'Actividad API', icon: Activity },
];

export function AppShell({ children }: PropsWithChildren) {
  const auth = useAuth();
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const [open, setOpen] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleRefresh() { setRefreshing(true); try { await auth.refresh(); } finally { setRefreshing(false); } }
  async function handleLogout() { setLoggingOut(true); try { await auth.logout(); navigate('/login', { replace: true }); } finally { setLoggingOut(false); } }

  return <div className="min-h-screen bg-slate-50 text-slate-950 dark:bg-slate-950 dark:text-slate-100">
    <div className="fixed inset-x-0 top-0 z-40 flex h-16 items-center border-b border-slate-200/80 bg-white/90 px-4 backdrop-blur md:hidden dark:border-slate-800 dark:bg-slate-950/90"><button className="focus-ring rounded-lg p-2" onClick={() => setOpen(true)} aria-label="Abrir navegación"><Menu className="h-5 w-5" /></button><Brand compact /><button className="focus-ring ml-auto rounded-lg p-2" onClick={toggle} aria-label="Cambiar tema">{theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}</button></div>
    {open && <button className="fixed inset-0 z-40 bg-slate-950/45 md:hidden" onClick={() => setOpen(false)} aria-label="Cerrar navegación" />}
    <aside className={cn('fixed inset-y-0 left-0 z-50 flex w-[272px] flex-col border-r border-slate-200 bg-white px-4 py-5 transition-transform md:translate-x-0 dark:border-slate-800 dark:bg-slate-950', open ? 'translate-x-0' : '-translate-x-full')}>
      <div className="flex items-center justify-between px-2"><Brand /><button className="focus-ring rounded-lg p-2 md:hidden" onClick={() => setOpen(false)} aria-label="Cerrar navegación"><X className="h-5 w-5" /></button></div>
      <div className="mt-7 rounded-2xl border border-blue-100 bg-blue-50/80 p-3 dark:border-blue-900/40 dark:bg-blue-950/20"><div className="flex items-center justify-between gap-2"><span className="text-xs font-bold uppercase tracking-[0.15em] text-blue-700 dark:text-blue-300">Backend</span><StatusPill tone="green">conectable</StatusPill></div><p className="mt-2 truncate text-xs text-slate-600 dark:text-slate-400" title={API_BASE_URL}>{API_BASE_URL}</p></div>
      <nav className="mt-6 space-y-1.5" aria-label="Navegación principal">{navItems.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)} className={({ isActive }) => cn('focus-ring flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition', isActive ? 'bg-[#0055FF] text-white shadow-[0_10px_30px_-18px_rgba(0,85,255,.75)]' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-white')}><Icon className="h-[18px] w-[18px]" />{label}</NavLink>)}</nav>
      <div className="mt-auto space-y-3 border-t border-slate-200 pt-4 dark:border-slate-800"><div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-900"><p className="truncate text-sm font-semibold">{auth.email || 'Sesión autenticada'}</p><p className="mt-1 truncate text-xs text-slate-500">SID {auth.tokens?.sessionId}</p></div><div className="grid grid-cols-2 gap-2"><SecondaryButton onClick={handleRefresh} disabled={refreshing} className="px-2" title="Rotar refresh token ahora">{refreshing ? <Spinner /> : <RefreshCw className="h-4 w-4" />} Refresh</SecondaryButton><SecondaryButton onClick={toggle} className="px-2">{theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />} Tema</SecondaryButton></div><SecondaryButton onClick={handleLogout} disabled={loggingOut} className="w-full">{loggingOut ? <Spinner /> : <LogOut className="h-4 w-4" />} Cerrar sesión</SecondaryButton></div>
    </aside>
    <main className="min-h-screen pt-16 md:ml-[272px] md:pt-0"><div className="mx-auto w-full max-w-[1480px] px-4 py-6 sm:px-6 sm:py-8 lg:px-10 lg:py-10">{children}</div></main>
  </div>;
}

function Brand({ compact = false }: { compact?: boolean }) {
  return <div className={cn('flex items-center gap-3', compact && 'ml-2')}><div className="grid h-9 w-9 place-items-center rounded-xl bg-[#0055FF] text-white shadow-[0_12px_32px_-16px_rgba(0,85,255,.8)]"><ShieldCheck className="h-5 w-5" /></div><div className={compact ? 'hidden min-[390px]:block' : ''}><p className="font-display text-base font-bold leading-none">AuthLab</p><p className="mt-1 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">Security console</p></div></div>;
}
