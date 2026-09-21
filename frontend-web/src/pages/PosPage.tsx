import { useState } from 'react';
import { api } from '@/lib/api';
import { queueSale, flushQueue, pendingCount } from '@/lib/offlineQueue';
import './pos.css';

interface CartLine {
  productId: number;
  description: string;
  quantity: number;
  unitPrice: number;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/**
 * Pantalla del punto de venta: escaneo/búsqueda por código de barras, carrito, y cobro.
 * Si no hay conexión, la venta se encola localmente y se sincroniza al reconectar (offline).
 */
export function PosPage() {
  const [barcode, setBarcode] = useState('');
  const [cart, setCart] = useState<CartLine[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState(pendingCount());

  const total = cart.reduce((sum, l) => sum + l.quantity * l.unitPrice, 0);

  const addByBarcode = async () => {
    if (!barcode.trim()) return;
    try {
      const { data } = await api.get(`/catalog/products/barcode/${encodeURIComponent(barcode.trim())}`);
      if (data.product) {
        const p = data.product;
        addLine({ productId: p.id, description: p.name, quantity: 1, unitPrice: p.price });
      } else {
        setMessage('Producto no encontrado. Regístralo en la sección Productos.');
      }
    } catch {
      setMessage('No se pudo buscar el producto (¿sin conexión?).');
    }
    setBarcode('');
  };

  const addLine = (line: CartLine) => {
    setCart((prev) => {
      const existing = prev.find((l) => l.productId === line.productId);
      if (existing) {
        return prev.map((l) =>
          l.productId === line.productId ? { ...l, quantity: l.quantity + 1 } : l,
        );
      }
      return [...prev, line];
    });
    setMessage(null);
  };

  const removeLine = (productId: number) => {
    setCart((prev) => prev.filter((l) => l.productId !== productId));
  };

  const checkout = async () => {
    if (cart.length === 0) return;
    // Clave de idempotencia: identifica la venta de forma única para evitar duplicados offline.
    const idempotencyKey = `pos-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const payload = {
      branchId: 1,
      lines: cart.map((l) => ({
        productId: l.productId,
        description: l.description,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discount: 0,
      })),
      payments: [{ method: 'CASH', amount: total }],
      idempotencyKey,
    };

    try {
      await api.post('/sales', payload);
      setMessage('Venta registrada correctamente.');
      setCart([]);
    } catch {
      // Sin conexión o error: se encola para sincronizar después.
      queueSale(payload);
      setPending(pendingCount());
      setMessage('Sin conexión: la venta se guardó y se sincronizará al reconectar.');
      setCart([]);
    }
  };

  const sync = async () => {
    const synced = await flushQueue(async (sale) => {
      await api.post('/sales', sale);
    });
    setPending(pendingCount());
    setMessage(synced > 0 ? `${synced} venta(s) sincronizada(s).` : 'No hay ventas pendientes.');
  };

  return (
    <div className="pos">
      <div className="pos-main">
        <h1 className="page-title">Punto de venta</h1>

        <div className="pos-scan card">
          <input
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addByBarcode()}
            placeholder="Escanea o escribe el código de barras y presiona Enter"
            autoFocus
          />
          <button className="btn-primary" onClick={addByBarcode}>Agregar</button>
        </div>

        {message && <div className="pos-message">{message}</div>}

        <div className="card" style={{ marginTop: 'var(--space-4)' }}>
          <table className="table">
            <thead>
              <tr><th>Producto</th><th>Cant.</th><th>Precio</th><th>Importe</th><th></th></tr>
            </thead>
            <tbody>
              {cart.map((l) => (
                <tr key={l.productId}>
                  <td>{l.description}</td>
                  <td>{l.quantity}</td>
                  <td>{money(l.unitPrice)}</td>
                  <td>{money(l.quantity * l.unitPrice)}</td>
                  <td>
                    <button className="btn-ghost pos-remove" onClick={() => removeLine(l.productId)}>✕</button>
                  </td>
                </tr>
              ))}
              {cart.length === 0 && (
                <tr><td colSpan={5} className="empty">Carrito vacío. Escanea un producto.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <aside className="pos-side card">
        <div className="pos-total-label">Total a pagar</div>
        <div className="pos-total">{money(total)}</div>
        <button className="btn-accent pos-checkout" disabled={cart.length === 0} onClick={checkout}>
          Cobrar
        </button>

        <div className="pos-sync">
          <span className="badge badge-muted">Pendientes offline: {pending}</span>
          <button className="btn-ghost" onClick={sync} disabled={pending === 0}>Sincronizar</button>
        </div>
      </aside>
    </div>
  );
}
