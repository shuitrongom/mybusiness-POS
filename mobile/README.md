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

## Siguientes pasos (cuando se compile)

- Notificaciones push (FCM) para cortes de caja y alertas de stock.
- Gráficas con fl_chart (ya incluida en dependencias).
- Detalle por sucursal.
