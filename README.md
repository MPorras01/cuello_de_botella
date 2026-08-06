# Trancones Medellín 🚦

PWA para detectar trancones en tiempo real en Medellín e identificar el cuello de botella.

## Stack
- **Backend:** Java 21 + Spring Boot 3 WebFlux + PostgreSQL + Redis
- **Frontend:** Vue 3 + Vite + MapLibre GL + Chart.js + vite-plugin-pwa

## Levantar en local

### Backend
```bash
cd backend
cp ../.env.example .env  # completar API keys
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Fuentes de datos oficiales (tiempo real) 🛰️

El backend consulta hasta **4 fuentes oficiales en paralelo** cada 30 s. Cada fuente se activa automáticamente cuando su variable de entorno está configurada; si una falla, se omite y se continúa con las demás. Los segmentos incluyen coordenadas/geometría para que los trancones y cuellos de botella se dibujen sobre el mapa.

| Fuente | Variable(s) | Acceso | Cobertura |
|---|---|---|---|
| **TomTom Traffic** (recomendada) | `TOMTOM_API_KEY` | Free tier, sin aprobación | Flujo y velocidad en tiempo real en 12 corredores del Valle de Aburrá (`traffic.sources.tomtom.points`) |
| **Google Routes** | `GOOGLE_MAPS_API_KEY` | Google Cloud (Routes API + facturación, $200/mes gratis) | Velocidad por tramos de ruta (`TRAFFIC_ON_POLYLINE`) |
| **SIMM / Datos abiertos Medellín** | `SIMM_RESOURCE_ID` (+ campos) | Público (CKAN), sin key | Sensores de velocidad de la ciudad |
| **Waze for Cities** | `WAZE_API_URL` | Requiere aprobación institucional | Alertas y congestiones colaborativas |

### Cómo activar cada fuente

1. **TomTom (la más fácil)**: crea cuenta gratis en [developer.tomtom.com](https://developer.tomtom.com), genera tu `API Key` (plan Free: 2.500–20.000 req/mes) y ponla en `TOMTOM_API_KEY`. Los 12 puntos de muestreo están preconfigurados en `application.yml` (se pueden editar).
2. **Google Routes**: habilita *Routes API* en [Google Cloud Console](https://console.cloud.google.com), crea una API Key y ponla en `GOOGLE_MAPS_API_KEY`.
3. **SIMM**: en [datosabiertos.medellin.gov.co](https://datosabiertos.medellin.gov.co) busca el dataset de sensores de velocidad/monitoreo de tráfico, copia el `resource_id` del recurso en `SIMM_RESOURCE_ID`. Si las columnas tienen otros nombres, ajusta `SIMM_SPEED_FIELD`, `SIMM_FREE_FLOW_FIELD`, `SIMM_NAME_FIELD`, `SIMM_LAT_FIELD`, `SIMM_LNG_FIELD`.
4. **Waze**: solicita acceso al programa *Waze for Cities* (solo sector público) y pega la URL de tu feed en `WAZE_API_URL`.

Copia `.env.example` a `.env`, completa las claves y cárgalas en el entorno antes de arrancar el backend.

> Sin ninguna clave configurada, el backend arranca igual y el frontend muestra **modo demo** (trancones simulados) para que la visualización de cuellos de botella sea visible. Con la primera fuente real activa, el modo demo se desactiva automáticamente.

## Seguridad 🔐

Toda la API (`/api/**`) requiere autenticación con **JWT** (Bearer token).
Único endpoint público: `POST /api/auth/login`.

### Variables de entorno (backend)

| Variable | Obligatoria | Descripción |
|---|---|---|
| `JWT_SECRET` | No* | Clave HMAC-SHA256 de **mínimo 32 caracteres** para firmar JWT. Si no se configura se genera una aleatoria por arranque. *Obligatoria en producción.* |
| `ADMIN_USERNAME` | No | Usuario inicial (default: `admin`). |
| `ADMIN_PASSWORD` | No* | Contraseña inicial (hash BCrypt en BD). Si no se configura, se genera una aleatoria impresa **una sola vez** en el log de arranque. *Obligatoria en producción.* |
| `JWT_EXPIRATION_MINUTES` | No | Vida del token en minutos (default: `15`). |
| `CORS_ORIGINS` | No | Orígenes permitidos separados por coma (default: `http://localhost:5173`). |
| `TRUST_FORWARDED_FOR` | No | `true` solo si el backend está detrás de un proxy de confianza (nginx, ALB) que reescribe `X-Forwarded-For`; se usa esa IP para el rate-limit (default: `false`). |
| `GOOGLE_MAPS_API_KEY`, `WAZE_API_URL`, `SIMM_API_URL` | Sí | Claves de las fuentes de tráfico. |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | Sí | Claves VAPID para Web Push. |

### Medidas implementadas

- **JWT firmado con HMAC-SHA256**, expiración corta (15 min) y rol embebido como claim.
- **Contraseñas con hash BCrypt** (costo 12); nunca se almacenan en claro.
- **Anti fuerza bruta**: rate-limit de login por IP (5 intentos / 15 min) con Redis y fallback en memoria.
- **Anti enumeración de usuarios**: mismo mensaje y tiempo de respuesta para usuario inexistente o contraseña incorrecta.
- **Validación estricta de entrada** en todos los endpoints (DTOs y parámetros).
- **Headers de seguridad**: CSP, HSTS, X-Frame-Options DENY, X-Content-Type-Options nosniff, Referrer-Policy, Permissions-Policy.
- **CORS restringido** a orígenes explícitos con headers mínimos.
- **Errores sin fuga de información**: el cliente solo recibe mensajes genéricos; los detalles van al log del servidor.
- El frontend envía el token en el header `Authorization` (nunca en la URL, incluido el stream SSE vía fetch). El token se guarda en `localStorage`: es el modelo estándar para SPAs, pero asume que no hay XSS persistente (mitigado con CSP).
- Los headers de seguridad los emite el backend; en producción, el servidor que sirve el SPA (nginx, etc.) debe aplicar los mismos headers.
- El service worker de la PWA **no** cachea respuestas de `/api/*` (evita retener datos autenticados tras el logout).

### Flujo de login

1. `POST /api/auth/login` con `{ "username": "...", "password": "..." }` → `{ "token", "tokenType", "expiresInSeconds", "username" }`.
2. Enviar `Authorization: Bearer <token>` en el resto de llamadas.
3. Los tokens expiran; el frontend cierra sesión automáticamente al recibir 401.

## Orden de desarrollo (para el agente)
1. Implementar fetchFromGoogle() en TrafficAggregatorService
2. Implementar fetchFromWaze()
3. Implementar fetchFromSIMM()
4. HistoryService + persist each snapshot
5. NotificationService (VAPID Web Push)
6. TrafficHistory.vue con Chart.js
7. PushToggle.vue
8. Build PWA + test install iOS/Android
