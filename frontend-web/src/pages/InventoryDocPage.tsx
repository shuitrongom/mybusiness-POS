import { useEffect, useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { ArrowDownToLine, ArrowUpFromLine, Plus, Minus, Trash2, Check, PackageOpen, TriangleAlert, Calendar } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { useSession } from '@/store/session';
import { decodeToken, hasModule } from '@/lib/jwt';
import { ProductPicker, type PickerProduct } from '@/components/ProductPicker';
import { InfoBanner } from '@/components/InfoBanner';
import { resolveCashierBranchId } from '@/lib/cashierBranch';
import '@/pages/dashboard.css';
import '@/pages/inventory-doc.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

// Fecha de HOY como YYYY-MM-DD (día local). Copiado LITERAL de BakeryDayStockPage para que el
// selector de día del historial se comporte igual que el de Existencias del día; se deja duplicado
// a propósito (no extraer a util) para no acoplar dos pantallas por un helper trivial.
function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

// Etiqueta legible de un día YYYY-MM-DD (ej. "24/10/2026") para los estados vacíos de días pasados.
function dayLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return iso;
  return new Date(y, m - 1, d).toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

// Fecha + hora local (ej. "16/10/2026 · 09:52 a.m."). Usa createdAt (instante exacto del registro).
function dateTime(iso: string | null): { date: string; time: string } {
  if (!iso) return { date: '—', time: '' };
  const d = new Date(iso);
  if (isNaN(d.getTime())) return { date: '—', time: '' };
  return {
    date: d.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' }),
    time: d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }),
  };
}

// Avatar de artículo: color estable e iniciales a partir del nombre (acabado premium del renglón).
const AVATAR_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9', '#d97706', '#0891b2'];
function avatarColor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}
function avatarInitials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  if (w.length === 1) return w[0].slice(0, 2).toUpperCase();
  return (w[0][0] + w[1][0]).toUpperCase();
}

interface Concept { id: number; name: string; direction: string; }
interface Product { id: number; name: string; price: number; cost: number; unit: string; }
interface Branch { id: number; name: string; active: boolean; }
// Origen del renglón: pan del día (stock normal) o merma. Solo el reparto mixto combina ambos; en
// los demás modos el renglón nace con un source constante ('NORMAL').
type LineSource = 'NORMAL' | 'WASTE';
interface Line { productId: number; source: LineSource; description: string; quantity: number; unitCost: number; entryPrice: number; unit: string; }
interface DocRow {
  id: number; folio: string; conceptName: string | null; branchName: string | null;
  totalQty: number; totalValue: number; createdBy: string | null; createdAt: string;
}
/**
 * Tipos de movimiento mostrables en el historial unificado.
 *
 * FRONTERA DE DOMINIO (MEDIUM-3): los valores NORMAL|MANUAL|CIERRE|ENVEJECIMIENTO|REPARTO
 * llegan del backend de /exits (= columna `kind` de listUnifiedExits). 'PRODUCCION' es
 * SINTÉTICO del front: el endpoint /productions NO devuelve `kind`; el front lo fija
 * literalmente para pintar el chip "Producción" en Entradas. No esperar 'PRODUCCION'
 * desde /exits ni un `kind` desde /productions.
 */
type MovementKind =
  | 'NORMAL'          // salida normal de inventario (serie S-)        [de /exits]
  | 'MANUAL'          // paso a merma manual (serie M-)                [de /exits]
  | 'CIERRE'          // sobrante a merma al cerrar caja (serie M-)    [de /exits]
  | 'ENVEJECIMIENTO'  // pan del día anterior a merma (serie M-)       [de /exits]
  | 'REPARTO'         // merma entregada a repartidor (serie M-)       [de /exits]
  | 'PRODUCCION';     // entrada / producción de pan (serie PROD-)     [sintético front]

/**
 * Fila del historial UNIFICADO de "Salidas recientes" de panadería (GET /bakery/exits):
 * salida normal (su concepto real) + merma MANUAL ("Paso a merma") + REPARTO + CIERRE +
 * ENVEJECIMIENTO. El backend ya resuelve el tipo, el folio (serie S- / M-) y el orden; el
 * front solo renderiza.
 */
interface ExitRow {
  id: string;            // 'DOC-12' | 'WASTE-7' (clave estable única entre fuentes)
  kind: MovementKind;
  concept: string;       // 'Salida' | 'Paso a merma' | 'Reparto de merma' | concepto real
  folio: string | null;
  branchName: string | null;
  totalQty: number;
  totalValue: number;
  cashier: string | null;
  createdAt: string;     // instante ISO
  // Artículo(s) de la salida (resueltos en el backend): nombre si es 1 solo artículo, null si son
  // varios/0; productCount = número de artículos distintos; itemsLabel = texto premium listo para
  // pintar ("Concha" | "3 artículos" | "—").
  productName: string | null;
  productCount: number;
  itemsLabel: string;
}

// Etiqueta CORTA del chip (premium, sin saturar la columna). Steering: ámbar = merma.
const KIND_LABEL: Record<MovementKind, string> = {
  NORMAL: 'Salida',
  MANUAL: 'Paso a merma',
  CIERRE: 'Sobrante',
  ENVEJECIMIENTO: 'Envejecido',
  REPARTO: 'Reparto',
  PRODUCCION: 'Producción',
};

/**
 * Color estable por tipo, texto BLANCO (ver .idoc-hist-chip). Steering: ámbar = merma.
 * Paleta corregida por contraste WCAG AA (ratios ≥ 4.5:1 de blanco sobre el fondo):
 * MANUAL/CIERRE #b45309 (≈5.3:1), ENVEJECIMIENTO #92400e (≈7.0:1), REPARTO #c2410c (≈4.6:1),
 * PRODUCCION #0a7a56 (≈4.7:1, verde de marca oscurecido, NO colisiona con AVATAR_COLORS),
 * neutro #64748b (≈4.8:1). Ver design.md §4 (NIT-2/NIT-3).
 */
function kindColor(kind: MovementKind): string {
  switch (kind) {
    case 'MANUAL': return '#b45309';         // ámbar oscuro (merma manual)
    case 'CIERRE': return '#b45309';         // ámbar oscuro (sobrante fin de día)
    case 'ENVEJECIMIENTO': return '#92400e'; // ámbar más oscuro (envejecido)
    case 'REPARTO': return '#c2410c';        // naranja oscuro (reparto de merma)
    case 'PRODUCCION': return '#0a7a56';     // verde de marca oscuro (producción)
    default: return '#64748b';               // neutro gris (salida normal / desconocido)
  }
}

/**
 * Fallback de etiqueta (MEDIUM-3): si llegara un `kind` fuera del dominio conocido (p. ej. un
 * nuevo origin del CHECK), NO dejar el chip vacío (eso reproduciría el hueco '—'). Mostrar
 * 'Salida' genérico; el color ya cae a neutro por el default del switch.
 */
function kindLabel(kind: string): string {
  return (KIND_LABEL as Record<string, string>)[kind] ?? 'Salida';
}

/** Fila por renglón de GET /bakery/productions (historial de producción, homologado a Salidas). */
interface ProductionRow {
  productionId: number;
  folio: string | null;         // PROD-
  statusCode: string | null;    // para el concepto (hoy siempre PRODUCED)
  branchName: string | null;    // ALMACÉN (JOIN a branch)
  productName: string | null;
  description: string | null;
  quantity: number | null;
  unit: string | null;
  entryPrice: number | null;    // PRECIO
  lineTotal: number | null;     // IMPORTE
  createdBy: string | null;     // REGISTRÓ (ya venía, no se pintaba)
  productionDate: string | null;
  createdAt: string | null;     // ISO (OffsetDateTime) — igual que Salidas
}

// Grid ÚNICO compartido por cabecera y filas de AMBAS tablas (Entradas y Salidas), para que las
// columnas cuadren por construcción (steering). 9 columnas; la última (REGISTRÓ) ≥160px porque
// aloja usuario + fecha/hora en dos líneas. Ver design.md §5.0.
const histCols =
  '132px 96px minmax(130px,1fr) minmax(150px,1.1fr) 110px 84px 96px 110px 160px';
//  TIPO  FOLIO  CONCEPTO        ARTÍCULO           ALMACÉN CANT.  PRECIO IMPORTE REGISTRÓ

/**
 * Celda REGISTRÓ de dos líneas (usuario + fecha/hora) compartida por ambas tablas: el nombre con
 * ellipsis + title (el email largo se ve completo al pasar el mouse) y debajo la fecha/hora tenue.
 * design.md §5.0 (HIGH-1).
 */
function RegisteredCell({ who, createdAt }: { who: string; createdAt: string | null }) {
  const dt = dateTime(createdAt);
  return (
    <span className="idoc-hist-when">
      <span className="idoc-hist-when-who" title={who}>{who}</span>
      <span className="idoc-hist-when-date">{dt.date}</span>
      {dt.time && <span className="idoc-hist-when-time">{dt.time}</span>}
    </span>
  );
}

/**
 * Documento de ENTRADA o SALIDA de inventario. Reutilizable: recibe el tipo (IN/OUT). Elige
 * concepto (motivo), almacén y captura renglones. Al finalizar afecta las existencias.
 */
export function InventoryDocPage({ docType }: { docType: 'IN' | 'OUT' }) {
  const isIn = docType === 'IN';
  // En giro panadería, la ENTRADA de inventario es la PRODUCCIÓN del día: captura precio de entrada
  // por renglón, estatus fijo "Producción" (sin selector) y envía a POST /bakery/productions.
  const isBakery = hasModule(decodeToken(useSession((s) => s.token)), 'bakery');
  const isProduction = isIn && isBakery;

  // En giro panadería, la SALIDA ofrece además dos acciones de merma: pasar pan del stock normal
  // a merma (MANUAL) o sacar merma hacia un repartidor (REPARTO). 'NORMAL' = salida genérica.
  type ExitMode = 'NORMAL' | 'MANUAL' | 'REPARTO';
  const [exitMode, setExitMode] = useState<ExitMode>('NORMAL');
  // Solo MANUAL (merma pura) es salida SIN columna de dinero. REPARTO ya NO es isWasteExit: muestra
  // precio bloqueado por origen y particiona el submit por renglón (Decisión 4-I).
  const isWasteExit = !isIn && isBakery && exitMode === 'MANUAL';
  // Precio BLOQUEADO (lo manda la ficha del artículo, no la operación): producción, salida normal
  // de pan y reparto. Definición única compartida por las Decisiones 2-C y 4-I.
  const fixedPrice = isProduction || (!isIn && isBakery && (exitMode === 'NORMAL' || exitMode === 'REPARTO'));
  const [concepts, setConcepts] = useState<Concept[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [conceptId, setConceptId] = useState<number>(0);
  const [branchId, setBranchId] = useState<number>(0);
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [history, setHistory] = useState<DocRow[]>([]);
  const [prodHistory, setProdHistory] = useState<ProductionRow[]>([]);
  // Historial UNIFICADO de salidas de panadería (salida normal + merma MANUAL/REPARTO).
  const [exits, setExits] = useState<ExitRow[]>([]);
  const [busy, setBusy] = useState(false);
  // Día del historial (default HOY). El selector permite ver días anteriores; loadHistory manda
  // from=to=day a /productions y /exits para que solo se vean los movimientos de ESE día.
  const [day, setDay] = useState<string>(todayIso());
  // Productos CON EXISTENCIA para el selector de Salidas (solo lo que tiene inventario). En reparto
  // mixto, cada entrada lleva su origen (source) y su precio por origen.
  const [availableProducts, setAvailableProducts] = useState<PickerProduct[]>([]);
  // Existencia disponible por artículo y ORIGEN, con clave compuesta `${source}:${productId}`
  // (availByKey["NORMAL:12"] = stock normal; availByKey["WASTE:12"] = merma). Fuente ÚNICA de
  // disponible en todos los modos (un solo mapa, sin estados por-origen separados ni fetch
  // redundante por renglón).
  const [availByKey, setAvailByKey] = useState<Record<string, number>>({});
  // Estado de la carga del selector de Salidas: distingue "no hay existencia" (vacío legítimo)
  // de "la carga falló" (error con reintento). Antes el .catch dejaba la lista vacía y ambos
  // casos se veían igual.
  const [availableError, setAvailableError] = useState(false);
  // Fuerza recargar los artículos con existencia al pulsar "Reintentar".
  const [availableReload, setAvailableReload] = useState(0);
  // Nombres de artículos sin precio que bloquean la producción (dispara el modal de aviso).
  const [missingPrice, setMissingPrice] = useState<string[]>([]);

  // SALIDA en panadería: historial UNIFICADO (salida normal + merma MANUAL/REPARTO) desde el
  // endpoint nuevo. El backend une, ordena y limita; aquí solo se renderiza.
  const isBakeryExit = !isIn && isBakery;

  const loadHistory = () => {
    if (isProduction) {
      // Guard igual que Salidas: no llamar hasta resolver branchId; la lista queda vacía
      // (estado-vacío premium) mientras tanto. Homologado con listUnifiedExits (NIT-4).
      if (!branchId) { setProdHistory([]); return; }
      api.get<ProductionRow[]>('/bakery/productions', { params: { branchId, from: day, to: day, limit: 200 } })
        .then((r) => setProdHistory(r.data)).catch(() => setProdHistory([]));
      return;
    }
    if (isBakeryExit) {
      if (!branchId) { setExits([]); return; }
      api.get<ExitRow[]>('/bakery/exits', { params: { branchId, from: day, to: day, limit: 200 } })
        .then((r) => setExits(r.data)).catch(() => setExits([]));
      return;
    }
    api.get<DocRow[]>('/inventory/documents', { params: { docType, limit: 15 } })
      .then((r) => setHistory(r.data)).catch(() => setHistory([]));
  };

  useEffect(() => {
    (async () => {
      try {
        const [con, prod, br] = await Promise.all([
          api.get<Concept[]>('/inventory/concepts', { params: { direction: docType } }),
          api.get<Product[]>('/catalog/products'),
          api.get<Branch[]>('/branches'),
        ]);
        setConcepts(con.data);
        setProducts(prod.data);
        const active = br.data.filter((b) => b.active);
        setBranches(active);
        // Fuente ÚNICA del branch del cajero (turno abierto > última usada en POS > primera
        // activa), para que Producción/Salidas operen en el MISMO almacén que el POS y no se
        // produzca en una sucursal y se venda en otra.
        const resolved = resolveCashierBranchId(active);
        if (resolved) setBranchId(resolved);
      } catch { toast.error('No se pudo cargar el catálogo'); }
    })();
    loadHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docType]);

  // En SALIDA de panadería el historial depende de la sucursal (el endpoint filtra por branchId),
  // que se resuelve async al cargar sucursales. Recarga cuando cambie branchId SOLO en este modo
  // (los demás modos no dependen de branchId, así que no se recargan de más).
  useEffect(() => {
    if ((isBakeryExit || isProduction) && branchId) loadHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isBakeryExit, isProduction, branchId, day]);

  // SALIDAS en giro panadería: el selector solo muestra panes CON EXISTENCIA. En REPARTO carga
  // AMBOS inventarios (pan del día + merma) etiquetados por origen; en los demás modos, una sola
  // lista de stock normal. availByKey es la fuente ÚNICA de disponible por clave compuesta.
  useEffect(() => {
    if (isIn || !isBakery || !branchId) { setAvailableProducts([]); setAvailByKey({}); setAvailableError(false); return; }
    let cancelled = false;
    type AvailRow = { productId: number; productName: string; unit: string | null; quantity: number; salePrice: number };
    if (exitMode === 'REPARTO') {
      Promise.all([
        api.get<AvailRow[]>('/bakery/stock/available', { params: { branchId, kind: 'normal' } }),
        api.get<AvailRow[]>('/bakery/stock/available', { params: { branchId, kind: 'waste' } }),
      ])
        .then(([norm, wst]) => {
          if (cancelled) return;
          const list: PickerProduct[] = [
            ...norm.data.map((x) => ({ id: x.productId, name: x.productName, unit: x.unit || 'pz', price: Number(x.salePrice) || 0, cost: 0, source: 'NORMAL' as const })),
            ...wst.data.map((x) => ({ id: x.productId, name: x.productName, unit: x.unit || 'pz', price: Number(x.salePrice) || 0, cost: 0, source: 'WASTE' as const })),
          ];
          setAvailableProducts(list);
          const avail: Record<string, number> = {};
          for (const x of norm.data) avail[`NORMAL:${x.productId}`] = Number(x.quantity) || 0;
          for (const x of wst.data) avail[`WASTE:${x.productId}`] = Number(x.quantity) || 0;
          setAvailByKey(avail);
          setAvailableError(false);
        })
        .catch(() => { if (!cancelled) { setAvailableProducts([]); setAvailByKey({}); setAvailableError(true); } });
    } else {
      api.get<AvailRow[]>('/bakery/stock/available', { params: { branchId, kind: 'normal' } })
        .then((r) => {
          if (cancelled) return;
          setAvailableProducts(r.data.map((x) => ({
            id: x.productId, name: x.productName, unit: x.unit || 'pz', price: Number(x.salePrice) || 0, cost: 0, source: 'NORMAL' as const,
          })));
          const avail: Record<string, number> = {};
          for (const x of r.data) avail[`NORMAL:${x.productId}`] = Number(x.quantity) || 0;
          setAvailByKey(avail);
          setAvailableError(false);
        })
        // La carga falló: se marca el error para mostrar un aviso con reintento, en vez de dejar
        // el selector vacío como si no hubiera existencia.
        .catch(() => { if (!cancelled) { setAvailableProducts([]); setAvailByKey({}); setAvailableError(true); } });
    }
    return () => { cancelled = true; };
  }, [isIn, isBakery, branchId, exitMode, availableReload]);

  // Claves compuestas de los renglones ya agregados (un mismo pan puede estar por cada origen).
  const selectedIds = useMemo(() => new Set(lines.map((l) => `${l.source}:${l.productId}`)), [lines]);

  const add = (p: PickerProduct) => {
    const source: LineSource = p.source ?? 'NORMAL';
    const key = `${source}:${p.id}`;
    setLines((prev) => prev.some((l) => `${l.source}:${l.productId}` === key) ? prev
      // Nace sin cantidad (0 = placeholder "1"); el cajero la captura. Precio de venta prellenado
      // desde el producto (por origen); costo unitario desde su costo.
      : [...prev, { productId: p.id, source, description: p.name, quantity: 0, unitCost: p.cost || 0, entryPrice: p.price || 0, unit: p.unit || '' }]);
  };

  // Lista para el selector: en SALIDAS de panadería, solo panes con existencia; en el resto, catálogo.
  const pickerProducts = (!isIn && isBakery) ? availableProducts : products;
  const setLine = (i: number, patch: Partial<Line>) => setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));

  const totalUnits = lines.reduce((s, l) => s + l.quantity, 0);
  const totalValue = lines.reduce((s, l) => s + l.quantity * (fixedPrice ? l.entryPrice : l.unitCost), 0);

  const submit = async () => {
    if (lines.length === 0) { toast.info('Agrega al menos un producto'); return; }
    // Regla de negocio: no se da ENTRADA/PRODUCCIÓN a un artículo sin precio. El precio lo define
    // la ficha del producto; si está en 0, hay que capturarlo primero (modal), no registrar en 0.
    if (isProduction) {
      const sinPrecio = lines.filter((l) => !(l.entryPrice > 0)).map((l) => l.description);
      if (sinPrecio.length > 0) {
        setMissingPrice(sinPrecio);
        return;
      }
    }
    const sinCantidad = lines.find((l) => l.quantity < 1);
    if (sinCantidad) {
      toast.error('Falta la cantidad', `Captura la cantidad de «${sinCantidad.description}» (mínimo 1).`);
      return;
    }
    setBusy(true);
    try {
      // No se saca más de lo disponible (segunda barrera además del backend). Avisa cuál se pasa.
      // El disponible se lee SIEMPRE por clave compuesta (origen + producto) del mapa único.
      if (!isIn && isBakery) {
        const over = lines.find((l) => {
          const a = availByKey[`${l.source}:${l.productId}`];
          return a != null && l.quantity > a;
        });
        if (over) {
          const a = availByKey[`${over.source}:${over.productId}`];
          toast.error('Cantidad mayor a la disponible',
            `De «${over.description}» solo hay ${a ?? 0} disponible(s) en ${over.source === 'WASTE' ? 'merma' : 'el día'}. Ajusta la cantidad.`);
          setBusy(false);
          return;
        }
      }
      if (isWasteExit) {
        // Merma manual (del stock normal a merma). Un renglón por transferencia; el backend valida
        // la no-sobreventa del origen.
        for (const l of lines) {
          await api.post('/bakery/waste/transfer', {
            productId: l.productId, branchId, quantity: l.quantity, origin: 'MANUAL',
          });
        }
        toast.success('Pan pasado a merma', 'El pan seleccionado salió del stock normal y entró a merma.');
      } else if (!isIn && isBakery && exitMode === 'REPARTO') {
        // Reparto mixto: se particiona por origen de cada renglón. La pata de pan del día es UN
        // documento OUT con el concepto "Reparto a repartidor" (valuado a su precio normal); la
        // pata de merma es un transfer REPARTO por renglón. Cada pata descuenta su propio inventario.
        const repartoConceptId = concepts.find((c) => c.name === 'Reparto a repartidor')?.id;
        const normalLines = lines.filter((l) => l.source === 'NORMAL');
        const wasteLines = lines.filter((l) => l.source === 'WASTE');
        if (normalLines.length > 0 && !repartoConceptId) {
          toast.error('Falta el concepto de reparto', 'Se aplicará tras la actualización del sistema. Intenta de nuevo más tarde.');
          setBusy(false);
          return;
        }
        if (normalLines.length > 0) {
          await api.post('/inventory/documents', {
            docType: 'OUT', conceptId: repartoConceptId, branchId, notes: notes || null,
            lines: normalLines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unitCost: l.entryPrice })),
          });
        }
        for (const l of wasteLines) {
          await api.post('/bakery/waste/transfer', { productId: l.productId, branchId, quantity: l.quantity, origin: 'REPARTO' });
        }
        toast.success('Reparto registrado', 'Se descontó el pan del día y/o la merma entregada al repartidor.');
      } else if (isProduction) {
        await api.post('/bakery/productions', {
          branchId, statusCode: 'PRODUCED', notes: notes || null,
          lines: lines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unit: l.unit || null, entryPrice: l.entryPrice, unitCost: l.unitCost })),
        });
        toast.success('Producción registrada', 'Se actualizaron las existencias del día.');
      } else {
        // Salida NORMAL: para pan se envía unitCost = precio de la ficha (valuación explícita, Hueco
        // 2); para no-pan se conserva el costo unitario capturado.
        await api.post('/inventory/documents', {
          docType, conceptId: conceptId || null, branchId, notes: notes || null,
          lines: lines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unitCost: (!isIn && isBakery && exitMode === 'NORMAL') ? l.entryPrice : l.unitCost })),
        });
        toast.success(isIn ? 'Entrada registrada' : 'Salida registrada', 'Se actualizó el inventario.');
      }
      setLines([]); setNotes('');
      loadHistory();
    } catch (err) {
      // Mensaje de negocio del ProblemDetail 422 (p.ej. "Captura primero el precio del artículo…")
      // cuando exista; si no, el texto genérico de siempre.
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      toast.error(isProduction ? 'No se pudo registrar la producción' : 'No se pudo registrar el movimiento', detail || undefined);
    } finally { setBusy(false); }
  };

  const Icon = isIn ? ArrowDownToLine : ArrowUpFromLine;

  return (
    <div>
      <h1 className="page-title">{isProduction ? 'Entradas / Producción' : isIn ? 'Entradas al inventario' : 'Salidas al inventario'}</h1>
      <p className="page-sub">{isProduction ? 'Registra la producción de pan del día con su precio de entrada' : isIn ? 'Registra entradas de mercancía con su concepto' : isBakery ? 'Registra salidas: merma manual, reparto de merma o salida normal' : 'Registra salidas de mercancía con su concepto'}</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          {!isIn && isBakery && (
            <label className="field"><span>Tipo de salida</span>
              <select value={exitMode} onChange={(e) => setExitMode(e.target.value as ExitMode)}>
                <option value="MANUAL">Pasar a merma (manual)</option>
                <option value="REPARTO">Reparto a repartidor (día y/o merma)</option>
                <option value="NORMAL">Salida normal (con concepto)</option>
              </select></label>
          )}
          {!isProduction && !isWasteExit && (
            <label className="field"><span>Concepto</span>
              <select value={conceptId} onChange={(e) => setConceptId(Number(e.target.value))}>
                <option value={0}>Selecciona…</option>
                {/* "Reparto a repartidor" no se ofrece a mano (lo usa el flujo de reparto
                    automáticamente); el estado 'concepts' se conserva completo para resolverlo. */}
                {concepts.filter((c) => c.name !== 'Reparto a repartidor').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select></label>
          )}
          <label className="field"><span>Almacén / sucursal</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field" style={{ gridColumn: 'span 2' }}><span>Observaciones</span>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
        </div>

        {/* Banner de contexto: explica en una línea qué hace esta pantalla / modo. */}
        {isProduction && (
          <InfoBanner icon={<ArrowDownToLine size={18} />} tone="brand" title="Producción del día">
            Registra el pan que horneaste hoy. Suma la existencia vendible y toma el precio de venta
            de la ficha de cada artículo (no editable aquí).
          </InfoBanner>
        )}
        {isIn && !isBakery && (
          <InfoBanner icon={<ArrowDownToLine size={18} />} tone="brand" title="Entrada al inventario">
            Registra la entrada de mercancía con su concepto. Suma la existencia del almacén.
          </InfoBanner>
        )}
        {!isIn && isBakery && exitMode === 'MANUAL' && (
          <InfoBanner icon={<Trash2 size={18} />} tone="amber" title="Pasar pan a merma (manual)">
            El pan sale del stock vendible y entra a MERMA. Úsalo para pan del día anterior o dañado
            que ya no se vende a precio normal. Solo aparecen artículos con existencia.
          </InfoBanner>
        )}
        {!isIn && isBakery && exitMode === 'REPARTO' && (
          <InfoBanner icon={<ArrowUpFromLine size={18} />} tone="sky" title="Reparto a repartidor (pan del día y/o merma)">
            El repartidor puede llevar pan del día (a precio normal) y pan de merma (a precio de
            merma). Cada renglón se descuenta de su inventario.
          </InfoBanner>
        )}
        {!isIn && isBakery && exitMode === 'NORMAL' && (
          <InfoBanner icon={<PackageOpen size={18} />} tone="neutral" title="Salida normal de inventario">
            Salida con un concepto (consumo, ajuste, etc.). Descuenta del stock normal. Solo aparecen
            artículos con existencia.
          </InfoBanner>
        )}
        {!isIn && !isBakery && (
          <InfoBanner icon={<ArrowUpFromLine size={18} />} tone="neutral" title="Salida del inventario">
            Registra la salida de mercancía con su concepto. Descuenta la existencia del almacén.
          </InfoBanner>
        )}

        {/* ESTADO DE ERROR (distinto del vacío): la carga de artículos con existencia falló.
            Se avisa con reintento en vez de mostrar el selector vacío como si no hubiera pan. */}
        {!isIn && isBakery && availableError && (
          <div className="idoc-load-error" style={{ marginTop: 'var(--space-4)' }}>
            <TriangleAlert size={18} />
            <div className="idoc-load-error-text">
              <strong>No se pudieron cargar los artículos con existencia.</strong>
              <span> Es un error de carga, no significa que no haya inventario. Revisa tu conexión.</span>
            </div>
            <button type="button" className="idoc-load-error-retry" onClick={() => setAvailableReload((n) => n + 1)}>
              Reintentar
            </button>
          </div>
        )}

        <div className="pur-add" style={{ marginTop: 'var(--space-4)' }}>
          <ProductPicker
            products={pickerProducts}
            onPick={add}
            selectedIds={selectedIds}
            placeholder={isProduction ? 'Elegir pan a producir…'
              : exitMode === 'REPARTO' ? 'Elegir pan (día o merma)…'
                : isWasteExit ? 'Elegir pan a pasar a merma…'
                  : 'Elegir producto…'}
          />
        </div>

        {/* Grid de columnas compartido por cabecera y filas, armado según el modo para que todo
            quede perfectamente alineado por construcción. */}
        {(() => {
          // En salidas de panadería, SIEMPRE se muestra el disponible del artículo (por origen de
          // cada renglón), para que el cajero sepa qué hay.
          const showAvail = !isIn && isBakery;
          const availLabel = exitMode === 'REPARTO' ? 'Disponible' : 'Existencia';
          // La columna de dinero (precio fijo bloqueado o costo editable) e importe se muestran
          // salvo en merma pura (MANUAL): fixedPrice || !isWasteExit los incluye (incl. REPARTO).
          const showMoney = fixedPrice || !isWasteExit;
          const cols = [
            'minmax(0, 1fr)',                 // Artículo
            '136px',                          // Cantidad (stepper)
            ...(showAvail ? ['150px'] : []),  // Disponible / Existencia
            ...(showMoney ? ['128px'] : []),  // Precio fijo o Costo unitario
            ...(showMoney ? ['110px'] : []),  // Importe
            '44px',                           // Acción
          ].join(' ');
          return (
            <div className="idoc-lines">
              <div className="idoc-row-head" style={{ gridTemplateColumns: cols }}>
                <span>Artículo</span>
                <span className="idoc-h-right">Cantidad</span>
                {showAvail && <span className="idoc-h-right">{availLabel}</span>}
                {showMoney && <span className="idoc-h-right">{fixedPrice ? 'Precio' : 'Costo unitario'}</span>}
                {showMoney && <span className="idoc-h-right">Importe</span>}
                <span />
              </div>

              {lines.map((l, i) => {
                // Disponible del artículo por ORIGEN del renglón (clave compuesta), fuente única.
                const avail = availByKey[`${l.source}:${l.productId}`];
                const over = showAvail && avail != null && l.quantity > avail;
                return (
                  <div className="idoc-row" key={`${l.source}:${l.productId}`} style={{ gridTemplateColumns: cols }}>
                    <div className="idoc-article">
                      <span className="idoc-article-avatar" style={{ background: avatarColor(l.description) }}>
                        {avatarInitials(l.description)}
                      </span>
                      <div className="idoc-article-text">
                        <div className="idoc-article-name">{l.description}</div>
                        <div className="idoc-article-unit">{l.unit || 'pieza'}</div>
                      </div>
                    </div>

                    <div className={`idoc-qty ${l.quantity < 1 ? 'is-empty' : ''}`}>
                      <button type="button" aria-label="Menos" disabled={l.quantity <= 1}
                        onClick={() => setLine(i, { quantity: Math.max(1, l.quantity - 1) })}>
                        <Minus size={14} />
                      </button>
                      {/* Placeholder "1" cuando aún no hay cantidad (0); nunca se registra en 0. */}
                      <input type="number" min={1} step={1} placeholder="1"
                        value={l.quantity > 0 ? l.quantity : ''}
                        onChange={(e) => {
                          const v = Math.floor(Number(e.target.value));
                          setLine(i, { quantity: Number.isFinite(v) && v > 0 ? v : 0 });
                        }} />
                      <button type="button" aria-label="Más"
                        onClick={() => setLine(i, { quantity: Math.max(1, l.quantity) + 1 })}>
                        <Plus size={14} />
                      </button>
                    </div>

                    {showAvail && (
                      <span className={`idoc-availcell ${avail != null && avail > 0 ? (over ? 'is-over' : 'is-ok') : 'is-none'}`}>
                        {avail == null
                          ? '…'
                          : avail > 0
                            ? <>{avail} <span className="idoc-availcell-unit">{l.unit || 'pz'}</span>{over && ' ⚠'}</>
                            : 'Sin existencia'}
                      </span>
                    )}

                    {fixedPrice ? (
                      // El precio lo define la ficha del producto (por origen); NO se edita aquí.
                      // Si el artículo no tiene precio, se marca para obligar a capturarlo primero.
                      l.entryPrice > 0 ? (
                        <span className="idoc-fixed-price" title="Precio del artículo (no editable aquí)">
                          {money(l.entryPrice)}
                        </span>
                      ) : (
                        <span className="idoc-fixed-price is-missing" title="Este artículo no tiene precio. Captúralo en su ficha.">
                          Sin precio
                        </span>
                      )
                    ) : !isWasteExit ? (
                      // Salida NORMAL no-pan: costo unitario editable.
                      <label className="idoc-money">
                        <span className="idoc-money-sign">$</span>
                        <input type="number" min={0} step="0.01" placeholder="0.00"
                          value={l.unitCost || ''}
                          onChange={(e) => setLine(i, { unitCost: Number(e.target.value) || 0 })} />
                      </label>
                    ) : null}

                    {(fixedPrice || !isWasteExit) && (
                      <span className="idoc-amount">{money(l.quantity * (fixedPrice ? l.entryPrice : l.unitCost))}</span>
                    )}

                    <button type="button" className="idoc-remove" aria-label="Quitar" onClick={() => removeLine(i)}>
                      <Trash2 size={15} />
                    </button>
                  </div>
                );
              })}

              {lines.length === 0 && (
                <div className="idoc-empty">
                  <PackageOpen size={26} strokeWidth={1.5} />
                  <span>Elige un artículo arriba para agregarlo al documento.</span>
                </div>
              )}
            </div>
          );
        })()}

        <div className="pur-foot">
          <div />
          <div className="pur-totals">
            <div className="pur-total-row"><span>Unidades</span><span>{totalUnits}</span></div>
            {(fixedPrice || !isWasteExit) && <div className="pur-total-row pur-total-grand"><span>Valor</span><strong>{money(totalValue)}</strong></div>}
            <div className="pur-actions">
              <button className="btn-accent" disabled={busy} onClick={submit}>
                <Check size={16} /> {isWasteExit
                  ? 'Pasar a merma'
                  : (!isIn && isBakery && exitMode === 'REPARTO') ? 'Registrar reparto'
                    : isProduction ? 'Registrar producción' : isIn ? 'Registrar entrada' : 'Registrar salida'}
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="idoc-hist-top">
          <h3 className="sec-title"><Icon size={18} /> {isProduction ? 'Producción' : isIn ? 'Entradas' : 'Salidas'} del día</h3>
          {(isProduction || isBakeryExit) && (
            <label className="field idoc-hist-day" style={{ margin: 0 }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Calendar size={14} /> Día</span>
              <input type="date" value={day} max={todayIso()} onChange={(e) => setDay(e.target.value || todayIso())} />
            </label>
          )}
        </div>
        {isProduction ? (
          // ENTRADAS / Producción reciente homologada a Salidas: MISMO grid (histCols) y las
          // MISMAS 9 columnas. Tipo (chip "Producción") · Folio (PROD-) · Concepto · Artículo
          // (avatar) · Almacén · Cantidad · Precio · Importe · Registró (usuario + fecha/hora).
          <div className="idoc-hist">
            <div className="idoc-hist-head" style={{ gridTemplateColumns: histCols }}>
              <span>Tipo</span>
              <span>Folio</span>
              <span>Concepto</span>
              <span>Artículo</span>
              <span>Almacén</span>
              <span className="idoc-hist-right">Cantidad</span>
              <span className="idoc-hist-right">Precio</span>
              <span className="idoc-hist-right">Importe</span>
              <span>Registró</span>
            </div>
            <div className="idoc-hist-body">
              {prodHistory.map((r, i) => {
                const name = r.productName || r.description || '';
                return (
                  <div className="idoc-hist-row" key={`${r.productionId}-${i}`} style={{ gridTemplateColumns: histCols }}>
                    <span className="idoc-hist-chip" style={{ background: kindColor('PRODUCCION') }}>
                      {kindLabel('PRODUCCION')}
                    </span>
                    {r.folio
                      ? <span className="idoc-hist-folio">{r.folio}</span>
                      : <span className="idoc-hist-muted">—</span>}
                    <span className="idoc-hist-name">Producción de pan</span>
                    <span className="idoc-hist-article">
                      <span className="idoc-hist-article-avatar"
                        style={{ background: name ? avatarColor(name) : '#64748b' }}>
                        {name ? avatarInitials(name) : <PackageOpen size={13} />}
                      </span>
                      <span className="idoc-hist-article-name">{name || '—'}</span>
                    </span>
                    <span>{r.branchName || '—'}</span>
                    <span className="idoc-hist-num">{r.quantity ?? 0} <span className="idoc-hist-muted" style={{ fontWeight: 400 }}>{r.unit || 'pz'}</span></span>
                    <span className="idoc-hist-num">{money(r.entryPrice ?? 0)}</span>
                    <span className="idoc-hist-amount">{money(r.lineTotal ?? 0)}</span>
                    <RegisteredCell who={r.createdBy || '—'} createdAt={r.createdAt} />
                  </div>
                );
              })}
              {prodHistory.length === 0 && (
                <div className="idoc-hist-empty">
                  {day === todayIso()
                    ? 'Aún no hay producción hoy. Registra el pan horneado arriba para que aparezca aquí.'
                    : `No hubo producción el ${dayLabel(day)}.`}
                </div>
              )}
            </div>
          </div>
        ) : isBakeryExit ? (
          // SALIDAS panadería: lista UNIFICADA con el MISMO grid (histCols) y columnas que
          // Entradas. El concepto y el folio (S- normal / M- merma) vienen resueltos del backend;
          // ninguna fila nueva queda en "—". La columna PRECIO muestra "—" (una salida de merma se
          // valora por su pérdida total, no por precio unitario); IMPORTE = pérdida (design.md §5.0).
          <div className="idoc-hist">
            <div className="idoc-hist-head" style={{ gridTemplateColumns: histCols }}>
              <span>Tipo</span>
              <span>Folio</span>
              <span>Concepto</span>
              <span>Artículo</span>
              <span>Almacén</span>
              <span className="idoc-hist-right">Cantidad</span>
              <span className="idoc-hist-right">Precio</span>
              <span className="idoc-hist-right">Importe</span>
              <span>Registró</span>
            </div>
            <div className="idoc-hist-body">
              {exits.map((r) => {
                const single = r.productCount === 1 && !!r.productName;
                return (
                  <div className="idoc-hist-row" key={r.id} style={{ gridTemplateColumns: histCols }}>
                    <span className="idoc-hist-chip" style={{ background: kindColor(r.kind) }}>
                      {kindLabel(r.kind)}
                    </span>
                    {r.folio
                      ? <span className="idoc-hist-folio">{r.folio}</span>
                      : <span className="idoc-hist-muted">—</span>}
                    <span className="idoc-hist-name">{r.concept}</span>
                    <span className="idoc-hist-article">
                      <span className="idoc-hist-article-avatar"
                        style={{ background: single ? avatarColor(r.productName as string) : '#64748b' }}>
                        {single ? avatarInitials(r.productName as string) : <PackageOpen size={13} />}
                      </span>
                      <span className="idoc-hist-article-name">{r.itemsLabel}</span>
                    </span>
                    <span>{r.branchName || '—'}</span>
                    <span className="idoc-hist-num">{r.totalQty}</span>
                    <span className="idoc-hist-num idoc-hist-muted">—</span>
                    <span className="idoc-hist-amount">{money(r.totalValue)}</span>
                    <RegisteredCell who={r.cashier || '—'} createdAt={r.createdAt} />
                  </div>
                );
              })}
              {exits.length === 0 && (
                <div className="idoc-hist-empty">
                  {day === todayIso()
                    ? 'Aún no hay salidas hoy. Registra una salida, un paso a merma o un reparto arriba.'
                    : `No hubo movimientos el ${dayLabel(day)}.`}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="idoc-hist">
            {(() => {
              const cols = '110px minmax(160px, 1fr) 160px 110px 140px 150px';
              return (
                <>
                  <div className="idoc-hist-head" style={{ gridTemplateColumns: cols }}>
                    <span>Folio</span>
                    <span>Concepto</span>
                    <span>Almacén</span>
                    <span className="idoc-hist-right">Unidades</span>
                    <span className="idoc-hist-right">Valor</span>
                    <span>Registró</span>
                  </div>
                  <div className="idoc-hist-body">
                    {history.map((d) => (
                      <div className="idoc-hist-row" key={d.id} style={{ gridTemplateColumns: cols }}>
                        <span className="idoc-hist-folio">{d.folio}</span>
                        <span className="idoc-hist-name">{d.conceptName || <span className="idoc-hist-muted">—</span>}</span>
                        <span>{d.branchName || '—'}</span>
                        <span className="idoc-hist-num">{d.totalQty}</span>
                        <span className="idoc-hist-amount">{money(d.totalValue)}</span>
                        <span className="idoc-hist-muted">{d.createdBy || '—'}</span>
                      </div>
                    ))}
                    {history.length === 0 && <div className="idoc-hist-empty">Sin movimientos registrados.</div>}
                  </div>
                </>
              );
            })()}
          </div>
        )}
      </motion.div>

      {/* Modal: no se puede dar producción a artículos sin precio. Guía al cajero a capturarlo. */}
      <AnimatePresence>
        {missingPrice.length > 0 && (
          <motion.div className="idoc-modal-overlay"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setMissingPrice([])}>
            <motion.div className="idoc-modal" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97 }} transition={{ duration: 0.18 }}>
              <div className="idoc-modal-icon"><TriangleAlert size={26} /></div>
              <h3 className="idoc-modal-title">Captura primero el precio</h3>
              <p className="idoc-modal-text">
                No se puede registrar la producción con precio en 0. Estos artículos no tienen precio
                de venta configurado:
              </p>
              <ul className="idoc-modal-list">
                {missingPrice.map((n) => <li key={n}>{n}</li>)}
              </ul>
              <p className="idoc-modal-hint">
                Ve a <strong>Productos</strong>, abre el artículo y captura su <strong>precio de venta</strong>
                en la pestaña Precios. Luego vuelve a registrar la producción.
              </p>
              <div className="idoc-modal-actions">
                <button className="btn-accent" onClick={() => setMissingPrice([])}>Entendido</button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function InventoryEntriesPage() { return <InventoryDocPage docType="IN" />; }
export function InventoryExitsPage() { return <InventoryDocPage docType="OUT" />; }
