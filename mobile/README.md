# MyBusiness Silva — App del dueño (Flutter)

App móvil (iOS/Android) para que el dueño del negocio monitoree sus ventas y métricas en tiempo
real. Consume el mismo backend REST de MyBusiness Silva.

## Estado

Código de la app listo (login + dashboard de BI con métricas del día y top productos, tema
enterprise oscuro). **Falta compilar/ejecutar**, lo cual requiere instalar el toolchain de Flutter
y el SDK de Android (no disponibles en el entorno donde se generó el código).

## Requisitos para compilar

1. **Flutter SDK** (3.5+): https://docs.flutter.dev/get-started/install/windows
2. **Android SDK** (para APK) y/o Xcode (para iOS).
3. Verificar el entorno con `flutter doctor`.

## Puesta en marcha

```bash
cd mobile
flutter pub get          # descarga dependencias
flutter run              # ejecuta en un emulador/dispositivo conectado
# o para generar el APK de release:
flutter build apk --release
```

## Configuración del backend

En `lib/services/api_service.dart`, `baseUrl` apunta a `http://10.0.2.2:8080/api/v1`
(que es el localhost del host visto desde el emulador Android). Para producción, cámbialo a la URL
del servidor real (https).

## Estructura

```
lib/
├── main.dart                 # arranque; decide login o dashboard según sesión
├── theme.dart                # tema enterprise (Material 3, oscuro)
├── services/api_service.dart # cliente HTTP + manejo de token
└── screens/
    ├── login_screen.dart     # inicio de sesión con la marca
    └── dashboard_screen.dart # métricas del día + top productos (BI)
```

## Notificaciones push (FCM)

La app incluye `NotificationsService` (Firebase Cloud Messaging + notificaciones locales) para
recibir avisos de cortes de caja, alertas de stock y ventas. Para activarlas:

1. Crear un proyecto en Firebase y registrar la app Android/iOS.
2. Colocar `google-services.json` (Android) y `GoogleService-Info.plist` (iOS) en el proyecto.
3. En `main.dart`, inicializar Firebase y llamar `NotificationsService().init()`.
4. Registrar el token del dispositivo (`deviceToken()`) en el backend para segmentar los envíos.

## Siguientes pasos (cuando se compile)

- Conectar `NotificationsService` en el arranque y registrar el token en el backend.
- Gráficas con fl_chart (ya incluida en dependencias).
- Detalle por sucursal.
