import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { useAuth } from './lib/auth';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from './pages/PublicPages';
import { ActivityPage, DashboardPage, RecoveryPage, SecurityLabPage, SessionsPage, TokensPage } from './pages/AppPages';

function ProtectedApp() {
  const { authenticated } = useAuth();
  if (!authenticated) return <Navigate to="/login" replace />;
  return <AppShell><Outlet /></AppShell>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route element={<ProtectedApp />}>
        <Route path="/app" element={<DashboardPage />} />
        <Route path="/app/sessions" element={<SessionsPage />} />
        <Route path="/app/tokens" element={<TokensPage />} />
        <Route path="/app/recovery" element={<RecoveryPage />} />
        <Route path="/app/lab" element={<SecurityLabPage />} />
        <Route path="/app/activity" element={<ActivityPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
