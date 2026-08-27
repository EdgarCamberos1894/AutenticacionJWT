import type { PropsWithChildren, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CheckCircle2,
  Fingerprint,
  KeyRound,
  MailCheck,
  RotateCcwKey,
  ShieldCheck,
} from 'lucide-react';
import { API_BASE_URL } from '../lib/api';
import { cn } from './ui';

export type AuthShellVariant = 'login' | 'register' | 'verify' | 'recovery' | 'reset';

type AuthShellProps = PropsWithChildren<{
  title: ReactNode;
  description: ReactNode;
  variant?: AuthShellVariant;
  asideTitle?: string;
}>;

const variants = {
  login: {
    eyebrow: 'Secure access',
    asideEyebrow: 'AuthLab access',
    asideTitle: 'Vuelve a una sesión real, no a una maqueta.',
    asideCopy: 'Autentícate contra Spring Security y continúa con el mismo access JWT, refresh rotatorio y session id que utiliza el backend.',
    highlight: 'Access JWT corto + refresh rotatorio',
    switchCaption: '¿Primera vez aquí?',
    switchTitle: 'Crear una cuenta de prueba',
    switchDescription: 'Recorre registro, verificación y login desde cero.',
    switchTo: '/register',
    icon: ShieldCheck,
    asideFirst: false,
  },
  register: {
    eyebrow: 'Account setup',
    asideEyebrow: 'One account flow',
    asideTitle: 'Crea la cuenta y deja que el backend haga el resto.',
    asideCopy: 'El alta conserva el contrato real: contraseña fuerte, cuenta pendiente, token de un solo uso y entrega de verificación durable.',
    highlight: 'Registro + verificación con token real',
    switchCaption: '¿Ya tienes cuenta?',
    switchTitle: 'Volver al login',
    switchDescription: 'Usa tu sesión existente y entra a la consola.',
    switchTo: '/login',
    icon: Fingerprint,
    asideFirst: true,
  },
  verify: {
    eyebrow: 'Email verification',
    asideEyebrow: 'Ownership check',
    asideTitle: 'Confirma el correo que controla esta cuenta.',
    asideCopy: 'El enlace generado por el backend aterriza aquí con su token real. También puedes comprobar el resend y su respuesta pública genérica.',
    highlight: 'Token de un solo uso + resend protegido',
    switchCaption: 'Después de verificar',
    switchTitle: 'Ir al login',
    switchDescription: 'Continúa con una sesión autenticada.',
    switchTo: '/login',
    icon: MailCheck,
    asideFirst: true,
  },
  recovery: {
    eyebrow: 'Account recovery',
    asideEyebrow: 'Recovery surface',
    asideTitle: 'Recupera acceso sin convertir el correo en un oráculo.',
    asideCopy: 'La solicitud pública mantiene una respuesta genérica y deja al backend aplicar rate limit, tokenización y entrega segura.',
    highlight: 'Respuesta genérica + rate limiting',
    switchCaption: '¿Recordaste tu contraseña?',
    switchTitle: 'Volver al login',
    switchDescription: 'No necesitas completar el recovery para regresar.',
    switchTo: '/login',
    icon: RotateCcwKey,
    asideFirst: false,
  },
  reset: {
    eyebrow: 'Password reset',
    asideEyebrow: 'Credential replacement',
    asideTitle: 'Cambia la credencial y corta las sesiones anteriores.',
    asideCopy: 'El token de recuperación se consume una sola vez. Al confirmar, el backend reemplaza la contraseña y revoca las sesiones y refresh tokens existentes.',
    highlight: 'Nueva contraseña + revocación de sesiones',
    switchCaption: 'Cuando termines',
    switchTitle: 'Iniciar sesión de nuevo',
    switchDescription: 'La nueva credencial será la única válida.',
    switchTo: '/login',
    icon: KeyRound,
    asideFirst: false,
  },
} satisfies Record<AuthShellVariant, {
  eyebrow: string;
  asideEyebrow: string;
  asideTitle: string;
  asideCopy: string;
  highlight: string;
  switchCaption: string;
  switchTitle: string;
  switchDescription: string;
  switchTo: string;
  icon: typeof ShieldCheck;
  asideFirst: boolean;
}>;

export function AuthShell({
  children,
  title,
  description,
  variant = 'login',
  asideTitle,
}: AuthShellProps) {
  const config = variants[variant];
  const InfoIcon = config.icon;

  return (
    <div className="auth-eventos relative min-h-screen overflow-hidden text-white">
      <div className="auth-backdrop" aria-hidden="true">
        <div className="auth-orb auth-orb-one" />
        <div className="auth-orb auth-orb-two" />
        <div className="auth-orb auth-orb-three" />
      </div>

      <header className="relative z-10 mx-auto flex w-full max-w-[72rem] items-center justify-between gap-4 px-5 py-5 sm:px-7 lg:px-8">
        <Link to="/login" className="focus-ring inline-flex items-center gap-3 rounded-full">
          <span className="grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-white/[.06] text-[var(--auth-accent)] shadow-[inset_0_1px_0_rgba(255,255,255,.06)]">
            <ShieldCheck className="h-[18px] w-[18px]" />
          </span>
          <span>
            <span className="font-display block text-base font-semibold tracking-tight text-white">AuthLab</span>
            <span className="block text-[9px] font-semibold uppercase tracking-[.22em] text-white/35">Authentication test console</span>
          </span>
        </Link>

        <div className="hidden items-center gap-2 rounded-full border border-white/[.08] bg-white/[.045] px-3 py-2 sm:flex">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 shadow-[0_0_12px_rgba(52,211,153,.75)]" />
          <span className="text-[10px] font-semibold uppercase tracking-[.16em] text-white/45">API</span>
          <span className="max-w-[240px] truncate font-mono text-[10px] text-white/70">{API_BASE_URL}</span>
        </div>
      </header>

      <main className="relative z-10 mx-auto flex w-full max-w-[72rem] items-center px-4 pb-8 pt-2 sm:px-6 sm:pb-10 lg:min-h-[calc(100vh-82px)] lg:px-8 lg:pb-12">
        <section className="auth-card w-full overflow-hidden rounded-[28px] border border-white/[.09] bg-[linear-gradient(180deg,rgba(11,18,34,.985),rgba(7,12,24,.995))] shadow-[0_36px_90px_rgba(2,8,20,.5)] sm:rounded-[30px]">
          <div className="grid lg:grid-cols-[.92fr_1.08fr]">
            <aside
              className={cn(
                'auth-info-panel relative hidden min-h-[38rem] overflow-hidden border-white/[.07] bg-[linear-gradient(180deg,rgba(9,15,28,.99),rgba(6,10,20,1))] lg:flex',
                config.asideFirst ? 'order-1 border-r' : 'order-2 border-l',
              )}
            >
              <div className="auth-info-glow" aria-hidden="true" />
              <div className="relative flex h-full w-full flex-col items-center justify-center px-8 py-10 text-center">
                <div className="mx-auto flex max-w-sm flex-col items-center">
                  <div className="inline-flex rounded-full border border-white/[.06] bg-white/[.035] px-3 py-1.5 text-[10px] font-semibold uppercase tracking-[.2em] text-[var(--auth-text-muted)]">
                    {config.asideEyebrow}
                  </div>

                  <div className="auth-icon-halo relative mt-6 inline-flex">
                    <div className="absolute inset-0 rounded-full bg-[radial-gradient(circle,rgba(143,229,255,.28),transparent_70%)] blur-2xl" />
                    <div className="relative inline-flex rounded-full border border-[var(--auth-border-strong)] bg-[linear-gradient(180deg,rgba(143,229,255,.12),rgba(18,53,74,.54))] p-3.5 text-[var(--auth-accent)] shadow-[0_16px_38px_rgba(6,16,34,.2)]">
                      <InfoIcon className="h-8 w-8" />
                    </div>
                  </div>

                  <h2 className="font-display mt-5 text-[1.65rem] font-semibold leading-[1.02] tracking-[-.045em] text-[var(--auth-text)]">
                    {asideTitle ?? config.asideTitle}
                  </h2>
                  <p className="mt-3 text-[.82rem] leading-6 text-[var(--auth-text-soft)]">{config.asideCopy}</p>

                  <div className="mt-6 flex w-full items-center gap-3 rounded-[18px] border border-white/[.05] bg-white/[.025] px-4 py-3 text-left">
                    <span className="rounded-full bg-[rgba(143,229,255,.08)] p-2 text-[var(--auth-accent)]">
                      <CheckCircle2 className="h-4 w-4" />
                    </span>
                    <p className="text-[.78rem] leading-5 text-[var(--auth-text-muted)]">{config.highlight}</p>
                  </div>

                  <Link
                    to={config.switchTo}
                    className="auth-switch group mt-4 flex w-full items-center justify-between rounded-[18px] border border-[rgba(143,229,255,.18)] bg-[linear-gradient(180deg,rgba(143,229,255,.075),rgba(255,255,255,.025))] px-4 py-3 text-left transition hover:border-[rgba(143,229,255,.34)] hover:bg-[linear-gradient(180deg,rgba(143,229,255,.11),rgba(255,255,255,.04))]"
                  >
                    <span>
                      <span className="block text-[9px] font-semibold uppercase tracking-[.22em] text-[var(--auth-accent)]">{config.switchCaption}</span>
                      <span className="mt-1 block text-sm font-semibold text-white">{config.switchTitle}</span>
                      <span className="mt-1 block text-[.72rem] leading-4 text-[var(--auth-text-soft)]">{config.switchDescription}</span>
                    </span>
                    <span className="ml-4 rounded-full border border-[rgba(143,229,255,.16)] bg-[rgba(143,229,255,.08)] p-2.5 text-[var(--auth-accent)] transition-transform group-hover:translate-x-0.5">
                      <ArrowRight className="h-4 w-4" />
                    </span>
                  </Link>
                </div>
              </div>
            </aside>

            <div
              className={cn(
                'auth-form-panel relative flex min-h-[38rem] flex-col bg-[linear-gradient(180deg,rgba(22,33,56,.985),rgba(12,18,32,.995))] px-5 py-7 sm:px-8 sm:py-8 lg:px-9 lg:py-9',
                config.asideFirst ? 'order-2' : 'order-1',
              )}
            >
              <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(143,229,255,.22),transparent)]" />
              <div className="mx-auto flex w-full max-w-[31rem] flex-1 flex-col justify-center">
                <div className="border-b border-white/[.06] pb-5">
                  <div className="flex flex-wrap items-center gap-3">
                    <p className="text-[10px] font-semibold uppercase tracking-[.28em] text-[var(--auth-accent)]">{config.eyebrow}</p>
                    <span className="rounded-full border border-white/[.06] bg-white/[.03] px-2 py-1 text-[9px] font-semibold uppercase tracking-[.14em] text-white/38">Backend connected</span>
                  </div>
                  <h1 className="font-display mt-4 text-[2rem] font-semibold leading-[1.02] tracking-[-.045em] text-[var(--auth-text)] sm:text-[2.25rem]">{title}</h1>
                  <p className="mt-3 max-w-[30rem] text-sm leading-6 text-[var(--auth-text-soft)]">{description}</p>
                </div>

                <div className="mt-6">{children}</div>

                <div className="mt-7 flex items-center justify-between gap-4 border-t border-white/[.06] pt-4 text-[10px] uppercase tracking-[.16em] text-white/28">
                  <span>AuthLab / browser acceptance</span>
                  <span className="hidden sm:inline">RFC 9457 aware</span>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
