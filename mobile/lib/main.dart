import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/api_service.dart';
import 'theme.dart';
import 'screens/login_screen.dart';
import 'screens/dashboard_screen.dart';

/// Punto de entrada de la app del dueño de MyBusiness Silva.
/// Carga el token guardado y decide la pantalla inicial (login o panel).
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final api = ApiService();
  await api.loadToken();
  runApp(MyBusinessOwnerApp(api: api));
}

class MyBusinessOwnerApp extends StatelessWidget {
  final ApiService api;
  const MyBusinessOwnerApp({super.key, required this.api});

  @override
  Widget build(BuildContext context) {
    return Provider<ApiService>.value(
      value: api,
      child: MaterialApp(
        title: 'MyBusiness Silva',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        home: api.isAuthenticated ? const DashboardScreen() : const LoginScreen(),
      ),
    );
  }
}
