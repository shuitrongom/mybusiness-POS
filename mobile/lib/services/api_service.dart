import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Cliente HTTP de la app del dueño. Se conecta al mismo backend REST de MyBusiness Silva.
/// Guarda el token de acceso de forma persistente y lo adjunta en cada petición.
class ApiService {
  /// URL base del backend. En producción se configura al del servidor real.
  /// Para pruebas con emulador Android, 10.0.2.2 apunta al localhost del host.
  static const String baseUrl = 'http://10.0.2.2:8080/api/v1';

  static const String _tokenKey = 'mbs.owner.token';
  String? _token;

  Future<void> loadToken() async {
    final prefs = await SharedPreferences.getInstance();
    _token = prefs.getString(_tokenKey);
  }

  bool get isAuthenticated => _token != null;

  Future<void> _saveToken(String? token) async {
    final prefs = await SharedPreferences.getInstance();
    _token = token;
    if (token == null) {
      await prefs.remove(_tokenKey);
    } else {
      await prefs.setString(_tokenKey, token);
    }
  }

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  /// Inicia sesión del Super Admin/dueño y guarda el token. Devuelve true si tuvo éxito.
  Future<bool> login(String email, String password, {String? mfaCode}) async {
    final res = await http.post(
      Uri.parse('$baseUrl/auth/superadmin/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': email,
        'password': password,
        if (mfaCode != null && mfaCode.isNotEmpty) 'mfaCode': mfaCode,
      }),
    );
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final token = data['accessToken'] as String?;
      if (token != null) {
        await _saveToken(token);
        return true;
      }
    }
    return false;
  }

  Future<void> logout() => _saveToken(null);

  /// GET genérico que devuelve JSON decodificado.
  Future<dynamic> getJson(String path) async {
    final res = await http.get(Uri.parse('$baseUrl$path'), headers: _headers);
    if (res.statusCode == 200) {
      return jsonDecode(res.body);
    }
    throw Exception('Error ${res.statusCode} al consultar $path');
  }
}
