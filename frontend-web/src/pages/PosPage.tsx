import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Store, ScanBarcode, Search, ShoppingCart, Minus, Plus, X,
  Banknote, CreditCard, Landmark, RefreshCw, Ticket as TicketIcon,
  User, Users, UserPlus, Tag, Percent, Trash2, ChevronDown, Sparkles,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { queueSale, flushQueue, pendingCount } from '@/lib/offlineQueue';
import { staggerContainer, staggerItem, springSoft } from '@/lib/motion';
import { Ticket, defaultTicketSettings, type TicketSettings, type TicketData } from '@/components/Ticket';
import { applyPromotions, type Promotion } from '@/lib/promotions';
import { PosShift, cachedShift, type ActiveShift } from '@/components/PosShift';
import { setPaperWidth } from '@/lib/thermalPrint';
import { printSale } from '@/lib/printRouter';
import { useSession } from '@/store/session';
import { decodeToken, canSell, hasModule } from '@/lib/jwt';
import { ShieldAlert } from 'lucide-react';
import type { AxiosError } from 'axios';
import './pos.css';

/** Precios profesionales del producto (coinciden con ProductExtras del backend). */
interface ProductExtras {
  price2: number;
  price3: number;
  price4: number;
  price5: number;
  taxRate: number;
  onSale: boolean;
  /** Precio FIJO de merma del artículo (giro panadería). 0 si no se configuró. */
  wastePrice: number;
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
  /** Identificador estable por renglón (permite varios renglones libres sin colisionar). */
  id: string;
  /** Producto del catálogo, o null para un artículo libre (producto rápido). */
  productId: number | null;
  categoryId: number | null;
  description: string;
  quantity: number;
  unitPrice: number;
  discount: number;
  /** IVA informativo del renglón (precios con IVA incluido; no suma al total). */
  taxRate?: number;
  /** Unidad de medida del artículo libre (pieza, kg, lt, …). Solo para artículos libres. */
  unit?: string;
  /** true si es un artículo libre capturado con "Producto rápido". */
  free?: boolean;
  /**
   * true si el renglón se vende como MERMA (giro panadería). El precio pasa al precio de merma
   * FIJO del artículo (no editable); el renglón viaja en la venta con waste=true y el backend
   * descuenta de la existencia de merma.
   */
  waste?: boolean;
  /** Precio de merma FIJO del artículo (del catálogo). Habilita el toggle si es > 0. */
  wastePrice?: number;
  /** Precio normal del renglón, guardado al marcar merma para restaurarlo al desmarcar. */
  normalPrice?: number;
}

/** Detalle del ProblemDetail 409 INSUFFICIENT_STOCK que devuelve el backend al sobrevender. */
interface InsufficientStock {
  title?: string;
  productId?: number;
  productName?: string | null;
  available?: number;
  requested?: number;
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
  // Producto seleccionado en la cuadrícula que espera que el cajero confirme la cantidad en el modal.
  const [qtyModalProduct, setQtyModalProduct] = useState<Product | null>(null);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [customerOpen, setCustomerOpen] = useState(false);
  // Precios especiales del cliente seleccionado (producto -> precio pactado). Tienen prioridad
  // sobre la lista de precios y el precio normal. Vacio = el cliente no tiene precios especiales.
  const [specialPrices, setSpecialPrices] = useState<Record<number, number>>({});
  // Existencia por producto en la sucursal activa (productId -> cantidad). En giro panadería el POS
  // solo muestra y vende panes CON existencia > 0: no se puede vender lo que no está en inventario.
  const [stockMap, setStockMap] = useState<Record<number, number>>({});
  // Existencia EN MERMA por producto en la sucursal activa (productId -> piezas en merma). En giro
  // panadería el botón "Vender como merma" solo aparece y se topa si hay piezas en merma (> 0):
  // "no se vende lo que no hay" también para la merma.
  const [wasteStockMap, setWasteStockMap] = useState<Record<number, number>>({});
  const [globalDiscount, setGlobalDiscount] = useState(0);
  const [discountOpen, setDiscountOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState(pendingCount());
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [ticket, setTicket] = useState<TicketData | null>(null);
  // Estado de la impresión directa del ticket recién vendido (controla el modal post-venta):
  //  'printing'  = enviando al agente ; 'printed' = impreso ; 'no-printer' = sin impresora
  //  (la venta igual quedó guardada y se puede reimprimir desde Reimpresión).
  const [printState, setPrintState] = useState<'printing' | 'printed' | 'no-printer' | null>(null);
  const [ticketSettings, setTicketSettings] = useState<TicketSettings>(defaultTicketSettings);
  const [promos, setPromos] = useState<Promotion[]>([]);
  const [now, setNow] = useState(new Date());
  const [shift, setShift] = useState<ActiveShift | null>(cachedShift());
  const [lastLineId, setLastLineId] = useState<string | null>(null);
  const [qtyInput, setQtyInput] = useState('1');
  const [quickOpen, setQuickOpen] = useState(false);
  const barcodeRef = useRef<HTMLInputElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const token = useSession((s) => s.token);
  const claims = decodeToken(token);
  const sellAllowed = canSell(claims);
  const bakeryEnabled = hasModule(claims, 'bakery');

  // Descuentos automáticos por promociones vigentes, recalculados con el carrito. Los artículos
  // libres (sin productId) no participan en promociones.
  const promoResult = useMemo(
    () => applyPromotions(promos, cart
      .filter((l): l is CartLine & { productId: number } => l.productId != null)
      .map((l) => ({
        productId: l.productId, categoryId: l.categoryId, quantity: l.quantity, unitPrice: l.unitPrice,
      }))),
    [promos, cart],
  );

  /**
   * Descuento por promoción de un renglón (0 para artículos libres, renglones de merma o sin
   * promoción). La merma se vende a su precio fijo: no acumula promociones.
   */
  const promoDiscountOf = (l: CartLine) =>
    l.productId != null && !l.waste ? (promoResult.discountByProduct[l.productId] || 0) : 0;

  const linesSubtotal = cart.reduce(
    (sum, l) => sum + l.quantity * l.unitPrice - promoDiscountOf(l), 0);
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
          .catch(() => { });
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

  // Carga la existencia de la sucursal (solo giro panadería) para mostrar/vender únicamente panes
  // con stock > 0. Se recarga al cambiar de sucursal y tras cada venta (reusa el contador 'pending'
  // y 'cart' vacío como señal de que algo cambió). En otros giros no se filtra por existencia.
  useEffect(() => {
    if (!bakeryEnabled || !branchId) { setStockMap({}); return; }
    let cancelled = false;
    api.get<{ productId: number; quantity: number }[]>('/inventory/stock', { params: { branchId } })
      .then((r) => {
        if (cancelled) return;
        const m: Record<number, number> = {};
        for (const row of r.data) m[row.productId] = Number(row.quantity) || 0;
        setStockMap(m);
      })
      .catch(() => { if (!cancelled) setStockMap({}); });
    return () => { cancelled = true; };
  }, [bakeryEnabled, branchId, ticket]);

  // Carga la existencia EN MERMA de la sucursal (solo giro panadería) para habilitar/topar el
  // botón "Vender como merma": solo panes CON existencia de merma > 0 pueden venderse como merma
  // ("no se vende lo que no hay"). Se recarga al cambiar de sucursal y tras cada venta (misma
  // señal 'ticket' que el stock normal). Fuera del giro no se carga.
  useEffect(() => {
    if (!bakeryEnabled || !branchId) { setWasteStockMap({}); return; }
    let cancelled = false;
    api.get<{ productId: number; quantity: number }[]>('/bakery/stock/available', { params: { branchId, kind: 'waste' } })
      .then((r) => {
        if (cancelled) return;
        const m: Record<number, number> = {};
        for (const row of r.data) m[row.productId] = Number(row.quantity) || 0;
        setWasteStockMap(m);
      })
      .catch(() => { if (!cancelled) setWasteStockMap({}); });
    return () => { cancelled = true; };
  }, [bakeryEnabled, branchId, ticket]);


  useEffect(() => {
    localStorage.setItem(PRICELIST_KEY, String(priceListId));
  }, [priceListId]);

  // Carga los precios especiales del cliente seleccionado (o los limpia al quitarlo). Es la
  // fuente de verdad del precio pactado por cliente; el POS lo resuelve con effectivePrice.
  useEffect(() => {
    if (!customer || customer.id <= 0) {
      setSpecialPrices({});
      return;
    }
    let cancelado = false;
    api.get<Record<number, number>>(`/customers/${customer.id}/prices/map`)
      .then((r) => { if (!cancelado) setSpecialPrices(r.data || {}); })
      .catch(() => { if (!cancelado) setSpecialPrices({}); });
    return () => { cancelado = true; };
  }, [customer]);

  /**
   * Precio efectivo de un producto con la prioridad del negocio:
   *   1) precio ESPECIAL pactado para el cliente seleccionado (si existe),
   *   2) precio de la LISTA de precios elegida,
   *   3) precio NORMAL del producto (respaldo dentro de priceForList).
   */
  const effectivePrice = useCallback((p: Product): number => {
    const especial = specialPrices[p.id];
    if (especial != null && especial >= 0) return especial;
    return priceForList(p, priceListId);
  }, [specialPrices, priceListId]);

  // Al cambiar de lista de precio O de cliente (precios especiales), recalcula el precio de los
  // renglones de catálogo. Los artículos libres conservan su precio capturado.
  useEffect(() => {
    setCart((prev) => prev.map((l) => {
      if (l.productId == null) return l;
      const prod = products.find((p) => p.id === l.productId);
      if (!prod) return l;
      return { ...l, unitPrice: effectivePrice(prod) };
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [priceListId, specialPrices]);

  const visibleProducts = useMemo(() => {
    const q = search.trim().toLowerCase();
    return products.filter((p) => {
      const matchCat = activeCategory === 'all' || p.categoryId === activeCategory;
      const matchText = q === '' || p.name.toLowerCase().includes(q);
      // Giro panadería: solo panes CON existencia (> 0). No se vende lo que no hay en inventario.
      const hasStock = !bakeryEnabled || (stockMap[p.id] ?? 0) > 0;
      return matchCat && matchText && hasStock;
    });
  }, [products, activeCategory, search, bakeryEnabled, stockMap]);

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

  /**
   * Tope de piezas vendibles de un renglón según su existencia disponible en la sucursal activa
   * ("no se vende lo que no hay"). Devuelve null cuando NO se puede/debe topar en el cliente:
   *  - fuera del giro panadería (no se filtra por existencia),
   *  - artículos libres (sin productId, no tienen existencia en catálogo).
   * Para renglones de MERMA el tope es la existencia EN MERMA (wasteStockMap[productId]); para
   * renglones normales, la existencia normal (stockMap[productId]). En ambos casos el backend es
   * la segunda barrera (409 INSUFFICIENT_STOCK sobre inventory_stock / bakery_waste_stock).
   */
  const stockCapOf = useCallback((line: Pick<CartLine, 'productId' | 'waste'>): number | null => {
    if (!bakeryEnabled || line.productId == null) return null;
    const source = line.waste ? wasteStockMap : stockMap;
    return Math.max(0, Math.floor(source[line.productId] ?? 0));
  }, [bakeryEnabled, stockMap, wasteStockMap]);

  /** Tope por producto para la cuadrícula/modal de cantidad (misma regla, sin renglón todavía). */
  const stockCapForProduct = useCallback((productId: number): number | null => {
    if (!bakeryEnabled) return null;
    return Math.max(0, Math.floor(stockMap[productId] ?? 0));
  }, [bakeryEnabled, stockMap]);

  // Red de seguridad visual: ¿algún renglón quedó por encima de su existencia disponible? (p. ej.
  // tras recargar el stockMap post-venta). Si lo hay, el botón de cobro se bloquea para no
  // sobrevender; el tope del renglón normalmente lo evita, esto es la última barrera del cliente.
  const hasOverStockLine = useMemo(() => cart.some((l) => {
    const cap = stockCapOf(l);
    return cap != null && l.quantity > cap;
  }), [cart, stockCapOf]);

  const addProduct = (p: Product, qty = 1) => {
    addLine({
      id: crypto.randomUUID(), productId: p.id, categoryId: p.categoryId, description: p.name,
      quantity: qty, unitPrice: effectivePrice(p), discount: 0,
      wastePrice: p.extras?.wastePrice ?? 0,
    });
    setMessage(null);
  };

  const addLine = (line: CartLine) => {
    setCart((prev) => {
      // Los productos del catálogo se agrupan por productId; los artículos libres (productId
      // null) siempre crean un renglón nuevo e independiente.
      if (line.productId != null) {
        const existing = prev.find((l) => l.productId === line.productId);
        if (existing) {
          // Candado por existencia: al reagregar, la suma no puede superar lo disponible. Si
          // excede, se fija al máximo y se avisa sin interrumpir el flujo del cajero.
          const cap = stockCapOf(existing);
          const desired = existing.quantity + line.quantity;
          const next = cap != null ? Math.min(desired, cap) : desired;
          if (cap != null && desired > cap) {
            toast.info('Sin más existencia', `Solo hay ${cap} ${cap === 1 ? 'pieza' : 'piezas'} de ${existing.description}.`);
          }
          setLastLineId(existing.id);
          return prev.map((l) =>
            l.id === existing.id ? { ...l, quantity: Math.max(1, next) } : l,
          );
        }
      }
      // Renglón nuevo: topa la cantidad inicial a lo disponible.
      const cap = stockCapOf(line);
      if (cap != null && line.quantity > cap) {
        toast.info('Sin más existencia', `Solo hay ${cap} ${cap === 1 ? 'pieza' : 'piezas'} de ${line.description}.`);
      }
      const capped = cap != null ? { ...line, quantity: Math.max(1, Math.min(line.quantity, cap)) } : line;
      setLastLineId(capped.id);
      return [...prev, capped];
    });
  };

  const setQuantity = (id: string, quantity: number) => {
    // La cantidad nunca baja de 1: ajustar piezas y eliminar son acciones distintas. Para quitar
    // el renglón se usa el botón Eliminar (bote), no bajando la cantidad a 0. Se identifica por
    // id estable para que los artículos libres (sin productId) también funcionen.
    const q = Math.max(1, Math.floor(quantity) || 1);
    setCart((prev) => prev.map((l) => {
      if (l.id !== id) return l;
      // Candado por existencia ("no se vende lo que no hay"): la cantidad del renglón no puede
      // superar lo disponible. Al exceder se fija al máximo y se avisa de forma no intrusiva.
      const cap = stockCapOf(l);
      if (cap != null && q > cap) {
        toast.info('Sin más existencia', `Solo hay ${cap} ${cap === 1 ? 'pieza' : 'piezas'} de ${l.description}.`);
        return { ...l, quantity: Math.max(1, cap) };
      }
      return { ...l, quantity: q };
    }));
  };

  const setUnitPrice = (id: string, unitPrice: number) => {
    setCart((prev) => prev.map((l) => {
      if (l.id !== id) return l;
      // Un renglón de merma usa el precio de merma fijo del artículo: no es editable.
      if (l.waste) return l;
      return { ...l, unitPrice: Math.max(unitPrice, 0) };
    }));
  };

  /**
   * Marca/desmarca un renglón como MERMA (giro panadería). Al activarla, guarda el precio normal
   * y fija el precio del renglón al precio de merma FIJO del artículo (no editable); el renglón
   * viaja con waste=true y el backend descuenta de la existencia de merma. Al desactivarla,
   * restaura el precio normal. Solo aplica a productos de catálogo con precio de merma > 0.
   */
  const toggleWaste = (id: string) => {
    setCart((prev) => prev.map((l) => {
      if (l.id !== id) return l;
      if (l.waste) {
        const restored = l.normalPrice ?? l.unitPrice;
        return { ...l, waste: false, normalPrice: undefined, unitPrice: restored };
      }
      const wastePrice = l.wastePrice ?? 0;
      if (wastePrice <= 0) {
        return l; // sin precio de merma configurado no se puede vender como merma
      }
      // "No se vende lo que no hay" también para la merma: topa la cantidad a la existencia EN
      // MERMA del artículo al activar el modo (un renglón con cantidad normal alta no puede
      // superar lo disponible en merma). Si no hay existencia de merma no se activa (barrera; el
      // botón ya no se muestra por Fix 1).
      const cap = l.productId != null ? Math.max(0, Math.floor(wasteStockMap[l.productId] ?? 0)) : 0;
      if (cap <= 0) return l;
      const capped = Math.max(1, Math.min(l.quantity, cap));
      if (capped < l.quantity) {
        toast.info('Sin más existencia', `Solo hay ${cap} ${cap === 1 ? 'pieza' : 'piezas'} en merma de ${l.description}.`);
      }
      return { ...l, waste: true, normalPrice: l.unitPrice, unitPrice: wastePrice, quantity: capped };
    }));
  };

  const removeLine = (id: string) => {
    setCart((prev) => prev.filter((l) => l.id !== id));
    setLastLineId((cur) => (cur === id ? null : cur));
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
    // No se cobra si un renglón quedó por encima de su existencia ("no se vende lo que no hay").
    if (hasOverStockLine) {
      toast.error('Ajusta la cantidad', 'Hay un artículo por encima de su existencia. Corrige la cantidad para cobrar.');
      return;
    }
    if (cart.length > 0) setCheckoutOpen(true);
  }, [cart.length, shift, hasOverStockLine]);

  // Cierra el modal post-venta y deja la caja lista para la siguiente venta.
  const cerrarTicket = useCallback(() => {
    setTicket(null);
    setPrintState(null);
    barcodeRef.current?.focus();
  }, []);

  // Reimprime (fuerza) el ticket mostrado por el agente directo.
  const reimprimirTicket = useCallback(() => {
    if (!ticket) return;
    setPrintState('printing');
    void printSale(ticketSettings, ticket).then((r) => setPrintState(r));
  }, [ticket, ticketSettings]);

  // Modal post-venta: Enter/Esc cierran (siguiente venta); P reimprime. Y autocierre: en cuanto la
  // impresión directa termina (impreso o sin impresora), el modal se cierra solo tras 4 s para que
  // el cajero alcance a leer el cambio a entregar. Un cierre manual cancela el temporizador.
  useEffect(() => {
    if (!ticket) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === 'Escape') { e.preventDefault(); cerrarTicket(); }
      else if (e.key === 'p' || e.key === 'P') { e.preventDefault(); reimprimirTicket(); }
    };
    window.addEventListener('keydown', onKey);
    // Autocierre solo cuando la impresión ya resolvió (no mientras está 'printing').
    const t = (printState === 'printed' || printState === 'no-printer')
      ? setTimeout(cerrarTicket, 4000)
      : undefined;
    return () => {
      window.removeEventListener('keydown', onKey);
      if (t) clearTimeout(t);
    };
  }, [ticket, printState, cerrarTicket, reimprimirTicket]);

  // Ajusta la cantidad del último renglón agregado (para +/- rápidos con teclado).
  const bumpLast = useCallback((delta: number) => {
    setCart((prev) => {
      const id = lastLineId ?? prev[prev.length - 1]?.id;
      if (id == null) return prev;
      return prev
        .map((l) => {
          if (l.id !== id) return l;
          const desired = l.quantity + delta;
          // Candado por existencia también para los atajos +/− del teclado.
          const cap = stockCapOf(l);
          if (cap != null && delta > 0 && desired > cap) {
            toast.info('Sin más existencia', `Solo hay ${cap} ${cap === 1 ? 'pieza' : 'piezas'} de ${l.description}.`);
            return { ...l, quantity: cap };
          }
          return { ...l, quantity: desired };
        })
        .filter((l) => l.quantity > 0);
    });
  }, [lastLineId, stockCapOf]);

  // Atajos de teclado del cajero (flujo rápido sin mouse):
  //   F2 enfocar código · F4 cliente · F9 o Enter cobrar · +/- cantidad del último ·
  //   Supr quitar último · Esc enfocar código. El modal de cobro y el de ticket tienen los suyos.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      const typingInField = !!el && (el.tagName === 'INPUT' || el.tagName === 'SELECT' || el.tagName === 'TEXTAREA');
      const inBarcode = el === barcodeRef.current;
      // No interferir si hay un modal abierto (cada modal maneja su propio teclado).
      const modalOpen = checkoutOpen || customerOpen || discountOpen || quickOpen || ticket != null;

      if (e.key === 'F2') { e.preventDefault(); barcodeRef.current?.focus(); return; }
      if (e.key === 'F3') { e.preventDefault(); searchRef.current?.focus(); return; }
      if (e.key === 'F4') { e.preventDefault(); if (!modalOpen) setCustomerOpen(true); return; }
      if (e.key === 'F7') { e.preventDefault(); if (!modalOpen) setQuickOpen(true); return; }
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
  }, [openCheckout, bumpLast, checkoutOpen, customerOpen, discountOpen, quickOpen, ticket, cart.length]);

  /** Agrega al carrito un artículo libre capturado en el modal "Producto rápido". */
  const addQuickItem = useCallback((item: { description: string; unitPrice: number; unit: string; taxRate: number }) => {
    // El artículo libre entra con cantidad 1; el cajero la ajusta en el carrito con − / +.
    addLine({
      id: crypto.randomUUID(), productId: null, categoryId: null,
      description: item.description.trim(), quantity: 1,
      unitPrice: item.unitPrice, discount: 0, taxRate: item.taxRate, unit: item.unit, free: true,
    });
    setQuickOpen(false);
    setMessage(null);
    barcodeRef.current?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
        discount: promoDiscountOf(l),
        unit: l.unit ?? null,
        // Merma de panadería: el renglón viaja con waste=true. El precio de merma es FIJO en el
        // artículo; el backend lo aplica y descuenta de la existencia de merma (no del stock normal).
        waste: l.waste ? true : null,
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
        lineTotal: l.quantity * l.unitPrice - promoDiscountOf(l),
      })),
      total,
      methodLabel,
      received: onCredit ? paid : received,
      change,
    };

    let ticketData: TicketData;
    try {
      const { data } = await api.post<{ saleId: number }>('/sales', payload);
      ticketData = { ...ticketBase, folio: data?.saleId ? `V-${data.saleId}` : idempotencyKey.slice(-6), offline: false };
    } catch (err) {
      const ax = err as AxiosError<InsufficientStock>;
      const problem = ax.response?.data;
      // 409 INSUFFICIENT_STOCK: el backend NO guardó la venta (rollback). No se encola offline:
      // es un rechazo de negocio. Se avisa al cajero y se topa la cantidad vendible a 'available'.
      if (ax.response?.status === 409 && problem?.title === 'INSUFFICIENT_STOCK') {
        const available = problem.available ?? 0;
        const name = problem.productName
          ?? (problem.productId != null
            ? cart.find((l) => l.productId === problem.productId)?.description
            : undefined)
          ?? 'el producto';
        setCart((prev) => prev.map((l) =>
          (problem.productId != null && l.productId === problem.productId)
            ? { ...l, quantity: Math.max(1, Math.floor(available)) }
            : l));
        toast.error(
          'Sin existencia suficiente',
          `Solo hay ${available} de ${name}. Ajustamos la cantidad a lo disponible.`,
        );
        return; // la venta no se registró; el cajero reintenta con la cantidad topada
      }
      // Cualquier otro error (sin conexión): se encola para sincronizar más tarde.
      queueSale(payload);
      setPending(pendingCount());
      ticketData = { ...ticketBase, folio: idempotencyKey.slice(-6), offline: true };
      toast.info('Venta guardada sin conexión', 'Se sincronizará automáticamente al reconectar.');
    }

    // Muestra el modal post-venta y lanza la impresión DIRECTA por el agente (sin diálogo). El
    // modal reacciona al resultado: 'printed' (impreso) o 'no-printer' (sin impresora -> queda
    // para reimprimir). La venta ya está guardada pase lo que pase con la impresión.
    setTicket(ticketData);
    setPrintState('printing');
    clearCart();
    setCustomer(null);
    setCheckoutOpen(false);
    barcodeRef.current?.focus();

    void printSale(ticketSettings, ticketData).then((result) => setPrintState(result));
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
          <button
            type="button"
            className="pos-quick-btn"
            onClick={() => setQuickOpen(true)}
            aria-label="Agregar producto rápido (venta aislada)"
            title="Producto rápido: agrega un artículo que no está en el catálogo (F7)"
          >
            <Sparkles size={16} /> Producto rápido <kbd className="pos-quick-kbd">F7</kbd>
          </button>
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
            const shownPrice = effectivePrice(p);
            const esEspecial = specialPrices[p.id] != null;
            return (
              <motion.button key={p.id} className="pos-tile" onClick={() => setQtyModalProduct(p)} title={p.name}
                variants={staggerItem} whileHover={{ y: -3 }} whileTap={{ scale: 0.96 }} transition={springSoft}>
                {esEspecial && <span className="pos-tile-special"><Tag size={11} /> Precio cliente</span>}
                {!esEspecial && p.extras?.onSale && <span className="pos-tile-sale">Oferta</span>}
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
                : search.trim() !== ''
                  ? 'Ningún producto coincide con la búsqueda.'
                  : bakeryEnabled
                    ? 'No hay pan con existencia. Registra la producción del día en Inventario → Entradas para poder vender.'
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
            {cart.map((l) => {
              const promoLabel = l.productId != null ? promoResult.labelByProduct[l.productId] : undefined;
              const promoDisc = l.productId != null ? (promoResult.discountByProduct[l.productId] || 0) : 0;
              // En giro panadería, un renglón de catálogo puede venderse como merma solo si TIENE
              // precio de merma (>0) Y EXISTENCIA en merma (>0): "no se vende lo que no hay"
              // aplicado a la merma. Sin piezas en merma, el botón no aparece.
              const wasteCap = l.productId != null ? Math.max(0, Math.floor(wasteStockMap[l.productId] ?? 0)) : 0;
              const canSellAsWaste = bakeryEnabled && l.productId != null && (l.wastePrice ?? 0) > 0 && wasteCap > 0;
              // Chip "máx. X": existencia disponible del renglón (giro panadería, no merma). Guía
              // al cajero sobre cuántas piezas puede vender y resalta en ámbar si ya topó.
              const cap = stockCapOf(l);
              const atCap = cap != null && l.quantity >= cap;
              return (
                <motion.div key={l.id} className="pos-line" layout
                  initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 20, height: 0 }} transition={springSoft}>
                  <span className="pos-line-avatar" style={{ background: colorFor(l.description) }}>
                    {initials(l.description)}
                  </span>
                  <div className="pos-line-info">
                    <div className="pos-line-name">
                      {l.free ? (
                        <span className="pos-line-free"><Sparkles size={11} /> especial{l.unit ? ` · ${l.unit}` : ''}</span>
                      ) : (
                        <span className="pos-line-code">#{l.productId}</span>
                      )}{' '}
                      {l.description}
                    </div>
                    <div className="pos-line-price">
                      <input className="pos-line-price-input" type="number" min={0} step="0.01"
                        value={l.unitPrice} disabled={l.waste}
                        title={l.waste ? 'Precio de merma fijo del artículo (no editable)' : undefined}
                        onChange={(e) => setUnitPrice(l.id, Number(e.target.value) || 0)} />
                      <span> c/u</span>
                    </div>
                    {promoLabel && !l.waste && (
                      <span className="pos-line-promo">
                        <Tag size={11} /> {promoLabel} · -{money(promoDisc)}
                      </span>
                    )}
                    {cap != null && (
                      <span className={`pos-line-stock ${atCap ? 'is-max' : ''}`}
                        title="Existencia disponible de este artículo en la sucursal">
                        máx. {cap}
                      </span>
                    )}
                    {canSellAsWaste && (
                      <button
                        type="button"
                        className={`pos-line-waste ${l.waste ? 'is-active' : ''}`}
                        onClick={() => toggleWaste(l.id)}
                        title={l.waste
                          ? 'Vendiendo como merma al precio de merma del artículo. Toca para quitar.'
                          : `Vender como merma a ${money(l.wastePrice ?? 0)} (precio fijo del artículo).`}
                      >
                        <Trash2 size={11} /> {l.waste
                          ? `Merma · ${money(l.wastePrice ?? 0)}`
                          : 'Vender como merma'}
                      </button>
                    )}
                  </div>
                  <div className="pos-qty">
                    <button onClick={() => setQuantity(l.id, l.quantity - 1)} aria-label="Menos"
                      disabled={l.quantity <= 1}>
                      <Minus size={14} />
                    </button>
                    <input
                      value={l.quantity}
                      onChange={(e) => setQuantity(l.id, Number(e.target.value) || 1)}
                      inputMode="numeric"
                    />
                    <button onClick={() => setQuantity(l.id, l.quantity + 1)} aria-label="Más"
                      disabled={atCap}
                      title={atCap ? `Solo hay ${cap} ${cap === 1 ? 'pieza' : 'piezas'} de ${l.description}` : undefined}>
                      <Plus size={14} />
                    </button>
                  </div>
                  <div className="pos-line-total">{money(l.quantity * l.unitPrice - promoDisc)}</div>
                  <button className="pos-line-remove" onClick={() => removeLine(l.id)} aria-label="Quitar">
                    <X size={15} />
                  </button>
                </motion.div>
              );
            })}
          </AnimatePresence >
          {
            cart.length === 0 && (
              <div className="pos-empty">
                <div className="pos-empty-icon"><ShoppingCart size={44} strokeWidth={1.5} /></div>
                <p>Agrega productos tocando la cuadrícula o escaneando su código.</p>
              </div>
            )
          }
        </div >

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
            disabled={cart.length === 0 || !shift || hasOverStockLine}
            onClick={openCheckout}
            title={!shift
              ? 'Abre tu caja para poder cobrar'
              : hasOverStockLine ? 'Hay un artículo por encima de su existencia. Ajusta la cantidad.' : undefined}
          >
            {!shift
              ? 'Abre tu caja para vender'
              : hasOverStockLine
                ? 'Ajusta la cantidad para cobrar'
                : <>Cobrar {money(total)} <kbd className="pos-pay-kbd">F9 / Enter</kbd></>}
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
      </aside >

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
        {quickOpen && (
          <QuickItemModal
            onCancel={() => setQuickOpen(false)}
            onConfirm={addQuickItem}
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
        {qtyModalProduct && (
          <QuantityModal
            product={qtyModalProduct}
            price={effectivePrice(qtyModalProduct)}
            maxQuantity={stockCapForProduct(qtyModalProduct.id)}
            onCancel={() => setQtyModalProduct(null)}
            onConfirm={(qty) => {
              addProduct(qtyModalProduct, qty);
              setQtyModalProduct(null);
            }}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {ticket && (
          <motion.div className="pos-modal-overlay" onClick={cerrarTicket}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
            <motion.div className="ticket-modal pos-sale-done" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97 }} transition={springSoft}>

              {/* Cabecera: cambio a entregar en grande (lo que importa al cajero en el momento) */}
              <div className="pos-done-hero">
                <button className="pos-done-close" onClick={cerrarTicket} aria-label="Cerrar"><X size={18} /></button>
                <span className="pos-done-badge">✓ Venta registrada · {ticket.folio}</span>
                {ticket.change != null && ticket.change > 0 ? (
                  <>
                    <span className="pos-done-change-label">Cambio a entregar</span>
                    <strong className="pos-done-change">{money(ticket.change)}</strong>
                  </>
                ) : (
                  <>
                    <span className="pos-done-change-label">Total cobrado</span>
                    <strong className="pos-done-change pos-done-change-ok">{money(ticket.total)}</strong>
                    <span className="pos-done-change-sub">Pago exacto, sin cambio</span>
                  </>
                )}
              </div>

              {/* Banda de estado de impresión directa */}
              <div className={`pos-done-print pos-done-print-${printState ?? 'printing'}`}>
                {printState === 'printing' && (
                  <><RefreshCw size={15} className="pos-done-spin" /> Imprimiendo ticket…</>
                )}
                {printState === 'printed' && (
                  <><TicketIcon size={15} /> Ticket impreso</>
                )}
                {printState === 'no-printer' && (
                  <><TicketIcon size={15} /> Sin impresora. La venta quedó guardada: puedes reimprimir el ticket desde <strong>Reimpresión</strong> cuando la impresora esté lista.</>
                )}
              </div>

              <div className="ticket-modal-body">
                <Ticket settings={ticketSettings} data={ticket} />
              </div>

              <div className="ticket-actions">
                <button className="pos-confirm pos-done-next" onClick={cerrarTicket}>
                  Siguiente venta <span className="pos-key-hint">Enter</span>
                </button>
                <button className="btn-ghost" onClick={reimprimirTicket} disabled={printState === 'printing'}>
                  <TicketIcon size={16} /> Reimprimir <span className="pos-key-hint">P</span>
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div >
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

/**
 * Modal de CANTIDAD: al tocar un producto en la cuadrícula, el cajero confirma cuántas piezas
 * agregar. Mínimo 1 (nunca 0; para quitar se usa el botón Eliminar del carrito). Botones −/+ a los
 * lados y campo editable en medio. Enter confirma.
 */
function QuantityModal({ product, price, maxQuantity, onCancel, onConfirm }:
  {
    product: Product; price: number; maxQuantity: number | null;
    onCancel: () => void; onConfirm: (qty: number) => void
  }) {
  // La cantidad se maneja como TEXTO para permitir el campo vacío mientras el cajero teclea.
  // El mínimo 1 se aplica solo al CONFIRMAR, no en cada tecla (así no "rebota" a 1).
  const [qtyText, setQtyText] = useState('1');
  const inputRef = useRef<HTMLInputElement>(null);
  const qtyNum = Number(qtyText) || 0;
  // Candado por existencia ("no se vende lo que no hay"): el modal no deja pedir más piezas de las
  // que hay en la sucursal. maxQuantity es null fuera del giro panadería (sin tope).
  const hasCap = maxQuantity != null;
  const cap = maxQuantity ?? Infinity;
  const overCap = hasCap && qtyNum > cap;
  const confirmQty = Math.max(1, Math.min(Math.floor(Number(qtyText) || 1), hasCap ? cap : Infinity));

  useEffect(() => {
    // Selecciona el valor al abrir para que el cajero pueda teclear la cantidad de inmediato.
    const t = setTimeout(() => inputRef.current?.select(), 60);
    return () => clearTimeout(t);
  }, []);

  const dec = () => setQtyText(String(Math.max(1, (Number(qtyText) || 1) - 1)));
  // "+" no pasa del tope de existencia.
  const inc = () => setQtyText(String(Math.min((Number(qtyText) || 0) + 1, hasCap ? cap : Infinity)));
  // Al confirmar: mínimo 1 (si quedó vacío o 0, se toma 1) y máximo la existencia disponible.
  const confirmar = () => onConfirm(confirmQty);

  const money2 = (n: number) =>
    new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

  return (
    <motion.div className="pos-modal-overlay" onClick={onCancel}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <motion.div className="pos-modal pos-qty-modal" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.94, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: 8 }} transition={{ duration: 0.16 }}
        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); confirmar(); } }}>
        <div className="pos-modal-head">
          <h3>{product.name}</h3>
          <button className="pos-modal-close" onClick={onCancel} aria-label="Cerrar"><X size={18} /></button>
        </div>
        <div className="pos-qty-modal-body">
          <div className="pos-qty-modal-price">{money2(price)} <span>c/u</span></div>
          {hasCap && (
            <span className={`pos-qty-modal-stock ${overCap ? 'is-max' : ''}`}>
              máx. {cap} {cap === 1 ? 'pieza disponible' : 'piezas disponibles'}
            </span>
          )}
          <div className="pos-qty-modal-stepper">
            <button className="pos-qty-modal-btn" onClick={dec} disabled={qtyNum <= 1} aria-label="Menos">
              <Minus size={22} />
            </button>
            <input
              ref={inputRef}
              className="pos-qty-modal-input"
              value={qtyText}
              inputMode="numeric"
              onFocus={(e) => e.target.select()}
              onChange={(e) => {
                // Solo dígitos. Permite vacío (el cajero puede borrar y teclear de nuevo).
                // Quita ceros a la izquierda para que no quede "05" ni empiece en 0.
                let v = e.target.value.replace(/[^\d]/g, '');
                v = v.replace(/^0+/, '');
                setQtyText(v);
              }}
              onBlur={() => {
                if (qtyText === '') { setQtyText('1'); return; }
                // Al salir del campo, topa a la existencia disponible (candado por existencia).
                if (hasCap && (Number(qtyText) || 0) > cap) setQtyText(String(cap));
              }}
              aria-label="Cantidad"
            />
            <button className="pos-qty-modal-btn" onClick={inc} aria-label="Más"
              disabled={hasCap && qtyNum >= cap}>
              <Plus size={22} />
            </button>
          </div>
          {overCap && (
            <div className="pos-qty-modal-warn">
              Solo hay {cap} {cap === 1 ? 'pieza' : 'piezas'} de {product.name}. Se agregará el máximo disponible.
            </div>
          )}
          <div className="pos-qty-modal-total">Importe: <strong>{money2(price * confirmQty)}</strong></div>
        </div>
        <div className="pos-modal-actions">
          <button className="btn-ghost" onClick={onCancel}>Cancelar</button>
          <button className="pos-confirm" onClick={confirmar}>
            Agregar {confirmQty} {confirmQty === 1 ? 'pieza' : 'piezas'}
          </button>
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

/** Unidades de medida para artículos de panadería capturados con Producto rápido. */
const QUICK_UNITS = ['pieza', 'kg', 'lt', 'bolsa', 'costal', 'docena'] as const;
type QuickUnit = typeof QUICK_UNITS[number];

/**
 * Modal "Producto rápido" (venta especial): captura un artículo que no está en el catálogo
 * (harina, leche, azúcar y demás ventas esporádicas de la panadería) para venderlo al instante.
 * El precio es con IVA incluido (igual que el resto del POS); el IVA es informativo y NO suma al
 * total. La cantidad NO se captura aquí: el renglón entra con 1 y se ajusta en el carrito. La
 * venta que incluya un artículo así queda clasificada como "especial" para el corte del admin.
 */
function QuickItemModal({ onCancel, onConfirm }:
  {
    onCancel: () => void;
    onConfirm: (item: { description: string; unitPrice: number; unit: QuickUnit; taxRate: number }) => void;
  }) {
  const [description, setDescription] = useState('');
  const [priceInput, setPriceInput] = useState('');
  const [unit, setUnit] = useState<QuickUnit>('pieza');
  // Panadería: exento por defecto. El 16% queda disponible para otros giros.
  const [taxRate, setTaxRate] = useState(0);

  const unitPrice = round2(Number(priceInput) || 0);
  const canConfirm = description.trim().length > 0 && unitPrice > 0;

  const confirm = () => {
    if (!canConfirm) return;
    onConfirm({ description: description.trim(), unitPrice, unit, taxRate });
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter') { e.preventDefault(); confirm(); }
      else if (e.key === 'Escape') { e.preventDefault(); onCancel(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canConfirm, description, priceInput, unit, taxRate]);

  return (
    <motion.div className="pos-modal-overlay" onClick={onCancel}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-quick-modal" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 14 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={springSoft}>

        {/* Encabezado con sello de venta especial */}
        <div className="pos-quick-hero">
          <span className="pos-quick-hero-badge"><Sparkles size={14} /> Venta especial</span>
          <h3 className="pos-quick-hero-title">Producto rápido</h3>
          <p className="pos-quick-hero-sub">Artículo fuera del catálogo (harina, leche, azúcar…). Queda registrado aparte en el corte.</p>
          <button className="pos-quick-close" onClick={onCancel} aria-label="Cerrar"><X size={18} /></button>
        </div>

        <div className="pos-quick-body">
          {/* Descripción */}
          <label className="pos-quick-field">
            <span className="pos-quick-label">Descripción del artículo</span>
            <input className="pos-quick-input" type="text" value={description} autoFocus maxLength={120}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Ej. Harina de trigo 1 kg, Leche entera 1 L…" />
          </label>

          {/* Unidad de medida (chips) */}
          <div className="pos-quick-field">
            <span className="pos-quick-label">Unidad de medida</span>
            <div className="pos-quick-units">
              {QUICK_UNITS.map((u) => (
                <button type="button" key={u}
                  className={`pos-quick-unit ${unit === u ? 'is-active' : ''}`}
                  onClick={() => setUnit(u)}>
                  {u}
                </button>
              ))}
            </div>
          </div>

          {/* Precio + IVA */}
          <div className="pos-quick-grid">
            <label className="pos-quick-field">
              <span className="pos-quick-label">Precio unitario</span>
              <div className="pos-quick-price">
                <span className="pos-quick-price-symbol">$</span>
                <input className="pos-quick-input" type="number" min={0} step="0.01"
                  value={priceInput} placeholder="0.00"
                  onChange={(e) => setPriceInput(e.target.value)} />
              </div>
            </label>
            <div className="pos-quick-field">
              <span className="pos-quick-label">IVA</span>
              <div className="pos-quick-tax">
                <button type="button" className={`pos-quick-tax-btn ${taxRate === 0 ? 'is-active' : ''}`}
                  onClick={() => setTaxRate(0)}>Exento 0%</button>
                <button type="button" className={`pos-quick-tax-btn ${taxRate === 0.16 ? 'is-active' : ''}`}
                  onClick={() => setTaxRate(0.16)}>IVA 16%</button>
              </div>
            </div>
          </div>

          <p className="pos-quick-note">Precio con IVA incluido. El IVA es informativo y no se suma al total.</p>

          {/* Resumen del renglón */}
          <div className="pos-quick-summary">
            <div className="pos-quick-summary-left">
              <span className="pos-quick-summary-label">Se agrega 1 {unit}</span>
              <span className="pos-quick-summary-hint">Ajusta la cantidad después en el carrito</span>
            </div>
            <strong className="pos-quick-summary-amount">{money(unitPrice)}</strong>
          </div>
        </div>

        <div className="pos-quick-actions">
          <button className="btn-ghost" onClick={onCancel}>Cancelar</button>
          <button className="pos-confirm" disabled={!canConfirm} onClick={confirm}>
            <Plus size={15} /> Agregar al carrito <span className="pos-key-hint">Enter</span>
          </button>
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
