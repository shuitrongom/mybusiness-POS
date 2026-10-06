# Despliegue en la nube — MyBusiness Silva (Opción A: servicios gestionados)

Guía paso a paso para publicar el sistema. Son 3 piezas: **base de datos**, **backend** y
**frontend**, más tu **dominio**. Costo de arranque: casi gratis / ~10–15 USD al mes.

Servicios recomendados (todos con plan gratis o barato para empezar):
- **Base de datos:** [Neon](https://neon.tech) (PostgreSQL serverless, gratis para empezar).
- **Backend:** [Railway](https://railway.app) (corre el Docker del backend, ~5 USD/mes).
- **Frontend:** [Vercel](https://vercel.com) (gratis).
- **Dominio + DNS:** [Cloudflare](https://cloudflare.com) (el más barato, DNS gratis).

Decisión tomada: **todos los negocios entran por el mismo dominio** (ej. `app.tudominio.com`) y
eligen su negocio al iniciar sesión. Más simple y suficiente para empezar.

---

## PASO 0 — Antes de empezar (ya está listo en el código)

Ya dejé preparado:
- `backend/Dockerfile` — compila y corre el backend.
- `backend/src/main/resources/application-prod.yml` — perfil de producción.
- Toda la config sensible por **variables de entorno** (BD, JWT, CORS, PAC).
- `frontend-web/.env.example` — variable `VITE_API_URL` para apuntar al backend.
- CORS configurable por `CORS_ALLOWED_ORIGINS`.

No necesitas tocar código. Solo seguir los pasos de abajo.

---

## PASO 1 — Comprar el dominio (Cloudflare)

1. Entra a https://dash.cloudflare.com → **Registrar dominio** (Domain Registration → Register).
2. Busca el nombre que quieres (ej. `mybusinesssilva.com`). Cómpralo (~15 USD/año).
3. Al terminar, el dominio ya queda con DNS de Cloudflare (lo usaremos en el paso 6).

Subdominios que usaremos:
- `app.tudominio.com` → el frontend (lo que usan los negocios).
- `api.tudominio.com` → el backend.

---

## PASO 2 — Base de datos (Neon)

1. Entra a https://neon.tech y crea cuenta (gratis).
2. **Create Project** → nombre `mybusiness-silva`, región la más cercana (ej. `AWS us-east-1`).
3. Al crearlo te da una **cadena de conexión** tipo:
   `postgresql://USUARIO:PASSWORD@HOST/neondb?sslmode=require`
4. Guarda esos datos. Los necesitamos como:
   - `DB_URL` = `jdbc:postgresql://HOST/neondb?sslmode=require`  ← ojo: empieza con `jdbc:`
   - `DB_USERNAME` = el USUARIO
   - `DB_PASSWORD` = el PASSWORD
5. En Neon, el usuario ya es dueño del esquema, así que el rol de la app es ese mismo usuario.
   Si Flyway se queja de permisos, en la consola SQL de Neon ejecuta:
   `GRANT ALL ON SCHEMA public TO USUARIO;` (normalmente no hace falta).

> Las migraciones (Flyway) corren solas al arrancar el backend y crean todo el esquema `admin`
> y los tenants. No tienes que crear tablas a mano.

---

## PASO 3 — Backend (Railway)

1. Sube el proyecto a **GitHub** (si aún no está). Railway despliega desde el repo.
2. Entra a https://railway.app → **New Project** → **Deploy from GitHub repo** → elige tu repo.
3. En **Settings → Root Directory** pon `backend` (para que use `backend/Dockerfile`).
4. Railway detecta el Dockerfile y construye. Mientras, ve a **Variables** y agrega:
   ```
   SPRING_PROFILES_ACTIVE = prod
   DB_URL        = jdbc:postgresql://HOST/neondb?sslmode=require
   DB_USERNAME   = (usuario de Neon)
   DB_PASSWORD   = (password de Neon)
   JWT_SECRET    = (una cadena larga y aleatoria de 40+ caracteres)
   JWT_ISSUER    = mybusiness-silva
   CORS_ALLOWED_ORIGINS = https://app.tudominio.com
   SERVER_PORT   = 8080
   ```
   Para `JWT_SECRET` usa algo largo y único (puedes generar uno en https://generate-secret.now.sh/32).
5. En **Settings → Networking → Generate Domain**: Railway te da una URL temporal
   (ej. `pos-backend-production.up.railway.app`). Verifica que arranca abriendo
   `https://esa-url/actuator/health` → debe responder `{"status":"UP"}`.
6. (Después del paso 6) Agrega el **dominio personalizado** `api.tudominio.com` en Networking.

---

## PASO 4 — Frontend (Vercel)

1. Entra a https://vercel.com → **Add New → Project** → importa tu repo de GitHub.
2. En **Root Directory** pon `frontend-web`.
3. Framework: **Vite**. Build command: `npm run build`. Output: `dist`.
4. En **Environment Variables** agrega:
   ```
   VITE_API_URL = https://api.tudominio.com
   ```
   (Si aún no conectas el dominio del backend, pon temporalmente la URL de Railway del paso 3.5.)
5. **Deploy**. Vercel te da una URL (ej. `tu-proyecto.vercel.app`). Ábrela: debe cargar el login.
6. (Después del paso 6) Agrega el dominio `app.tudominio.com` en **Settings → Domains**.

---

## PASO 5 — Conectar el frontend con el backend (CORS)

Ya quedó configurado por variables:
- En **Railway** (backend): `CORS_ALLOWED_ORIGINS = https://app.tudominio.com`
- En **Vercel** (frontend): `VITE_API_URL = https://api.tudominio.com`

Si cambias dominios, actualiza estas dos variables y vuelve a desplegar.

---

## PASO 6 — Conectar el dominio (DNS en Cloudflare)

En Cloudflare → tu dominio → **DNS → Records**, agrega:

1. **Frontend (Vercel):** sigue las instrucciones que Vercel te da en Settings → Domains
   (normalmente un registro `CNAME` de `app` → `cname.vercel-dns.com`).
2. **Backend (Railway):** en Railway → Networking → Custom Domain pon `api.tudominio.com`;
   Railway te da un destino `CNAME`; créalo en Cloudflare (`api` → el destino de Railway).

HTTPS: tanto Vercel como Railway emiten el certificado SSL automáticamente. No haces nada.

> Tip: en Cloudflare pon los registros como "DNS only" (nube gris) la primera vez para que
> Vercel/Railway puedan validar el certificado; luego puedes activar el proxy (nube naranja).

---

## PASO 7 — Primer arranque y prueba

1. Abre `https://app.tudominio.com`.
2. Inicia sesión como **Super Admin** (las credenciales semilla del sistema; revisa el seeder
   de datos o créalas según tu configuración actual).
3. Da de alta un **negocio** desde el panel de Super Admin → se crea su tenant con el catálogo.
4. Entra como dueño del negocio, crea un cajero, y prueba el flujo de caja.
5. Verifica impresión de ticket (configura 58/80 mm en Configuración de ticket).

---

## Costos aproximados mensuales (arranque)
- Dominio: ~15 USD/año (una vez).
- Neon (BD): 0 USD (plan gratis) hasta cierto uso.
- Railway (backend): ~5 USD/mes.
- Vercel (frontend): 0 USD.
- **Total: ~5 USD/mes + dominio anual.**

---

## Checklist de variables de entorno (resumen)

**Backend (Railway):**
| Variable | Valor |
|---|---|
| SPRING_PROFILES_ACTIVE | prod |
| DB_URL | jdbc:postgresql://HOST/neondb?sslmode=require |
| DB_USERNAME | (Neon) |
| DB_PASSWORD | (Neon) |
| JWT_SECRET | cadena larga aleatoria |
| JWT_ISSUER | mybusiness-silva |
| CORS_ALLOWED_ORIGINS | https://app.tudominio.com |
| SERVER_PORT | 8080 |

**Frontend (Vercel):**
| Variable | Valor |
|---|---|
| VITE_API_URL | https://api.tudominio.com |

---

## Cuando contrates el PAC real (facturación) o el agregador de recargas
Agrega en el backend (Railway) estas variables, sin tocar código:
- `CFDI_PAC = real` (y las credenciales del PAC que definamos en su adaptador).
- `PAYMENTS_PROVIDER = real` (y las credenciales del agregador).

---

## Despliegue automático (CI/CD) — ya configurado

El repo ya está en GitHub: `https://github.com/shuitrongom/mybusiness-POS` (rama `master`).
Dejé configurado el despliegue continuo, así que **después del setup inicial, solo haces push y se
despliega solo**:

- `backend/railway.json` — Railway construye con el `Dockerfile` y vigila `/actuator/health`.
- `frontend-web/vercel.json` — Vercel construye con Vite y sirve la SPA.
- `server.port` usa la variable `PORT` que inyecta Railway automáticamente.

### Conexión inicial (una sola vez, mañana)
1. En **Railway**: New Project → Deploy from GitHub repo → elige `mybusiness-POS` →
   Settings → Root Directory = `backend`. Agrega las variables del checklist. Railway queda
   "escuchando" la rama `master`.
2. En **Vercel**: Add New Project → importa `mybusiness-POS` → Root Directory = `frontend-web`.
   Agrega `VITE_API_URL`. Vercel queda "escuchando" la rama `master`.

### Flujo de cada día (a partir de mañana)
```bash
git add .
git commit -m "describe tu cambio"
git push
```
Con ese `git push`:
- **Railway** reconstruye y redespliega el backend automáticamente (y corre las migraciones Flyway nuevas al arrancar).
- **Vercel** reconstruye y publica el frontend automáticamente.

No tocas servidores ni subes archivos a mano. Solo `git push`.

> Importante: las migraciones de base de datos (Vxx) se aplican SOLAS al arrancar el backend,
> incluyendo las de cada tenant (gracias a TenantMigrationRunner). Así que un cambio de esquema
> también viaja con un simple push.
