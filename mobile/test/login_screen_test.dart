import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:mybusiness_silva_owner/services/api_service.dart';
import 'package:mybusiness_silva_owner/screens/login_screen.dart';
import 'package:mybusiness_silva_owner/theme.dart';

/// Prueba de widget de la pantalla de login: verifica que se renderiza con los campos y el botón.
void main() {
  testWidgets('La pantalla de login muestra los campos y el botón de entrar',
      (WidgetTester tester) async {
    await tester.pumpWidget(
      Provider<ApiService>.value(
        value: ApiService(),
        child: MaterialApp(
          theme: AppTheme.dark(),
          home: const LoginScreen(),
        ),
      ),
    );

    // Título y marca.
    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(find.text('MyBusiness Silva'), findsOneWidget);

    // Botón de entrar.
    expect(find.text('Entrar'), findsOneWidget);

    // Campos de texto (correo, contraseña, MFA).
    expect(find.byType(TextField), findsNWidgets(3));
  });
}
