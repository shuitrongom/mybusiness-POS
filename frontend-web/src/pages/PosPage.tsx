import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Store, ScanBarcode, Search, ShoppingCart, Minus, Plus, X,
  Banknote, CreditCard, Landmark, RefreshCw, Ticket as TicketIcon,
  User, Users, UserPlus, Tag, Percent, Trash2, ChevronDown,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { queueSale, flushQueue, pendingCount } from '@/lib/offlineQueue';
import { staggerContainer, staggerItem, springSoft } from '@/lib/motion';
import { Ticket, defaultTicketSettings, type TicketSettings, type TicketData } from '@/components/Ticket';
import { applyPromotions, type Promotion } from '@/lib/promotions';
import { PosShift, cachedShift, type ActiveShift } from '@/components/PosShift';
import { printTicketHtml, setPaperWidth } from '@/lib/thermalPrint';
import { saleTicketHtml } from '@/lib/ticketHtml';
import { useSession } from '@/store/session';
import { decodeToken, canSell } from '@/lib/jwt';
import { ShieldAlert } from 'lucide-react';
import './pos.css';

/** Precios profesionales del producto (coinciden con ProductExtras del backend). */
interface ProductExtras {
  price2: number;
  price3: number;
  price4: number;
  price5: number;
  taxRate: number;
  onSale: boolean;
}

interface Product {
  id: number;
  name: string;
  price: number;
  cost: number;
  categoryId: number | null;
  unit: string;
  soldByWeight: boolean;
  imageUrl: string | null;
  extras: ProductExtras;
}

interface Category {
  id: number;
  name: string;
}

interface Branch {
  id: number;
  name: string;
  code: string | null;
  active: boolean;
}

interface PriceList {
  id: number;
  name: string;
}

interface Customer {
  id: number;
  name: string;
  rfc: string | null;
  phone: string | null;
  creditAvailable: number;
}

interface CartLine {
  productId: number;
  categoryId: number | null;
  description: string;
  quantity: number;
  unitPrice: number;
  discount: number;
}

type PaymentMethod = 'CASH' | 'CARD' | 'TRANSFER' | 'VOUCHER';

interface PaymentSplit {
  method: PaymentMethod;
  amount: number;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

// Paleta para el avatar de cada producto/categoría (color estable derivado del nombre).
const TILE_COLORS = [
  '#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff',
  '#0ea5e9', '#16a34a', '#d97706', '#db2777', '#0891b2',
];
function colorFor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return TILE_COLORS[hash % TILE_COLORS.length];
}
function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

/** Devuelve el precio del producto para el nivel de lista elegido (1..5), con respaldo al precio 1. */
function priceForList(p: Product, listId: number): number {
  const x = p.extras;
  const candidate =
    listId === 2 ? x?.price2 :
    listId === 3 ? x?.price3 :
    listId === 4 ? x?.price4 :
    listId === 5 ? x?.price5 : p.price;
  return candidate && candidate > 0 ? candidate : p.price;
}

const BRANCH_KEY = 'mbs.branchId';
const PRICELIST_KEY = 'mbs.priceListId';

/**
 * Punto de venta profesional (enterprise): cabecera con sucursal, cliente, vendedor y lista de
 * precio; cuadrícula de productos con multiprecio; carrito con descuento por renglón y descuento
 * global; cobro con pagos mixtos y venta a crédito. Funciona sin conexión (encola y sincroniza).
 */
export function PosPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [priceLists, setPriceLists] = useState<PriceList[]>([]);
  const [branchId, setBranchId] = useState<number>(() => Number(localStorage.getItem(BRANCH_KEY)) || 0);
  const [priceListId, setPriceListId] = useState<number>(() => Number(localStorage.getItem(PRICELIST_KEY)) || 1);
  const [activeCategory, setActiveCategory] = useState<number | 'all'>('all');
  const [search, setSearch] = useState('');
  const [barcode, setBarcode] = useState('');
  const [cart, setCart] = useState<CartLine[]>([]);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [customerOpen, setCustomerOpen] = useState(false);
  const [globalDiscount, setGlobalDiscount] = useState(0);
  const [discountOpen, setDiscountOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState(pendingCount());
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [ticket, setTicket] = useState<TicketData | null>(null);
  const [ticketSettings, setTicketSettings] = useState<TicketSettings>(defaultTicketSettings);
  const [promos, setPromos] = useState<Promotion[]>([]);
  const [now, setNow] = useState(new Date());
  const [shift, setShift] = useState<ActiveShift | null>(cachedShift());
  const [lastProductId, setLastProductId] = useState<number | null>(null);
  const [qtyInput, setQtyInput] = useState('1');
  const barcodeRef = useRef<HTMLInputElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const token = useSession((s) => s.token);
  const sellAllowed = canSell(decodeToken(token));

  // Descuentos automáticos por promociones vigentes, recalculados con el carrito.
  const promoResult = useMemo(
    () => applyPromotions(promos, cart.map((l) => ({
      productId: l.productId, categoryId: l.categoryId, quantity: l.quantity, unitPrice: l.unitPrice,
    }))),
    [promos, cart],
  );

  const linesSubtotal = cart.reduce(
    (sum, l) => sum + l.quantity * l.unitPrice - (promoResult.discountByProduct[l.productId] || 0), 0);
  const total = Math.max(linesSubtotal - globalDiscount, 0);
  const itemCount = cart.reduce((sum, l) => sum + l.quantity, 0);
  const activeBranch = branches.find((b) => b.id === branchId);
  const activeList = priceLists.find((l) => l.id === priceListId);

  // Reloj de la cabecera.
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000 * 30);
    return () => clearInterval(t);
  }, []);

  // Carga inicial: catálogo, categorías, sucursales y listas de precio.
  useEffect(() => {
    (async () => {
      try {
        const [prods, cats, brs] = await Promise.all([
          api.get<Product[]>('/catalog/products'),
          api.get<Category[]>('/catalog/products/categories'),
          api.get<Branch[]>('/branches'),
        ]);
        setProducts(prods.data);
        setCategories(cats.data);
        api.get<TicketSettings>('/tickets/settings')
          .then((r) => { setTicketSettings(r.data); setPaperWidth(r.data.paperWidthMm); })
          .catch(() => {});
        api.get<PriceList[]>('/sales/price-lists')
          .then((r) => setPriceLists(r.data.length ? r.data : [{ id: 1, name: 'Público' }]))
          .catch(() => setPriceLists([{ id: 1, name: 'Público' }]));
        api.get<Promotion[]>('/promotions/active')
          .then((r) => setPromos(r.data))
          .catch(() => setPromos([]));
        const active = brs.data.filter((b) => b.active);
        setBranches(active);
        setBranchId((prev) => (active.some((b) => b.id === prev) ? prev : active[0]?.id ?? 0));
      } catch {
        setMessage('No se pudo cargar el catálogo. Revisa tu conexión.');
      }
    })();
  }, []);

  useEffect(() => {
    if (branchId) localStorage.setItem(BRANCH_KEY, String(branchId));
  }, [branchId]);

  useEffect(() => {
    localStorage.setItem(PRICELIST_KEY, String(priceListId));
  }, [priceListId]);

  // Al cambiar de lista de precio, recalcula el precio de los renglones que no fueron editados
  // manualmente (se toma el precio del producto para la nueva lista).
  useEffect(() => {
    setCart((prev) => prev.map((l) => {
      const prod = products.find((p) => p.id === l.productId);
      if (!prod) return l;
      return { ...l, unitPrice: priceForList(prod, priceListId) };
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [priceListId]);

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
    // La cantidad se toma del campo visible "Cantidad" (por defecto 1). Sin sintaxis rara.
    const qty = Math.max(Number(qtyInput) || 1, 0.001);
    const local = products.find((p) => String(p.id) === code);
    if (local) {
      addProduct(local, qty);
      setBarcode('');
      setQtyInput('1');
      return;
    }
    try {
      const { data } = await api.get(`/catalog/products/barcode/${encodeURIComponent(code)}`);
      if (data.product) {
        addProduct(data.product as Product, qty);
      } else {
        setMessage('Producto no encontrado. Regístralo en la sección Productos.');
      }
    } catch {
      setMessage('No se pudo buscar el producto (¿sin conexión?).');
    }
    setBarcode('');
    setQtyInput('1');
  };

  const addProduct = (p: Product, qty = 1) => {
    addLine({
      productId: p.id, categoryId: p.categoryId, description: p.name, quantity: qty,
      unitPrice: priceForList(p, priceListId), discount: 0,
    });
    setLastProductId(p.id);
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

  const setUnitPrice = (productId: number, unitPrice: number) => {
    setCart((prev) => prev.map((l) => (l.productId === productId ? { ...l, unitPrice: Math.max(unitPrice, 0) } : l)));
  };

  const removeLine = (productId: number) => {
    setCart((prev) => prev.filter((l) => l.productId !== productId));
    setLastProductId((cur) => (cur === productId ? null : cur));
  };

  const clearCart = () => {
    setCart([]);
    setGlobalDiscount(0);
  };

  const openCheckout = useCallback(() => {
    if (!shift) {
      toast.info('Abre tu caja', 'Debes abrir tu caja con el fondo inicial antes de cobrar.');
      return;
    }
    if (cart.length > 0) setCheckoutOpen(true);
  }, [cart.length, shift]);

  // Cuando se muestra el ticket tras cobrar: Enter/P imprime, Esc cierra e inicia nueva venta.
  useEffect(() => {
    if (!ticket) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === 'p' || e.key === 'P') { e.preventDefault(); if (ticket) printTicketHtml(saleTicketHtml(ticketSettings, ticket)); }
      else if (e.key === 'Escape') { e.preventDefault(); setTicket(null); barcodeRef.current?.focus(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [ticket, ticketSettings]);

  // Ajusta la cantidad del último renglón agregado (para +/- rápidos con teclado).
  const bumpLast = useCallback((delta: number) => {
    setCart((prev) => {
      const id = lastProductId ?? prev[prev.length - 1]?.productId;
      if (id == null) return prev;
      return prev
        .map((l) => (l.productId === id ? { ...l, quantity: l.quantity + delta } : l))
        .filter((l) => l.quantity > 0);
    });
  }, [lastProductId]);

  // Atajos de teclado del cajero (flujo rápido sin mouse):
  //   F2 enfocar código · F4 cliente · F9 o Enter cobrar · +/- cantidad del último ·
  //   Supr quitar último · Esc enfocar código. El modal de cobro y el de ticket tienen los suyos.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      const typingInField = !!el && (el.tagName === 'INPUT' || el.tagName === 'SELECT' || el.tagName === 'TEXTAREA');
      const inBarcode = el === barcodeRef.current;
      // No interferir si hay un modal abierto (cada modal maneja su propio teclado).
      const modalOpen = checkoutOpen || customerOpen || discountOpen || ticket != null;

      if (e.key === 'F2') { e.preventDefault(); barcodeRef.current?.focus(); return; }
      if (e.key === 'F3') { e.preventDefault(); searchRef.current?.focus(); return; }
      if (e.key === 'F4') { e.preventDefault(); if (!modalOpen) setCustomerOpen(true); return; }
      if (e.key === 'F9') { e.preventDefault(); if (!modalOpen) openCheckout(); return; }
      // Enter con carrito lleno (y no escribiendo en un campo que no sea el código) → cobrar.
      if (e.key === 'Enter' && !modalOpen && !typingInField && cart.length > 0) {
        e.preventDefault(); openCheckout(); return;
      }
      if (modalOpen) return;
      // +/- ajustan el último renglón (salvo si estás escribiendo en un campo distinto al código).
      if ((e.key === '+' || e.key === '=') && (!typingInField || inBarcode)) { e.preventDefault(); bumpLast(1); return; }
      if ((e.key === '-') && (!typingInField || inBarcode)) { e.preventDefault(); bumpLast(-1); return; }
      if (e.key === 'Delete' && !typingInField) { e.preventDefault(); bumpLast(-9999); return; }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [openCheckout, bumpLast, checkoutOpen, customerOpen, discountOpen, ticket, cart.length]);

  const finishSale = async (payments: PaymentSplit[], onCredit: boolean, cashReceived?: number) => {
    if (cart.length === 0) return;
    const idempotencyKey = `pos-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const paid = payments.reduce((s, p) => s + p.amount, 0);
    const payload = {
      branchId: branchId || 1,
      cashRegisterId: shift?.cashRegisterId ?? null,
      shiftId: shift?.shiftId ?? null,
      customerId: customer?.id ?? null,
      priceListId,
      globalDiscount,
      onCredit,
      lines: cart.map((l) => ({
        productId: l.productId,
        description: l.description,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discount: promoResult.discountByProduct[l.productId] || 0,
      })),
      payments: payments.filter((p) => p.amount > 0).map((p) => ({ method: p.method, amount: p.amount })),
      idempotencyKey,
    };

    const cashPaid = payments.filter((p) => p.method === 'CASH').reduce((s, p) => s + p.amount, 0);
    // El efectivo recibido (con el que se calcula el cambio) puede ser mayor que el aplicado.
    const received = cashReceived != null && cashReceived > 0 ? cashReceived : (cashPaid > 0 ? cashPaid : paid);
    const change = onCredit ? 0 : round2(Math.max(received - Math.max(total - (paid - cashPaid), 0), 0));
    const methodLabel = payments.length === 1
      ? labelOf(payments[0].method)
      : onCredit ? 'Crédito' : 'Pago mixto';
    const ticketBase: Omit<TicketData, 'folio' | 'offline'> = {
      branchName: activeBranch?.name ?? 'Matriz',
      dateTime: new Date(),
      lines: cart.map((l) => ({
        description: l.description, quantity: l.quantity,
        unitPrice: l.unitPrice,
        lineTotal: l.quantity * l.unitPrice - (promoResult.discountByProduct[l.productId] || 0),
      })),
      total,
      methodLabel,
      received: onCredit ? paid : received,
      change,
    };

    try {
      const { data } = await api.post<{ saleId: number }>('/sales', payload);
      setTicket({ ...ticketBase, folio: data?.saleId ? `V-${data.saleId}` : idempotencyKey.slice(-6), offline: false });
      toast.success(
        onCredit ? 'Venta a crédito registrada' : 'Venta registrada',
        onCredit ? `Saldo a crédito: ${money(total - paid)}.`
          : change > 0 ? `Cambio a entregar: ${money(change)}.` : 'Se descontó el inventario.',
      );
    } catch {
      queueSale(payload);
      setPending(pendingCount());
      setTicket({ ...ticketBase, folio: idempotencyKey.slice(-6), offline: true });
      toast.info('Venta guardada sin conexión', 'Se sincronizará automáticamente al reconectar.');
    }
    clearCart();
    setCustomer(null);
    setCheckoutOpen(false);
    barcodeRef.current?.focus();
  };

  const sync = async () => {
    try {
      const synced = await flushQueue(async (sale) => {
        await api.post('/sales', sale);
      });
      setPending(pendingCount());
      setMessage(synced > 0 ? `${synced} venta(s) sincronizada(s).` : 'No hay ventas pendientes.');
      if (synced > 0) {
        toast.success('Ventas sincronizadas', `${synced} venta(s) enviada(s) al servidor.`);
      } else {
        toast.info('Sin ventas pendientes', 'No hay nada por sincronizar.');
      }
    } catch {
      setPending(pendingCount());
      toast.error('No se pudieron sincronizar las ventas', 'Revisa la conexión e inténtalo de nuevo.');
    }
  };

  const timeLabel = now.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
  const dateLabel = now.toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long' });

  // Regla de negocio: administración y supervisión NO venden. Si el usuario no puede vender,
  // el punto de venta queda bloqueado con un mensaje claro (el backend también lo rechaza).
  if (!sellAllowed) {
    return (
      <div className="pos-blocked">
        <div className="pos-blocked-card">
          <ShieldAlert size={44} strokeWidth={1.5} />
          <h2>El punto de venta es para el personal de caja</h2>
          <p>
            Tu rol administra y supervisa el negocio, pero no registra ventas. Las ventas las
            realizan los cajeros desde su propia caja. Desde aquí puedes consultar ventas por
            cajero, cortes de caja, reportes y comprobantes.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="pos">
      {/* Puerta de turno: exige caja abierta con fondo inicial antes de vender. */}
      <PosShift branchId={branchId || 1} onShiftChange={setShift} />

      {/* Catálogo */}
      <div className="pos-catalog">
        <header className="pos-header">
          <div className="pos-header-left">
            <span className="pos-header-title">Punto de venta</span>
            <span className="pos-header-date">{dateLabel} · {timeLabel}</span>
          </div>
          <div className="pos-header-controls">
            {priceLists.length > 1 && (
              <div className="pos-pricelist">
                <span className="pos-pricelist-icon"><Tag size={15} /></span>
                <select value={priceListId} onChange={(e) => setPriceListId(Number(e.target.value))}
                  aria-label="Lista de precio">
                  {priceLists.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
                </select>
              </div>
            )}
            <div className="pos-branch">
              <span className="pos-branch-icon"><Store size={18} /></span>
              {branches.length > 1 ? (
                <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}
                  className="pos-branch-select" aria-label="Sucursal">
                  {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              ) : (
                <span className="pos-branch-name">{activeBranch?.name ?? 'Matriz'}</span>
              )}
            </div>
          </div>
        </header>

        <div className="pos-toolbar">
          <div className="pos-field pos-field-barcode">
            <span className="pos-field-icon"><ScanBarcode size={17} /></span>
            <input
              ref={barcodeRef}
              value={barcode}
              onChange={(e) => setBarcode(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && addByBarcode()}
              placeholder="Escanea o teclea el código y Enter (F2)"
              autoFocus
            />
          </div>
          <div className="pos-field pos-field-qty" title="Cantidad a agregar">
            <span className="pos-qty-label">Cant.</span>
            <input
              type="number" min={1} step="1"
              value={qtyInput}
              onChange={(e) => setQtyInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') barcodeRef.current?.focus(); }}
              aria-label="Cantidad"
            />
          </div>
          <div className="pos-field">
            <span className="pos-field-icon"><Search size={17} /></span>
            <input
              ref={searchRef}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar producto por nombre (F3)"
            />
          </div>
        </div>

        <div className="pos-cats">
          <button
            className={`pos-cat ${activeCategory === 'all' ? 'is-active' : ''}`}
            onClick={() => setActiveCategory('all')}
          >
            <span className="pos-cat-dot" style={{ background: '#64748b' }} />
            Todos
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              className={`pos-cat ${activeCategory === c.id ? 'is-active' : ''}`}
              onClick={() => setActiveCategory(c.id)}
            >
              <span className="pos-cat-dot" style={{ background: colorFor(c.name) }} />
              {c.name}
            </button>
          ))}
        </div>

        <AnimatePresence>
          {message && (
            <motion.div className="pos-message"
              initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}>
              {message}
            </motion.div>
          )}
        </AnimatePresence>

        <motion.div className="pos-grid" variants={staggerContainer} initial="hidden" animate="visible">
          {visibleProducts.map((p) => {
            const shownPrice = priceForList(p, priceListId);
            return (
              <motion.button key={p.id} className="pos-tile" onClick={() => addProduct(p)} title={p.name}
                variants={staggerItem} whileHover={{ y: -3 }} whileTap={{ scale: 0.96 }} transition={springSoft}>
                {p.extras?.onSale && <span className="pos-tile-sale">Oferta</span>}
                {p.imageUrl ? (
                  <img src={p.imageUrl} alt={p.name} className="pos-tile-img" />
                ) : (
                  <span className="pos-tile-avatar" style={{ background: colorFor(p.name) }}>
                    {initials(p.name)}
                  </span>
                )}
                <span className="pos-tile-code">#{p.id}</span>
                <span className="pos-tile-name">{p.name}</span>
                <span className="pos-tile-foot">
                  <span className="pos-tile-price">{money(shownPrice)}</span>
                  <span className="pos-tile-unit">{p.unit}</span>
                </span>
              </motion.button>
            );
          })}
          {visibleProducts.length === 0 && (
            <div className="pos-grid-empty">
              {products.length === 0
                ? 'Aún no hay productos. Regístralos en la sección Productos.'
                : 'Ningún producto coincide con la búsqueda.'}
            </div>
          )}
        </motion.div>
      </div>

      {/* Ticket / carrito */}
      <aside className="pos-ticket">
        {/* Cliente */}
        <button className="pos-customer" onClick={() => setCustomerOpen(true)}>
          <span className="pos-customer-icon"><User size={16} /></span>
          <span className="pos-customer-info">
            {customer ? (
              <>
                <span className="pos-customer-name">{customer.name}</span>
                <span className="pos-customer-sub">
                  Crédito disponible: {money(customer.creditAvailable)}
                </span>
              </>
            ) : (
              <>
                <span className="pos-customer-name">Público en general</span>
                <span className="pos-customer-sub">Toca para asignar cliente (F4)</span>
              </>
            )}
          </span>
          {customer ? (
            <span className="pos-customer-clear" onClick={(e) => { e.stopPropagation(); setCustomer(null); }}>
              <X size={15} />
            </span>
          ) : (
            <ChevronDown size={16} />
          )}
        </button>

        <div className="pos-ticket-head">
          <div>
            <div className="pos-ticket-title">Venta actual</div>
            <div className="pos-ticket-sub">
              {itemCount} artículo(s){activeList ? ` · ${activeList.name}` : ''}
            </div>
          </div>
          {cart.length > 0 && (
            <button className="pos-clear" onClick={clearCart}>Vaciar</button>
          )}
        </div>

        <div className="pos-lines">
          <AnimatePresence initial={false}>
            {cart.map((l) => (
              <motion.div key={l.productId} className="pos-line" layout
                initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 20, height: 0 }} transition={springSoft}>
                <span className="pos-line-avatar" style={{ background: colorFor(l.description) }}>
                  {initials(l.description)}
                </span>
                <div className="pos-line-info">
                  <div className="pos-line-name"><span className="pos-line-code">#{l.productId}</span> {l.description}</div>
                  <div className="pos-line-price">
                    <input className="pos-line-price-input" type="number" min={0} step="0.01"
                      value={l.unitPrice}
                      onChange={(e) => setUnitPrice(l.productId, Number(e.target.value) || 0)} />
                    <span> c/u</span>
                  </div>
                  {promoResult.labelByProduct[l.productId] && (
                    <span className="pos-line-promo">
                      <Tag size={11} /> {promoResult.labelByProduct[l.productId]} · -{money(promoResult.discountByProduct[l.productId])}
                    </span>
                  )}
                </div>
                <div className="pos-qty">
                  <button onClick={() => setQuantity(l.productId, l.quantity - 1)} aria-label="Menos">
                    <Minus size={14} />
                  </button>
                  <input
                    value={l.quantity}
                    onChange={(e) => setQuantity(l.productId, Number(e.target.value) || 0)}
                    inputMode="numeric"
                  />
                  <button onClick={() => setQuantity(l.productId, l.quantity + 1)} aria-label="Más">
                    <Plus size={14} />
                  </button>
                </div>
                <div className="pos-line-total">{money(l.quantity * l.unitPrice - (promoResult.discountByProduct[l.productId] || 0))}</div>
                <button className="pos-line-remove" onClick={() => removeLine(l.productId)} aria-label="Quitar">
                  <X size={15} />
                </button>
              </motion.div>
            ))}
          </AnimatePresence>
          {cart.length === 0 && (
            <div className="pos-empty">
              <div className="pos-empty-icon"><ShoppingCart size={44} strokeWidth={1.5} /></div>
              <p>Agrega productos tocando la cuadrícula o escaneando su código.</p>
            </div>
          )}
        </div>

        <div className="pos-summary">
          <div className="pos-summary-line">
            <span>Subtotal</span>
            <span>{money(cart.reduce((s, l) => s + l.quantity * l.unitPrice, 0))}</span>
          </div>
          {promoResult.total > 0 && (
            <div className="pos-summary-line pos-summary-promo">
              <span><Tag size={13} /> Promociones</span>
              <span>- {money(promoResult.total)}</span>
            </div>
          )}
          <button className="pos-summary-discount" onClick={() => setDiscountOpen(true)}>
            <span><Percent size={14} /> Descuento</span>
            <span className={globalDiscount > 0 ? 'has-discount' : ''}>
              {globalDiscount > 0 ? `- ${money(globalDiscount)}` : 'Agregar'}
            </span>
          </button>
          <div className="pos-summary-row">
            <span>Total</span>
            <strong className="pos-summary-total">{money(total)}</strong>
          </div>
          <button
            className="pos-pay"
            disabled={cart.length === 0 || !shift}
            onClick={openCheckout}
            title={!shift ? 'Abre tu caja para poder cobrar' : undefined}
          >
            {!shift ? 'Abre tu caja para vender' : <>Cobrar {money(total)} <kbd className="pos-pay-kbd">F9 / Enter</kbd></>}
          </button>
          <div className="pos-hotkeys">
            <span><kbd>F2</kbd> código</span>
            <span><kbd>F3</kbd> buscar</span>
            <span><kbd>+</kbd><kbd>−</kbd> cantidad</span>
            <span><kbd>F4</kbd> cliente</span>
            <span><kbd>Enter</kbd> cobrar</span>
            <span><kbd>F12</kbd> exacto</span>
            <span><kbd>P</kbd> imprimir</span>
          </div>
          <div className="pos-sync">
            <span className="badge badge-muted">Pendientes por sincronizar: {pending}</span>
            <button className="btn-ghost pos-sync-btn" onClick={sync} disabled={pending === 0}>
              <RefreshCw size={14} /> Sincronizar
            </button>
          </div>
        </div>
      </aside>

      <AnimatePresence>
        {customerOpen && (
          <CustomerPicker
            onClose={() => setCustomerOpen(false)}
            onPick={(c) => { setCustomer(c); setCustomerOpen(false); }}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {discountOpen && (
          <DiscountModal
            subtotal={linesSubtotal}
            current={globalDiscount}
            onCancel={() => setDiscountOpen(false)}
            onApply={(d) => { setGlobalDiscount(d); setDiscountOpen(false); }}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {checkoutOpen && (
          <CheckoutModal
            total={total}
            customer={customer}
            onCancel={() => setCheckoutOpen(false)}
            onConfirm={finishSale}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {ticket && (
          <motion.div className="pos-modal-overlay" onClick={() => setTicket(null)}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
            <motion.div className="ticket-modal" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97 }} transition={springSoft}>
              <div className="ticket-modal-head">
                <h3>Venta registrada</h3>
                <button className="modal-close" onClick={() => setTicket(null)} aria-label="Cerrar">×</button>
              </div>
              <div className="ticket-modal-body">
                <Ticket settings={ticketSettings} data={ticket} />
              </div>
              <div className="ticket-actions">
                <button className="btn-ghost" onClick={() => setTicket(null)}>Cerrar</button>
                <button className="pos-confirm" onClick={() => ticket && printTicketHtml(saleTicketHtml(ticketSettings, ticket))}>
                  <TicketIcon size={16} /> Imprimir ticket <span className="pos-key-hint">P / Enter</span>
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

const labelOf = (m: PaymentMethod) =>
  m === 'CASH' ? 'Efectivo' : m === 'CARD' ? 'Tarjeta' : m === 'TRANSFER' ? 'Transferencia' : 'Vale';

/**
 * Selector de cliente: busca en el servidor por nombre/RFC/teléfono y permite asignar uno.
 */
function CustomerPicker({ onClose, onPick }:
  { onClose: () => void; onPick: (c: Customer) => void }) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const t = setTimeout(async () => {
      try {
        const { data } = await api.get<Customer[]>('/customers', { params: { q, limit: 30 } });
        if (!cancelled) setResults(data);
      } catch {
        if (!cancelled) setResults([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 250);
    return () => { cancelled = true; clearTimeout(t); };
  }, [q]);

  return (
    <motion.div className="pos-modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-modal pos-modal-picker" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={springSoft}>
        <div className="pos-modal-head">
          <h3><User size={18} /> Asignar cliente</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="pos-field pos-picker-search">
          <span className="pos-field-icon"><Search size={16} /></span>
          <input value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="Buscar por nombre, RFC o teléfono…" autoFocus />
        </div>
        <div className="pos-picker-list">
          <button className="pos-picker-item is-general" onClick={() =>
            onPick({ id: 0, name: 'Público en general', rfc: null, phone: null, creditAvailable: 0 })}>
            <span className="pos-picker-avatar" style={{ background: '#64748b' }}><Users size={16} /></span>
            <span className="pos-picker-info">
              <span className="pos-picker-name">Público en general</span>
              <span className="pos-picker-sub">Venta sin cliente registrado</span>
            </span>
          </button>
          {loading && <div className="pos-picker-empty">Buscando…</div>}
          {!loading && results.map((c) => (
            <button key={c.id} className="pos-picker-item" onClick={() => onPick(c)}>
              <span className="pos-picker-avatar" style={{ background: colorFor(c.name) }}>
                {initials(c.name)}
              </span>
              <span className="pos-picker-info">
                <span className="pos-picker-name">{c.name}</span>
                <span className="pos-picker-sub">
                  {c.rfc ? `RFC ${c.rfc} · ` : ''}Crédito: {money(c.creditAvailable)}
                </span>
              </span>
            </button>
          ))}
          {!loading && results.length === 0 && q && (
            <div className="pos-picker-empty">
              <UserPlus size={20} /> Sin coincidencias. Da de alta al cliente en la sección Clientes.
            </div>
          )}
        </div>
      </motion.div>
    </motion.div>
  );
}

/** Modal de descuento global: por importe o por porcentaje del subtotal. */
function DiscountModal({ subtotal, current, onCancel, onApply }:
  { subtotal: number; current: number; onCancel: () => void; onApply: (d: number) => void }) {
  const [mode, setMode] = useState<'amount' | 'percent'>('amount');
  const [value, setValue] = useState<string>(current ? String(current) : '');
  const num = Number(value) || 0;
  const computed = mode === 'percent'
    ? Math.min((subtotal * num) / 100, subtotal)
    : Math.min(num, subtotal);

  return (
    <motion.div className="pos-modal-overlay" onClick={onCancel}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={springSoft}>
        <div className="pos-modal-head">
          <h3><Percent size={18} /> Descuento de la venta</h3>
          <button className="modal-close" onClick={onCancel} aria-label="Cerrar">×</button>
        </div>
        <div className="pos-methods">
          <button className={`pos-method ${mode === 'amount' ? 'is-active' : ''}`}
            onClick={() => setMode('amount')}>Importe $</button>
          <button className={`pos-method ${mode === 'percent' ? 'is-active' : ''}`}
            onClick={() => setMode('percent')}>Porcentaje %</button>
        </div>
        <label className="pos-received">
          <span>{mode === 'percent' ? 'Porcentaje a descontar' : 'Importe a descontar'}</span>
          <input type="number" min={0} value={value} autoFocus
            onChange={(e) => setValue(e.target.value)} placeholder="0" />
        </label>
        <div className="pos-change">
          <span>Descuento aplicado</span>
          <strong>{money(computed)}</strong>
        </div>
        <div className="pos-modal-actions">
          <button className="btn-ghost" onClick={() => onApply(0)}><Trash2 size={15} /> Quitar</button>
          <button className="pos-confirm" onClick={() => onApply(computed)}>Aplicar</button>
        </div>
      </motion.div>
    </motion.div>
  );
}

/** Redondeo a 2 decimales sin errores de punto flotante (evita 248.45999999). */
const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** Denominaciones de billetes y monedas de México para el cobro rápido en efectivo. */
const MXN_DENOMS = [1000, 500, 200, 100, 50, 20, 10, 5, 2, 1];

/**
 * Modal de cobro premium: pagos MIXTOS y VENTA A CRÉDITO. Para efectivo ofrece billetes rápidos
 * (denominaciones MXN), "pago exacto" y captura libre, y calcula el cambio con redondeo correcto.
 */
function CheckoutModal({ total, customer, onCancel, onConfirm }:
  {
    total: number;
    customer: Customer | null;
    onConfirm: (payments: PaymentSplit[], onCredit: boolean, cashReceived?: number) => void;
    onCancel: () => void;
  }) {
  const totalR = round2(total);
  const [onCredit, setOnCredit] = useState(false);
  // Efectivo recibido (para el cambio). Inicia en 0 = aún no se captura.
  const [cashReceived, setCashReceived] = useState(0);
  // Pagos que NO son efectivo (tarjeta, transferencia, vale) para pago mixto.
  const [otherSplits, setOtherSplits] = useState<PaymentSplit[]>([]);

  const otherPaid = round2(otherSplits.reduce((s, p) => s + (Number(p.amount) || 0), 0));
  // Lo que falta cubrir con efectivo tras los otros métodos.
  const cashDue = round2(Math.max(totalR - otherPaid, 0));
  const change = round2(Math.max(cashReceived - cashDue, 0));
  const covered = round2(otherPaid + Math.min(cashReceived, cashDue));
  const missing = round2(Math.max(totalR - covered, 0));

  const creditBalance = round2(Math.max(totalR - otherPaid - cashReceived, 0));
  const creditExceeds = onCredit && customer != null && creditBalance > customer.creditAvailable;
  const canConfirm = onCredit
    ? (customer != null && customer.id > 0 && !creditExceeds)
    : missing <= 0;

  // Sugerencias de billetes: el importe exacto y los billetes >= al adeudo de efectivo.
  const quickBills = [round2(cashDue), ...MXN_DENOMS.filter((d) => d >= cashDue)]
    .filter((v, i, arr) => arr.indexOf(v) === i)
    .slice(0, 6);

  const addBill = (v: number) => setCashReceived((prev) => round2(prev + v));
  const setOther = (i: number, patch: Partial<PaymentSplit>) =>
    setOtherSplits((prev) => prev.map((s, idx) => (idx === i ? { ...s, ...patch } : s)));
  const addOther = () => setOtherSplits((prev) => [...prev, { method: 'CARD', amount: round2(Math.max(cashDue, 0)) }]);
  const removeOther = (i: number) => setOtherSplits((prev) => prev.filter((_, idx) => idx !== i));

  const buildPayments = (): PaymentSplit[] => {
    const list: PaymentSplit[] = [];
    // Efectivo aplicado = lo que cubre (sin el cambio).
    const cashApplied = round2(Math.min(cashReceived, cashDue));
    if (!onCredit && cashApplied > 0) list.push({ method: 'CASH', amount: cashApplied });
    if (onCredit && cashReceived > 0) list.push({ method: 'CASH', amount: round2(cashReceived) });
    otherSplits.filter((s) => (Number(s.amount) || 0) > 0).forEach((s) => list.push({ method: s.method, amount: round2(s.amount) }));
    return list;
  };

  const otherMethods: PaymentMethod[] = ['CARD', 'TRANSFER', 'VOUCHER'];
  const MethodIcon = ({ m }: { m: PaymentMethod }) =>
    m === 'CASH' ? <Banknote size={16} /> : m === 'CARD' ? <CreditCard size={16} />
      : m === 'TRANSFER' ? <Landmark size={16} /> : <TicketIcon size={16} />;

  // Atajos del cobro: F12 pago exacto · Enter confirmar · Esc cancelar.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'F12') { e.preventDefault(); if (!onCredit) setCashReceived(cashDue); }
      else if (e.key === 'Enter') { e.preventDefault(); if (canConfirm) onConfirm(buildPayments(), onCredit, cashReceived); }
      else if (e.key === 'Escape') { e.preventDefault(); onCancel(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canConfirm, cashDue, cashReceived, onCredit, otherSplits]);

  return (
    <motion.div className="pos-modal-overlay" onClick={onCancel}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-checkout" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.96, y: 14 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={springSoft}>
        <button className="pos-checkout-close" onClick={onCancel} aria-label="Cerrar"><X size={18} /></button>

        {/* Hero con el total */}
        <div className="pos-checkout-hero">
          <span className="pos-checkout-hero-label">Total a pagar</span>
          <strong className="pos-checkout-hero-total">{money(totalR)}</strong>
        </div>

        <div className="pos-checkout-body">
          {/* Conmutador contado / crédito */}
          <div className="pos-mode-switch">
            <button className={`pos-mode ${!onCredit ? 'is-active' : ''}`} onClick={() => setOnCredit(false)}>
              <Banknote size={15} /> De contado
            </button>
            <button className={`pos-mode ${onCredit ? 'is-active' : ''}`}
              onClick={() => setOnCredit(true)} disabled={!customer || customer.id === 0}>
              <CreditCard size={15} /> A crédito
            </button>
          </div>

          {onCredit && (!customer || customer.id === 0) && (
            <p className="pos-credit-warn">Asigna un cliente registrado para vender a crédito.</p>
          )}

          {/* Efectivo recibido: billetes rápidos + captura */}
          <div className="pos-cash-block">
            <div className="pos-cash-head">
              <span className="pos-cash-title"><Banknote size={16} /> Efectivo recibido</span>
              {cashReceived > 0 && (
                <button className="pos-cash-clear" onClick={() => setCashReceived(0)}>Limpiar</button>
              )}
            </div>
            <div className="pos-cash-amount">
              <span className="pos-cash-symbol">$</span>
              <input type="number" min={0} step="0.01" value={cashReceived || ''} placeholder="0.00"
                onChange={(e) => setCashReceived(round2(Number(e.target.value) || 0))} autoFocus />
            </div>
            <div className="pos-bills">
              <button className="pos-bill pos-bill-exact" onClick={() => setCashReceived(cashDue)}>
                Pago exacto · {money(cashDue)} <span className="pos-key-hint">F12</span>
              </button>
              {quickBills.map((v, i) => (
                <button key={`${v}-${i}`} className="pos-bill" onClick={() => addBill(v)}>+ {money(v)}</button>
              ))}
            </div>
          </div>

          {/* Otras formas de pago (mixto) */}
          {otherSplits.map((s, i) => (
            <div className="pos-split" key={i}>
              <select value={s.method} onChange={(e) => setOther(i, { method: e.target.value as PaymentMethod })}>
                {otherMethods.map((m) => <option key={m} value={m}>{labelOf(m)}</option>)}
              </select>
              <div className="pos-split-amount">
                <MethodIcon m={s.method} />
                <input type="number" min={0} step="0.01" value={s.amount}
                  onChange={(e) => setOther(i, { amount: round2(Number(e.target.value) || 0) })} />
              </div>
              <button className="pos-split-remove" onClick={() => removeOther(i)} aria-label="Quitar pago">
                <X size={15} />
              </button>
            </div>
          ))}
          <button className="pos-split-add" onClick={addOther}>
            <Plus size={14} /> Agregar tarjeta / transferencia / vale
          </button>

          {/* Resumen dinámico */}
          {!onCredit ? (
            <div className={`pos-change ${missing > 0 ? 'is-short' : ''}`}>
              <span>{missing > 0 ? 'Falta por cubrir' : 'Cambio'}</span>
              <strong>{money(missing > 0 ? missing : change)}</strong>
            </div>
          ) : (
            <div className={`pos-change ${creditExceeds ? 'is-short' : ''}`}>
              <span>Saldo a crédito</span>
              <strong>{money(creditBalance)}</strong>
            </div>
          )}
          {creditExceeds && (
            <p className="pos-credit-warn">
              El saldo ({money(creditBalance)}) supera el crédito disponible del cliente
              ({money(customer!.creditAvailable)}).
            </p>
          )}

          <div className="pos-modal-actions">
            <button className="btn-ghost" onClick={onCancel}>Cancelar</button>
            <button className="pos-confirm" disabled={!canConfirm} onClick={() => onConfirm(buildPayments(), onCredit, cashReceived)}>
              {onCredit ? 'Registrar a crédito' : 'Confirmar cobro'} <span className="pos-key-hint">Enter</span>
            </button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
