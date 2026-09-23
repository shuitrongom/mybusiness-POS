# MyBusiness Silva — Guía de instalación y despliegue

Guía para (1) levantar el proyecto en una máquina nueva y (2) subirlo a la nube.

Repositorio: https://github.com/shuitrongom/mybusiness-POS

---

## Arquitectura, en corto

- **Base de datos**: PostgreSQL 17 (en Docker para desarrollo). Multi-tenant: un schema por negocio.
- **Backend**: Java 25 + Spring Boot (Maven). Expone la API en el puerto **8080**.
- **Frontend web**: React + Vite + TypeScript (PWA). Dev en el puerto **5173**.
- **App móvil**: Flutter (opcional, en `mobile/`).

---

## 1) Requisitos en la máquina nueva

Instala esto antes de empezar:

| Herramienta | Versión | Para qué | Descarga |
|---|---|---|---|
| **Git** | reciente | Clonar el repo | https://git-scm.com/download/win |
| **JDK 25** | Microsoft Build of OpenJDK 25 | Compilar/correr el backend | https://learn.microsoft.com/java/openjdk/download |
| **Docker Desktop** | reciente | Correr PostgreSQL | https://www.docker.com/products/docker-desktop |
| **Node.js** | 20 LTS o superior | Frontend web | https://nodejs.org |
| **Flutter** (opcional) | 3.24.x | App móvil | https://docs.flutter.dev/get-started/install |

> Nota: para el backend usamos **Java 25**. El SDK de Android/Flutter usa Java 21 aparte (solo si compilas la app móvil).

---

## 2) Clonar el proyecto

```powershell
git clone https://github.com/shuitrongom/mybusiness-POS.git
cd mybusiness-POS
```

---

## 3) Crear el archivo `.env` del backend (NO viene en el repo)

El `.env` real no se sube por seguridad. Créalo a partir de la plantilla:

```powershell
Copy-Item backend\.env.example backend\.env
```

Contenido de referencia de `backend\.env` para desarrollo local (con la BD en Docker):

```
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://localhost:5544/mybusiness_silva
DB_USERNAME=pos_admin
DB_PASSWORD=pos_admin_dev
DB_POOL_MAX=10
DB_POOL_MIN=2
SERVER_PORT=8080
LOG_LEVEL_APP=DEBUG
```

> Importante: para desarrollo local usamos el usuario administrador de la BD (`pos_admin` / `pos_admin_dev`), que es el que crea el contenedor de Docker (ver `infra/docker-compose.yml`). El `pos_app` es el usuario de aplicación con menos privilegios para producción.

---

## 4) Levantar la base de datos (Docker)

```powershell
docker compose -f infra/docker-compose.yml up -d
```

Esto crea el contenedor `mbs-postgres` con:
- Base de datos: `mybusiness_silva`
- Usuario admin: `pos_admin` / `pos_admin_dev`
- Puerto en el host: **5544** (se mapea al 5432 interno para no chocar con otros PostgreSQL)

Verifica que esté sano:

```powershell
docker ps --filter "name=mbs-postgres"
```

Debe decir `(healthy)`.

---

## 5) Levantar el backend

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"   # ajusta a tu ruta real de JDK 25
$env:DB_URL = "jdbc:postgresql://localhost:5544/mybusiness_silva"
$env:DB_USERNAME = "pos_admin"
$env:DB_PASSWORD = "pos_admin_dev"

# Compilar (la primera vez descarga dependencias)
.\mvnw.cmd -B -DskipTests package

# Correr el JAR (usa el java de JDK 25)
& "$env:JAVA_HOME\bin\java.exe" -jar target\pos-backend-0.0.1-SNAPSHOT.jar
```

Cuando arranca, Flyway aplica las migraciones automáticamente. Verifica salud:

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing
# Debe responder {"status":"UP"}
```

El Super Admin inicial (perfil dev) es:
- Correo: `admin@mybusinesssilva.com`
- Contraseña: `Admin1234!Cambiar`

---

## 6) Levantar el frontend web

En otra terminal:

```powershell
cd frontend-web
npm install
npm run dev
```

Abre **http://localhost:5173**. El frontend habla con el backend en 8080 (proxy configurado en `vite.config.ts`).

---

## 7) Comandos útiles del día a día

```powershell
# Detener la base de datos
docker compose -f infra/docker-compose.yml down

# Detener y BORRAR los datos de la BD (empezar de cero)
docker compose -f infra/docker-compose.yml down -v

# Correr las pruebas del backend
cd backend; .\mvnw.cmd -B test

# Compilar el frontend para producción
cd frontend-web; npm run build

# Guardar avances en GitHub
git add -A
git commit -m "descripcion del cambio"
git push
```

---

## 8) Subir a la nube (producción)

El backend ya trae un `Dockerfile` (en `backend/`), así que se puede desplegar como contenedor. Piezas necesarias en producción:

1. **Base de datos PostgreSQL administrada** (para no operar la BD a mano):
   - AWS RDS for PostgreSQL, Google Cloud SQL, Azure Database, Neon, Supabase o Railway.
   - Crea la base `mybusiness_silva` y un usuario de aplicación.

2. **Backend** (contenedor Docker):
   - Opciones sencillas: **Railway**, **Render**, **Fly.io**, o un VPS con Docker.
   - Opciones robustas: AWS (ECS/App Runner), Google Cloud Run, Azure Container Apps.
   - Variables de entorno en producción (NO usar los valores de dev):
     ```
     SPRING_PROFILES_ACTIVE=prod
     DB_URL=jdbc:postgresql://<host-bd>:5432/mybusiness_silva
     DB_USERNAME=<usuario_app>
     DB_PASSWORD=<contraseña_fuerte>
     SERVER_PORT=8080
     ```
   - Genera además un secreto fuerte para el JWT (revisar `SecurityProperties`/config antes del primer despliegue).

3. **Frontend web** (sitio estático):
   - `npm run build` genera `frontend-web/dist`.
   - Publícalo en **Netlify**, **Vercel**, **Cloudflare Pages** o el mismo bucket/CDN.
   - Configura la URL del backend de producción (variable de entorno / proxy) en lugar de `localhost:8080`.

4. **Dominio y HTTPS**: apunta tu dominio al frontend y define la ruta/API al backend con certificado TLS.

> Sugerencia para empezar rápido y barato: **Neon** (PostgreSQL) + **Railway o Render** (backend en Docker) + **Netlify o Vercel** (frontend). Cuando crezca, migrar a AWS/GCP.

### Pendientes reales antes de producción (importante)
- **Notificaciones (WhatsApp/correo)**: hoy usan un adaptador **simulado** (solo registra en el log). Para enviar de verdad hay que conectar un proveedor (Twilio / WhatsApp Cloud API / SendGrid / SES) e implementar el adaptador real, activándolo por configuración.
- **CFDI (facturación)**: el timbrado usa un **PAC simulado**. Requiere contratar un PAC real y su adaptador.
- **Pago de servicios / recargas**: adaptador de agregador **simulado**; requiere contrato con el proveedor.
- **Fotos de producto**: hoy se guardan embebidas (base64) en la BD del negocio. Para catálogos grandes conviene almacenamiento de objetos (S3 / GCS) y guardar solo la URL.
- **App móvil (APK)**: compila y pasa análisis/tests, pero el APK no se ha generado (limitación de RAM en la máquina de desarrollo). Alternativa: compilar en CI (GitHub Actions / Codemagic).
- **Secreto del JWT y contraseñas**: usar valores fuertes y únicos en producción, nunca los de desarrollo.

---

## 9) Checklist de migración a la máquina nueva

- [ ] Instalar Git, JDK 25, Docker Desktop, Node.js.
- [ ] `git clone` del repo.
- [ ] Crear `backend/.env` desde `backend/.env.example`.
- [ ] `docker compose -f infra/docker-compose.yml up -d` y esperar `(healthy)`.
- [ ] Compilar y correr el backend; verificar `/actuator/health` = UP.
- [ ] `npm install` + `npm run dev` en `frontend-web`; abrir http://localhost:5173.
- [ ] Entrar como Super Admin y crear un negocio de prueba.

---

_Última actualización: generada automáticamente al configurar el respaldo en GitHub._
