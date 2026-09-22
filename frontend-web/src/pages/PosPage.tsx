import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { queueSale, flushQueue, pendingCount } from '@/lib/offlineQueue';
import './pos.css';

interface Product {
  id: number;
  name: string;
  price: number;
  categoryId: number | null;
  unit: string;
  soldByWeight: boolean;
}

interface Category {
  id: number;
  name: string;
}

interface CartLine {
  productId: number;
  description: string;
  quantity: number;
  unitPrice: number;
}

type PaymentMethod = 'CASH' | 'CARD' | 'TRANSFER';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/**
 * Punto de venta profesional: cuadrícula de productos por categoría con búsqueda y lector de
 * código de barras, carrito editable y cobro con método de pago y cálculo de cambio.
 * Funciona sin conexión: la venta se encola y se sincroniza al reconectar.
 */
export function PosPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [activeCategory, setActiveCategory] = useState<number | 'all'>('all');
  const [search, setSearch] = useState('');
  const [barcode, setBarcode] = useState('');
  const [cart, setCart] = useState<CartLine[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState(pendingCount());
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const barcodeRef = useRef<HTMLInputElement>(null);

  const total = cart.reduce((sum, l) => sum + l.quantity * l.unitPrice, 0);
  const itemCount = cart.reduce((sum, l) => sum + l.quantity, 0);

  // Carga inicial del catálogo y las categorías del negocio.
  useEffect(() => {
    (async () => {
      try {
        const [prods, cats] = await Promise.all([
          api.get<Product[]>('/catalog/products'),
          api.get<Category[]>('/catalog/products/categories'),
        ]);
        setProducts(prods.data.filter((p) => p.price >= 0));
        setCategories(cats.data);
      } catch {
        setMessage('No se pudo cargar el catálogo. Revisa tu conexión.');
      }
    })();
  }, []);

  const visibleProducts = useMemo(() => {
    const q = search.trim().toLowerCase();
    return products.filter((p) => {
      const matchCat = activeCategory === 'all' || p.categoryId === activeCategory;
      const matchText = q === '' || p.name.toLowerCase().includes(q);
      return matchCat && matchText;
    });
  }, [products, activeCategory, search]);

  const addByBarcode = async () => {
    const code = barcode.trim();
    if (!code) return;
    // Primero intenta en el catálogo ya cargado (rápido, offline).
    const local = products.find((p) => String(p.id) === code);
    if (local) {
      addProduct(local);
      setBarcode('');
      return;
    }
    try {
      const { data } = await api.get(`/catalog/products/barcode/${encodeURIComponent(code)}`);
      if (data.product) {
        addProduct(data.product as Product);
      } else {
        setMessage('Producto no encontrado. Regístralo en la sección Productos.');
      }
    } catch {
      setMessage('No se pudo buscar el producto (¿sin conexión?).');
    }
    setBarcode('');
  };

  const addProduct = (p: Product) => {
    addLine({ productId: p.id, description: p.name, quantity: 1, unitPrice: p.price });
    setMessage(null);
  };

  const addLine = (line: CartLine) => {
    setCart((prev) => {
      const existing = prev.find((l) => l.productId === line.productId);
      if (existing) {
        return prev.map((l) =>
          l.productId === line.productId ? { ...l, quantity: l.quantity + line.quantity } : l,
        );
      }
      return [...prev, line];
    });
  };

  const setQuantity = (productId: number, quantity: number) => {
    if (quantity <= 0) {
      removeLine(productId);
      return;
    }
    setCart((prev) => prev.map((l) => (l.productId === productId ? { ...l, quantity } : l)));
  };

  const removeLine = (productId: number) => {
    setCart((prev) => prev.filter((l) => l.productId !== productId));
  };

  const clearCart = () => setCart([]);

  const finishSale = async (method: PaymentMethod, received: number) => {
    if (cart.length === 0) return;
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
      // En tarjeta/transferencia el monto es el total; en efectivo, lo recibido (para el cambio).
      payments: [{ method, amount: method === 'CASH' ? Math.max(received, total) : total }],
      idempotencyKey,
    };

    try {
      await api.post('/sales', payload);
      const change = method === 'CASH' ? Math.max(received - total, 0) : 0;
      setMessage(
        change > 0
          ? `Venta registrada. Cambio a entregar: ${money(change)}.`
          : 'Venta registrada correctamente.',
      );
    } catch {
      queueSale(payload);
      setPending(pendingCount());
      setMessage('Sin conexión: la venta se guardó y se sincronizará al reconectar.');
    }
    setCart([]);
    setCheckoutOpen(false);
    barcodeRef.current?.focus();
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
      {/* Zona de catálogo */}
      <div className="pos-catalog">
        <div className="pos-toolbar">
          <input
            ref={barcodeRef}
            className="pos-barcode"
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addByBarcode()}
            placeholder="Escanea un código de barras y presiona Enter"
            autoFocus
          />
          <input
            className="pos-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar producto por nombre…"
          />
        </div>

        <div className="pos-cats">
          <button
            className={`pos-cat ${activeCategory === 'all' ? 'is-active' : ''}`}
            onClick={() => setActiveCategory('all')}
          >
            Todos
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              className={`pos-cat ${activeCategory === c.id ? 'is-active' : ''}`}
              onClick={() => setActiveCategory(c.id)}
            >
              {c.name}
            </button>
          ))}
        </div>

        {message && <div className="pos-message">{message}</div>}

        <div className="pos-grid">
          {visibleProducts.map((p) => (
            <button key={p.id} className="pos-tile" onClick={() => addProduct(p)} title={p.name}>
              <span className="pos-tile-name">{p.name}</span>
              <span className="pos-tile-price">{money(p.price)}</span>
              <span className="pos-tile-unit">{p.unit}</span>
            </button>
          ))}
          {visibleProducts.length === 0 && (
            <div className="pos-grid-empty">
              {products.length === 0
                ? 'Aún no hay productos. Regístralos en la sección Productos.'
                : 'Ningún producto coincide con la búsqueda.'}
            </div>
          )}
        </div>
      </div>

      {/* Ticket / carrito */}
      <aside className="pos-ticket">
        <div className="pos-ticket-head">
          <div>
            <div className="pos-ticket-title">Venta actual</div>
            <div className="pos-ticket-sub">{itemCount} artículo(s)</div>
          </div>
          {cart.length > 0 && (
            <button className="pos-clear" onClick={clearCart}>Vaciar</button>
          )}
        </div>

        <div className="pos-lines">
          {cart.map((l) => (
            <div key={l.productId} className="pos-line">
              <div className="pos-line-info">
                <div className="pos-line-name">{l.description}</div>
                <div className="pos-line-price">{money(l.unitPrice)} c/u</div>
              </div>
              <div className="pos-qty">
                <button onClick={() => setQuantity(l.productId, l.quantity - 1)} aria-label="Menos">−</button>
                <input
                  value={l.quantity}
                  onChange={(e) => setQuantity(l.productId, Number(e.target.value) || 0)}
                  inputMode="numeric"
                />
                <button onClick={() => setQuantity(l.productId, l.quantity + 1)} aria-label="Más">+</button>
              </div>
              <div className="pos-line-total">{money(l.quantity * l.unitPrice)}</div>
              <button className="pos-line-remove" onClick={() => removeLine(l.productId)} aria-label="Quitar">✕</button>
            </div>
          ))}
          {cart.length === 0 && (
            <div className="pos-empty">
              <div className="pos-empty-icon">🛒</div>
              <p>Agrega productos tocando la cuadrícula o escaneando su código.</p>
            </div>
          )}
        </div>

        <div className="pos-summary">
          <div className="pos-summary-row">
            <span>Total</span>
            <strong className="pos-summary-total">{money(total)}</strong>
          </div>
          <button
            className="pos-pay"
            disabled={cart.length === 0}
            onClick={() => setCheckoutOpen(true)}
          >
            Cobrar {money(total)}
          </button>
          <div className="pos-sync">
            <span className="badge badge-muted">Pendientes por sincronizar: {pending}</span>
            <button className="btn-ghost" onClick={sync} disabled={pending === 0}>Sincronizar</button>
          </div>
        </div>
      </aside>

      {checkoutOpen && (
        <CheckoutModal
          total={total}
          onCancel={() => setCheckoutOpen(false)}
          onConfirm={finishSale}
        />
      )}
    </div>
  );
}

/**
 * Modal de cobro: elige método de pago y, en efectivo, captura lo recibido para calcular el
 * cambio con botones de billetes rápidos. Confirma la venta.
 */
function CheckoutModal({ total, onCancel, onConfirm }:
  { total: number; onCancel: () => void; onConfirm: (m: PaymentMethod, received: number) => void }) {
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [received, setReceived] = useState<string>('');
  const receivedNum = Number(received) || 0;
  const change = Math.max(receivedNum - total, 0);
  const insufficient = method === 'CASH' && receivedNum > 0 && receivedNum < total;

  // Sugerencias de billetes comunes en México, más el importe exacto.
  const quickBills = [total, 50, 100, 200, 500, 1000].filter((v, i, a) => a.indexOf(v) === i);

  const canConfirm = method !== 'CASH' || receivedNum >= total;

  return (
    <div className="pos-modal-overlay" onClick={onCancel}>
      <div className="pos-modal" onClick={(e) => e.stopPropagation()}>
        <div className="pos-modal-head">
          <h3>Cobrar venta</h3>
          <button className="modal-close" onClick={onCancel} aria-label="Cerrar">×</button>
        </div>

        <div className="pos-modal-total">
          <span>Total a pagar</span>
          <strong>{money(total)}</strong>
        </div>

        <div className="pos-methods">
          {(['CASH', 'CARD', 'TRANSFER'] as PaymentMethod[]).map((m) => (
            <button
              key={m}
              className={`pos-method ${method === m ? 'is-active' : ''}`}
              onClick={() => setMethod(m)}
            >
              {m === 'CASH' ? '💵 Efectivo' : m === 'CARD' ? '💳 Tarjeta' : '🏦 Transferencia'}
            </button>
          ))}
        </div>

        {method === 'CASH' && (
          <>
            <label className="pos-received">
              <span>Efectivo recibido</span>
              <input
                type="number"
                min={0}
                value={received}
                onChange={(e) => setReceived(e.target.value)}
                placeholder="0.00"
                autoFocus
              />
            </label>
            <div className="pos-bills">
              {quickBills.map((b, i) => (
                <button key={`${b}-${i}`} onClick={() => setReceived(String(b))}>
                  {b === total ? 'Exacto' : money(b)}
                </button>
              ))}
            </div>
            <div className={`pos-change ${insufficient ? 'is-short' : ''}`}>
              <span>{insufficient ? 'Falta' : 'Cambio'}</span>
              <strong>{money(insufficient ? total - receivedNum : change)}</strong>
            </div>
          </>
        )}

        <div className="pos-modal-actions">
          <button className="btn-ghost" onClick={onCancel}>Cancelar</button>
          <button
            className="pos-confirm"
            disabled={!canConfirm}
            onClick={() => onConfirm(method, receivedNum)}
          >
            Confirmar cobro
          </button>
        </div>
      </div>
    </div>
  );
}
