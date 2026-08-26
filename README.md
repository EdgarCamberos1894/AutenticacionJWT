# AuthLab UI

Frontend independiente para probar de extremo a extremo la referencia `architecture/modular` de `AutenticacionJWT`.

No contiene código Spring ni pretende ser el frontend de un producto final. Es una consola de aceptación visual para comprobar el contrato HTTP, la autenticación y los casos de seguridad del backend desde un navegador real.

## Stack

- React 19 + TypeScript
- Vite
- Tailwind CSS
- React Router
- TanStack Query
- Lucide
- Sonner

La interfaz toma como referencia el lenguaje visual de EleVideo: jerarquía alta, superficies limpias, azul como acento, dashboard con métricas, tarjetas ricas, responsive y tema claro/oscuro.

## Qué se puede probar

- registro, verificación de email y password reset
- login, access JWT y refresh rotatorio
- refresh manual y automático una sola vez ante `401`
- logout y logout-all
- listado y revocación de sesiones
- inspección visual de JWT y `kid`
- probe manual de UUID para observar ownership/IDOR
- demostración controlada de la revocación stateless
- respuestas RFC 9457 y `Retry-After`
- rate-limit testing manual sin generador automático de tráfico

El activity log registra únicamente método, ruta, status, latencia, `ProblemDetails.code` y `Retry-After`. Nunca registra bodies, contraseñas o tokens.

## Tokens en el navegador

Esta es una consola de pruebas para un backend cuyo contrato actual devuelve access y refresh token en JSON. Ambos se conservan en `sessionStorage`, no en `localStorage`, para poder probar la rotación dentro de la pestaña.

No debe interpretarse como recomendación para una aplicación web de producción. En un producto real se debería decidir explícitamente si el refresh token se mueve a cookie HttpOnly/SameSite/Secure o a un patrón BFF según el modelo de amenazas.

## Backend

En otra copia/worktree:

```bash
git switch architecture/modular
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## AuthLab

En otra copia/worktree:

```bash
git switch frontend/auth-lab
npm install
npm run dev
```

Abre `http://localhost:3000`.

Vite usa el puerto `3000` con `strictPort` porque coincide deliberadamente con el CORS local y con los enlaces `http://localhost:3000/verify-email` y `http://localhost:3000/reset-password` que genera el backend local. De esta forma los links que imprime `LoggingTransactionalEmailSender` aterrizan directamente en las pantallas correctas.

## API remota

Por defecto `VITE_API_BASE_URL=http://localhost:8080`. Copia `.env.example` a `.env.local` para apuntar a otra instancia.

## Build

```bash
npm run typecheck
npm run build
```

## Separación del backend

La rama `frontend/auth-lab` tiene un árbol propio con únicamente frontend. No contiene `pom.xml`, Java, Flyway ni archivos del backend. `architecture/modular` permanece intacta.
