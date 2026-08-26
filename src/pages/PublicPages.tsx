import { useMemo, useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowRight, CheckCircle2, Mail, RotateCcwKey, ShieldCheck } from 'lucide-react';
import { AuthShell } from '../components/AuthShell';
import { Button, Field, ProblemPanel, Spinner, SuccessPanel } from '../components/ui';
import { authApi } from '../lib/api';
import { useAuth } from '../lib/auth';

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  if (auth.authenticated) return <Navigate to="/app" replace />;
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(null); setBusy(true);
    try { await auth.login(email, password); navigate('/app', { replace: true }); }
    catch (nextError) { setError(nextError); }
    finally { setBusy(false); }
  }
  return <AuthShell title={<>Vuelve a tu <span className="gradient-text">sesión</span></>} description="Autentícate contra el backend modular y entra a la consola de pruebas."><form className="space-y-5" onSubmit={submit}><Field label="Correo" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required placeholder="tu@correo.com" /><Field label="Contraseña" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required hint={<Link className="text-[#0055FF] hover:underline" to="/forgot-password">¿Olvidaste tu contraseña?</Link>} /><ProblemPanel error={error} /><Button className="w-full" disabled={busy}>{busy ? <Spinner /> : <ShieldCheck className="h-4 w-4" />} Iniciar sesión</Button></form><p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">¿Aún no tienes cuenta? <Link className="font-semibold text-[#0055FF] hover:underline" to="/register">Crear cuenta</Link></p></AuthShell>;
}

export function RegisterPage() {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<unknown>(null); const [result, setResult] = useState<{ email: string; userId: string } | null>(null); const [busy, setBusy] = useState(false);
  const passwordError = password && password.length < 15 ? 'El backend exige al menos 15 caracteres.' : undefined;
  const confirmError = confirm && password !== confirm ? 'Las contraseñas no coinciden.' : undefined;
  async function submit(event: FormEvent) { event.preventDefault(); if (password.length < 15 || password !== confirm) return; setBusy(true); setError(null); try { const response = await authApi.register(email, password); setResult({ email: response.email, userId: response.userId }); } catch (nextError) { setError(nextError); } finally { setBusy(false); } }
  return <AuthShell title={<>Crea una cuenta de <span className="gradient-text">prueba</span></>} description="El registro crea una cuenta pendiente y encola la verificación de correo de forma durable.">{result ? <div className="space-y-5"><SuccessPanel><p className="font-semibold">Cuenta creada para {result.email}</p><p className="mt-1">User ID: <span className="font-mono text-xs">{result.userId}</span></p></SuccessPanel><div className="rounded-2xl border border-slate-200 p-5 dark:border-slate-800"><p className="font-semibold">Siguiente paso</p><p className="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">En perfil local, el backend imprime el correo y su enlace en logs. Ábrelo y caerás directamente en <code>/verify-email</code>.</p></div><Link to={`/verify-email?email=${encodeURIComponent(result.email)}`} className="focus-ring flex h-11 items-center justify-center gap-2 rounded-xl bg-[#0055FF] px-4 text-sm font-semibold text-white">Ir a verificación <ArrowRight className="h-4 w-4" /></Link></div> : <form className="space-y-5" onSubmit={submit}><Field label="Correo" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /><Field label="Contraseña" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={15} maxLength={128} hint="15–128 caracteres" error={passwordError} /><Field label="Confirmar contraseña" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required error={confirmError} /><ProblemPanel error={error} /><Button className="w-full" disabled={busy || Boolean(passwordError || confirmError)}>{busy ? <Spinner /> : <Mail className="h-4 w-4" />} Crear y enviar verificación</Button></form>}<p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">¿Ya tienes cuenta? <Link className="font-semibold text-[#0055FF] hover:underline" to="/login">Iniciar sesión</Link></p></AuthShell>;
}

export function VerifyEmailPage() {
  const [params] = useSearchParams(); const [token, setToken] = useState(params.get('token') || ''); const [email, setEmail] = useState(params.get('email') || '');
  const [error, setError] = useState<unknown>(null); const [success, setSuccess] = useState(false); const [resendSuccess, setResendSuccess] = useState(false); const [busy, setBusy] = useState(false);
  async function confirm(event: FormEvent) { event.preventDefault(); setBusy(true); setError(null); setSuccess(false); try { await authApi.confirmVerification(token.trim()); setSuccess(true); } catch (nextError) { setError(nextError); } finally { setBusy(false); } }
  async function resend() { if (!email) return; setBusy(true); setError(null); setResendSuccess(false); try { await authApi.resendVerification(email); setResendSuccess(true); } catch (nextError) { setError(nextError); } finally { setBusy(false); } }
  return <AuthShell title={<>Verifica tu <span className="gradient-text">correo</span></>} description="Consume el token del enlace o prueba el resend genérico sin revelar si la cuenta existe."><div className="space-y-6"><form className="space-y-4" onSubmit={confirm}><Field label="Token de verificación" value={token} onChange={(e) => setToken(e.target.value)} required maxLength={512} placeholder="Se completa desde ?token=..." /><Button className="w-full" disabled={busy || !token.trim()}>{busy ? <Spinner /> : <CheckCircle2 className="h-4 w-4" />} Confirmar verificación</Button></form>{success && <SuccessPanel>Correo verificado. Ya puedes iniciar sesión.</SuccessPanel>}<div className="border-t border-slate-200 pt-6 dark:border-slate-800"><p className="mb-3 text-sm font-semibold">¿Necesitas otro enlace?</p><Field label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} /><Button type="button" className="mt-3 w-full" onClick={resend} disabled={busy || !email}>Reenviar verificación</Button>{resendSuccess && <div className="mt-3"><SuccessPanel>Solicitud aceptada con respuesta genérica 202.</SuccessPanel></div>}</div><ProblemPanel error={error} /><Link to="/login" className="block text-center text-sm font-semibold text-[#0055FF] hover:underline">Volver al login</Link></div></AuthShell>;
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState(''); const [error, setError] = useState<unknown>(null); const [success, setSuccess] = useState(false); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(null); setSuccess(false); try { await authApi.requestPasswordReset(email); setSuccess(true); } catch (nextError) { setError(nextError); } finally { setBusy(false); } }
  return <AuthShell title={<>Recupera tu <span className="gradient-text">acceso</span></>} description="El backend responde igual para cuentas existentes y desconocidas. Aquí puedes observar esa propiedad."><form className="space-y-5" onSubmit={submit}><Field label="Correo" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />{success && <SuccessPanel>Solicitud aceptada. Revisa el enlace que el sender local imprime en los logs del backend.</SuccessPanel>}<ProblemPanel error={error} /><Button className="w-full" disabled={busy}>{busy ? <Spinner /> : <RotateCcwKey className="h-4 w-4" />} Solicitar recuperación</Button></form><p className="mt-6 text-center text-sm"><Link className="font-semibold text-[#0055FF] hover:underline" to="/login">Volver al login</Link></p></AuthShell>;
}

export function ResetPasswordPage() {
  const [params] = useSearchParams(); const initialToken = useMemo(() => params.get('token') || '', [params]);
  const [token, setToken] = useState(initialToken); const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState(''); const [error, setError] = useState<unknown>(null); const [success, setSuccess] = useState(false); const [busy, setBusy] = useState(false);
  const passwordError = password && password.length < 15 ? 'El backend exige al menos 15 caracteres.' : undefined; const confirmError = confirm && password !== confirm ? 'Las contraseñas no coinciden.' : undefined;
  async function submit(event: FormEvent) { event.preventDefault(); if (password.length < 15 || password !== confirm) return; setBusy(true); setError(null); setSuccess(false); try { await authApi.confirmPasswordReset(token, password); setSuccess(true); } catch (nextError) { setError(nextError); } finally { setBusy(false); } }
  return <AuthShell title={<>Define una nueva <span className="gradient-text">contraseña</span></>} description="Al completar el reset, el backend también revoca las sesiones y refresh tokens existentes."><form className="space-y-5" onSubmit={submit}><Field label="Token de recuperación" value={token} onChange={(e) => setToken(e.target.value)} required maxLength={512} /><Field label="Nueva contraseña" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={15} maxLength={128} required error={passwordError} hint="15–128 caracteres" /><Field label="Confirmar contraseña" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required error={confirmError} />{success && <SuccessPanel>Contraseña actualizada y sesiones previas revocadas. Ya puedes volver a iniciar sesión.</SuccessPanel>}<ProblemPanel error={error} /><Button className="w-full" disabled={busy || Boolean(passwordError || confirmError)}>{busy ? <Spinner /> : <RotateCcwKey className="h-4 w-4" />} Cambiar contraseña</Button></form><p className="mt-6 text-center text-sm"><Link className="font-semibold text-[#0055FF] hover:underline" to="/login">Ir al login</Link></p></AuthShell>;
}
