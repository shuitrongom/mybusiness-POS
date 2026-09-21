import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Servicio de notificaciones push del dueño. Integra Firebase Cloud Messaging (FCM) para
/// recibir avisos del servidor (cortes de caja, alertas de stock, ventas importantes) y las
/// muestra como notificaciones locales cuando la app está en primer plano.
///
/// Requiere configurar Firebase en el proyecto (google-services.json en Android / GoogleService
/// -Info.plist en iOS) y registrar el token del dispositivo en el backend para segmentar envíos.
class NotificationsService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _local = FlutterLocalNotificationsPlugin();

  static const AndroidNotificationChannel _channel = AndroidNotificationChannel(
    'mbs_owner_channel',
    'Avisos del negocio',
    description: 'Cortes de caja, alertas de inventario y ventas',
    importance: Importance.high,
  );

  /// Inicializa el canal local, solicita permisos de notificación y engancha el listener de FCM.
  Future<void> init() async {
    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosInit = DarwinInitializationSettings();
    await _local.initialize(
      const InitializationSettings(android: androidInit, iOS: iosInit),
    );
    await _local
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.createNotificationChannel(_channel);

    await _messaging.requestPermission();

    // Muestra las notificaciones recibidas mientras la app está en primer plano.
    FirebaseMessaging.onMessage.listen(_showForeground);
  }

  /// Devuelve el token FCM del dispositivo, para registrarlo en el backend.
  Future<String?> deviceToken() => _messaging.getToken();

  void _showForeground(RemoteMessage message) {
    final notification = message.notification;
    if (notification == null) return;
    _local.show(
      notification.hashCode,
      notification.title,
      notification.body,
      NotificationDetails(
        android: AndroidNotificationDetails(
          _channel.id,
          _channel.name,
          channelDescription: _channel.description,
          importance: Importance.high,
        ),
        iOS: const DarwinNotificationDetails(),
      ),
    );
  }
}
