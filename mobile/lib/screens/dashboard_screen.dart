import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../services/api_service.dart';
import '../theme.dart';
import 'login_screen.dart';

/// Panel principal de la app del dueño: métricas del día y productos más vendidos.
/// Consume los endpoints de Business Intelligence del backend.
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final _money = NumberFormat.currency(locale: 'es_MX', symbol: '\$');
  Map<String, dynamic>? _summary;
  List<dynamic> _top = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final api = context.read<ApiService>();
    try {
      final summary = await api.getJson('/bi/dashboard/today') as Map<String, dynamic>;
      final top = await api.getJson('/bi/products/top?days=30&limit=5') as List<dynamic>;
      if (!mounted) return;
      setState(() {
        _summary = summary;
        _top = top;
        _loading = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = 'No se pudieron cargar los datos.';
          _loading = false;
        });
      }
    }
  }

  Future<void> _logout() async {
    await context.read<ApiService>().logout();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
    );
  }

  double _num(dynamic v) => v == null ? 0 : (v as num).toDouble();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('MyBusiness Silva'),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
          IconButton(onPressed: _logout, icon: const Icon(Icons.logout)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      Text('Resumen de hoy',
                          style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 12),
                      _metricsGrid(),
                      const SizedBox(height: 24),
                      Text('Productos más vendidos (30 días)',
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      ..._top.map(_topTile),
                      if (_top.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(16),
                          child: Text('Aún no hay ventas registradas.',
                              style: TextStyle(color: Colors.white54)),
                        ),
                    ],
                  ),
                ),
    );
  }

  Widget _metricsGrid() {
    final s = _summary ?? {};
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      childAspectRatio: 1.7,
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      children: [
        _metricCard('Ventas del día', '${s['salesCount'] ?? 0}', false),
        _metricCard('Total vendido', _money.format(_num(s['salesTotal'])), true),
        _metricCard('Ticket promedio', _money.format(_num(s['averageTicket'])), false),
        _metricCard('Unidades', '${s['itemsSold'] ?? 0}', false),
      ],
    );
  }

  Widget _metricCard(String label, String value, bool accent) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: accent
            ? const LinearGradient(colors: [AppTheme.brand, AppTheme.brandBright])
            : null,
        color: accent ? null : AppTheme.surface,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(label, style: const TextStyle(color: Colors.white70, fontSize: 13)),
          const SizedBox(height: 6),
          Text(value,
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 22, color: Colors.white)),
        ],
      ),
    );
  }

  Widget _topTile(dynamic p) {
    final m = p as Map<String, dynamic>;
    return Card(
      child: ListTile(
        title: Text(m['name']?.toString() ?? ''),
        subtitle: Text('Cantidad: ${m['quantity'] ?? 0}'),
        trailing: Text(_money.format(_num(m['revenue'])),
            style: const TextStyle(fontWeight: FontWeight.w700)),
      ),
    );
  }
}
