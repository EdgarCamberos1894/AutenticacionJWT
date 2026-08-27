import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, PropsWithChildren, ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react';
import { ApiError } from '../lib/api';

export function cn(...values: Array<string | false | null | undefined>) { return values.filter(Boolean).join(' '); }

export function Button({ className, children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className={cn('primary-button focus-ring inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-[#0055FF] px-4 text-sm font-semibold text-white transition hover:bg-[#0047d6] disabled:opacity-50', className)} {...props}>{children}</button>;
}
export function SecondaryButton({ className, children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className={cn('focus-ring inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800', className)} {...props}>{children}</button>;
}
export function DangerButton({ className, children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className={cn('focus-ring inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-rose-600 px-4 text-sm font-semibold text-white transition hover:bg-rose-700 disabled:opacity-50', className)} {...props}>{children}</button>;
}
export function Spinner({ className = 'h-4 w-4' }: { className?: string }) { return <Loader2 className={cn(className, 'animate-spin')} aria-hidden="true" />; }
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) { return <div className={cn('surface soft-shadow rounded-2xl', className)} {...props} />; }

export function Field({ label, hint, error, className, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: ReactNode; error?: string }) {
  return <label className="field-shell block space-y-2"><div className="flex items-center justify-between gap-3"><span className="field-label text-sm font-semibold text-slate-700 dark:text-slate-200">{label}</span>{hint && <span className="field-hint text-xs text-slate-400">{hint}</span>}</div><input className={cn('field-input focus-ring h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 dark:bg-slate-950 dark:text-slate-100', error ? 'border-rose-400' : 'border-slate-200 hover:border-slate-300 dark:border-slate-700 dark:hover:border-slate-600', className)} {...props} />{error && <p className="field-error text-xs font-medium text-rose-600">{error}</p>}</label>;
}

export function PageHeader({ eyebrow, title, description, action }: { eyebrow?: string; title: ReactNode; description: ReactNode; action?: ReactNode }) {
  return <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div className="max-w-3xl">{eyebrow && <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-[#0055FF]">{eyebrow}</p>}<h1 className="font-display text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl dark:text-white">{title}</h1><p className="mt-2 text-sm leading-6 text-slate-500 sm:text-base dark:text-slate-400">{description}</p></div>{action}</div>;
}

export function StatusPill({ tone, children }: PropsWithChildren<{ tone: 'green' | 'blue' | 'amber' | 'slate' | 'rose' }>) {
  const tones = {
    green: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/20',
    blue: 'bg-blue-50 text-blue-700 ring-blue-200 dark:bg-blue-500/10 dark:text-blue-300 dark:ring-blue-500/20',
    amber: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/20',
    slate: 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
    rose: 'bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-500/10 dark:text-rose-300 dark:ring-rose-500/20',
  };
  return <span className={cn('inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset', tones[tone])}>{children}</span>;
}

export function ProblemPanel({ error, title = 'El backend rechazó la solicitud' }: { error: unknown; title?: string }) {
  if (!error) return null;
  const apiError = error instanceof ApiError ? error : null;
  const detail = apiError?.problem?.detail || (error instanceof Error ? error.message : 'Error desconocido');
  const code = apiError?.problem?.code;
  return <div className="problem-panel rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-950 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-100"><div className="flex gap-3"><AlertCircle className="problem-icon mt-0.5 h-5 w-5 shrink-0 text-rose-600" /><div className="min-w-0"><p className="problem-title font-semibold">{title}</p><p className="problem-detail mt-1 text-sm text-rose-800 dark:text-rose-200">{detail}</p><div className="problem-meta mt-2 flex flex-wrap gap-2 text-xs">{apiError && <span>HTTP {apiError.status}</span>}{code && <span>• {String(code)}</span>}{apiError?.retryAfter && <span>• Retry-After: {apiError.retryAfter}s</span>}</div></div></div></div>;
}

export function SuccessPanel({ children }: PropsWithChildren) {
  return <div className="success-panel rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900 dark:border-emerald-900/60 dark:bg-emerald-950/25 dark:text-emerald-100"><div className="flex gap-3"><CheckCircle2 className="success-icon mt-0.5 h-5 w-5 shrink-0 text-emerald-600" /><div className="success-content">{children}</div></div></div>;
}
