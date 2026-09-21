# Guía de despliegue — MyBusiness Silva

Cómo poner el sistema en producción, empezando con infraestructura de bajo costo y escalando
después sin reprogramar (gracias a la arquitectura hexagonal).

## Componentes a desplegar

| Componente | Qué es | Opción económica de arranque |
|-----------|--------|------------------------------|
| Base de datos | PostgreSQL 17 | Neon / Supabase (plan gratuito) |
| Backend | API Spring Boot (Docker) | Fly.io / Render / Railway |
| Frontend web | PWA estática (React) | Vercel / Netlify |
| App móvil | Flutter (APK/tiendas) | Google Play / App Store |

## 1. Base de datos

1. Crear una base PostgreSQL 17 en el proveedor elegido.
2. Crear el rol de aplicación `pos_app` (sin superusuario, para que aplique RLS):
   ```sql
   CREATE ROLE pos_app WITH LOGIN PASSWORD '<clave-fuerte>';
   GRANT CONNECT, CREATE ON DATABASE <db> TO pos_app;
   ```
3. Guardar la URL JDBC, usuario y contraseña como secretos.

## 2. Backend

Variables de entorno requeridas (secretos):

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host>:5432/<db>
DB_USERNAME=pos_app
DB_PASSWORD=<clave-fuerte>
JWT_SECRET=<cadena aleatoria de 32+ bytes>
SUPERADMIN_EMAIL=<tu-correo>
SUPERADMIN_PASSWORD=<clave inicial; cambiar tras el primer acceso>
CFDI_PAC=simulated        # cambiar a 'real' al contratar un PAC
PAYMENTS_PROVIDER=simulated
```

Construir y desplegar (ejemplo genérico con Docker):

```bash
cd backend
docker build -t mybusiness-silva-api .
# subir la imagen al proveedor y ejecutar con las variables de entorno anteriores
```

Al arrancar, Flyway aplica automáticamente las migraciones del schema `admin` y crea el usuario
Super Admin inicial. Los schemas de cada negocio se crean al dar de alta cada negocio.

## 3. Frontend web

```bash
cd frontend-web
npm ci
npm run build     # genera dist/ (PWA)
# desplegar dist/ en Vercel/Netlify
```

Configurar el proxy/redirect de `/api` hacia la URL pública del backend (o usar variable de
entorno para la base de la API).

## 4. App móvil

Ver `mobile/README.md`. Requiere Flutter SDK + Android SDK/Xcode. Ajustar `baseUrl` en
`lib/services/api_service.dart` a la URL pública del backend (https).

## Seguridad en producción (lista de verificación)

- [ ] Cambiar la contraseña del Super Admin tras el primer acceso.
- [ ] `JWT_SECRET` fuerte y único, en gestor de secretos (no en el repo).
- [ ] TLS/HTTPS en backend y frontend.
- [ ] Restringir CORS a los dominios reales (ver `SecurityConfig`).
- [ ] Respaldos automáticos de la base de datos y prueba de restauración.
- [ ] Activar MFA para los usuarios administradores.

## Escalado (sin reprogramar)

- Base de datos: migrar a instancia dedicada mayor; un cliente grande puede pasar a base dedicada
  cambiando solo su configuración de conexión (arquitectura de tenancy preparada para ello).
- Backend: aumentar réplicas/recursos del contenedor.
- Integraciones reales: cambiar `CFDI_PAC=real` y `PAYMENTS_PROVIDER=real` e implementar/activar
  los adaptadores del PAC y del agregador (los puertos ya existen).
