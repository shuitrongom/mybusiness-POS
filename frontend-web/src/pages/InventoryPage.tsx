import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'motion/react';
import {
  Boxes, PackageCheck, Search, X, Layers, DollarSign, TriangleAlert,
  ChevronLeft, ChevronRight, ArrowDownToLine, ArrowUpFromLine,
  SlidersHorizontal, ArrowLeftRight,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, pressable, staggerContainer, staggerItem } from '@/lib/motion';
import '@/pages/dashboard.css';
import './inventory.css';

interface StockRow {
  productId: number;
  branchId: number;
  branchName: string | null;
  productName: string;
  sku: string | null;
  unit: string;
  categoryName: string | null;
  quantity: number;
  minQuantity: number;
  cost: number;
  stockValue: number;
}

interface Summary { skus: number; units: number; value: number; alerts: number; }
interface Branch { id: number; name: string; active: boolean; }

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);

type Op = 'IN' | 'OUT' | 'ADJUST' | 'TRANSFER';

/**
 * Inventario nivel enterprise: KPIs (SKUs, unidades, valor, alertas), tabla de existencias con
 * nombre de producto, buscador, filtro por sucursal y estado de stock, y operaciones por producto
 * (entrada, salida, ajuste, traspaso) mediante un panel — sin capturar IDs a mano.
 */
export function InventoryPage() {
  const queryClient = useQueryClient();
  const [branchId, setBranchId] = useState<number | 'all'>('all');
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'low' | 'out'>('all');
  const [op, setOp] = useState<{ kind: Op; row: StockRow } | null>(null);
  const [page, setPage] = useState(1);
  const PAGE_SIZE = 12;

  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data.filter((b) => b.active),
  });

  const branchParam = branchId === 'all' ? '' : `?branchId=${branchId}`;

  const stock = useQuery({
    queryKey: ['inventory', 'stock', branchId],
    queryFn: async () => (await api.get<StockRow[]>(`/inventory/stock${branchParam}`)).data,
  });

  const summary = useQuery({
    queryKey: ['inventory', 'summary', branchId],
    queryFn: async () => (await api.get<Summary>(`/inventory/summary${branchParam}`)).data,
  });

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (stock.data ?? []).filter((r) => {
      const matchText = q === '' || r.productName.toLowerCase().includes(q) || (r.sku ?? '').toLowerCase().includes(q);
      const low = r.minQuantity > 0 && r.quantity <= r.minQuantity;
      const out = r.quantity <= 0;
      const matchStatus = statusFilter === 'all' || (statusFilter === 'low' && low) || (statusFilter === 'out' && out);
      return matchText && matchStatus;
    });
  }, [stock.data, query, statusFilter]);

  // Al cambiar búsqueda, filtro o sucursal, vuelve a la primera página.
  useEffect(() => { setPage(1); }, [query, statusFilter, branchId]);

  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageRows = rows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const s = summary.data;

  return (
    <div>
      <div className="inv-head">
        <div>
          <h1 className="page-title">Inventario</h1>
          <p className="page-sub">Existencias, movimientos y alertas</p>
        </div>
        {(branches.data?.length ?? 0) > 0 && (
          <select className="inv-branch-select" value={branchId}
            onChange={(e) => setBranchId(e.target.value === 'all' ? 'all' : Number(e.target.value))}>
            <option value="all">Todas las sucursales</option>
            {branches.data!.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        )}
      </div>

      {/* KPIs */}
      <motion.div className="inv-kpis" variants={staggerContainer} initial="hidden" animate="visible">
        <Kpi icon={<Layers size={18} />} tint="blue" label="Productos (SKUs)" value={num(s?.skus ?? 0)} />
        <Kpi icon={<Boxes size={18} />} tint="green" label="Unidades en existencia" value={num(s?.units ?? 0)} />
        <Kpi icon={<DollarSign size={18} />} tint="violet" label="Valor del inventario" value={money(s?.value ?? 0)} />
        <Kpi icon={<TriangleAlert size={18} />} tint="amber" label="Alertas de stock" value={num(s?.alerts ?? 0)} />
      </motion.div>

      {/* Tabla de existencias */}
      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <div className="inv-search">
            <Search size={16} />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar producto o SKU…" />
          </div>
          <div className="inv-filters">
            <button className={`inv-chip ${statusFilter === 'all' ? 'is-active' : ''}`} onClick={() => setStatusFilter('all')}>Todos</button>
            <button className={`inv-chip ${statusFilter === 'low' ? 'is-active' : ''}`} onClick={() => setStatusFilter('low')}>Stock bajo</button>
            <button className={`inv-chip ${statusFilter === 'out' ? 'is-active' : ''}`} onClick={() => setStatusFilter('out')}>Agotados</button>
          </div>
        </div>

        <table className="table inv-table">
          <thead>
            <tr>
              <th>Producto</th><th>Sucursal</th><th>Existencia</th><th>Mínimo</th>
              <th>Costo</th><th>Valor</th><th>Acciones</th>
            </tr>
          </thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {pageRows.map((r) => {
              const out = r.quantity <= 0;
              const low = !out && r.minQuantity > 0 && r.quantity <= r.minQuantity;
              return (
                <motion.tr key={`${r.productId}-${r.branchId}`} variants={staggerItem}>
                  <td>
                    <strong>{r.productName}</strong>
                    <div className="inv-sub">{r.categoryName ?? 'Sin categoría'}{r.sku ? ` · ${r.sku}` : ''}</div>
                  </td>
                  <td>{r.branchName ?? r.branchId}</td>
                  <td>
                    <span className={`inv-stock ${out ? 'is-out' : low ? 'is-low' : 'is-ok'}`}>
                      {num(r.quantity)} {r.unit}
                    </span>
                  </td>
                  <td>{num(r.minQuantity)}</td>
                  <td>{money(r.cost)}</td>
                  <td>{money(r.stockValue)}</td>
                  <td>
                    <div className="inv-actions">
                      <button type="button" className="inv-op inv-op-in" title="Entrada (surtir)"
                        onClick={() => setOp({ kind: 'IN', row: r })}>
                        <ArrowDownToLine size={14} /> Entrada
                      </button>
                      <button type="button" className="inv-op inv-op-out" title="Salida (merma)"
                        onClick={() => setOp({ kind: 'OUT', row: r })}>
                        <ArrowUpFromLine size={14} /> Salida
                      </button>
                      <button type="button" className="inv-op inv-op-adj" title="Ajustar"
                        onClick={() => setOp({ kind: 'ADJUST', row: r })}>
                        <SlidersHorizontal size={14} /> Ajuste
                      </button>
                      <button type="button" className="inv-op inv-op-tr" title="Traspasar"
                        onClick={() => setOp({ kind: 'TRANSFER', row: r })}>
                        <ArrowLeftRight size={14} /> Traspaso
                      </button>
                    </div>
                  </td>
                </motion.tr>
              );
            })}
            {rows.length === 0 && (
              <tr><td colSpan={7} className="empty">
                {stock.data?.length === 0
                  ? 'Aún no hay existencias registradas.'
                  : 'Ningún producto coincide con el filtro.'}
              </td></tr>
            )}
          </motion.tbody>
        </table>
        {rows.length === 0 && stock.data?.length === 0 && (
          <div className="inv-empty"><PackageCheck size={40} strokeWidth={1.5} />
            <p>Registra entradas o compras para ver existencias aquí.</p></div>
        )}

        {rows.length > PAGE_SIZE && (
          <div className="inv-pager">
            <span className="inv-pager-info">
              Mostrando {(currentPage - 1) * PAGE_SIZE + 1}–{Math.min(currentPage * PAGE_SIZE, rows.length)} de {rows.length}
            </span>
            <div className="inv-pager-btns">
              <button className="inv-pager-btn" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>
                <ChevronLeft size={16} /> Anterior
              </button>
              <span className="inv-pager-page">{currentPage} / {totalPages}</span>
              <button className="inv-pager-btn" disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)}>
                Siguiente <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </motion.div>

      <AnimatePresence>
        {op && (
          <OpModal op={op.kind} row={op.row} branches={branches.data ?? []}
            onClose={() => setOp(null)}
            onDone={() => { setOp(null); queryClient.invalidateQueries({ queryKey: ['inventory'] }); }} />
        )}
      </AnimatePresence>
    </div>
  );
}

function Kpi({ icon, label, value, tint }: { icon: ReactNode; label: string; value: string; tint: string }) {
  return (
    <motion.div className="kpi-card" variants={staggerItem}>
      <span className={`kpi-icon kpi-${tint}`}>{icon}</span>
      <div className="kpi-body">
        <span className="kpi-label">{label}</span>
        <span className="kpi-value">{value}</span>
      </div>
    </motion.div>
  );
}

/** Panel de operación de inventario (entrada, salida, ajuste o traspaso) para un producto. */
function OpModal({ op, row, branches, onClose, onDone }: {
  op: Op; row: StockRow; branches: Branch[]; onClose: () => void; onDone: () => void;
}) {
  const [qty, setQty] = useState('');
  const [reason, setReason] = useState('');
  const [toBranch, setToBranch] = useState<number | ''>('');

  const title = op === 'IN' ? 'Entrada al inventario'
    : op === 'OUT' ? 'Salida del inventario'
    : op === 'ADJUST' ? 'Ajuste de inventario' : 'Traspaso entre sucursales';

  const run = useMutation({
    mutationFn: async () => {
      const q = Number(qty) || 0;
      if (op === 'TRANSFER') {
        return api.post('/inventory/transfer', {
          productId: row.productId, fromBranchId: row.branchId, toBranchId: Number(toBranch), quantity: q,
        });
      }
      // Entrada suma, salida resta, ajuste usa el signo tal cual se capture.
      const delta = op === 'IN' ? Math.abs(q) : op === 'OUT' ? -Math.abs(q) : q;
      const label = op === 'IN' ? 'Entrada' : op === 'OUT' ? 'Salida' : 'Ajuste';
      return api.post('/inventory/adjust', {
        productId: row.productId, branchId: row.branchId, delta,
        reason: reason.trim() || label,
      });
    },
    onSuccess: () => {
      toast.success('Inventario actualizado', `${row.productName}: operación aplicada.`);
      onDone();
    },
    onError: () => toast.error('No se pudo aplicar la operación', 'Revisa los datos e inténtalo de nuevo.'),
  });

  const canRun = op === 'TRANSFER'
    ? Number(qty) > 0 && toBranch !== '' && Number(toBranch) !== row.branchId
    : op === 'ADJUST' ? qty !== '' && Number(qty) !== 0 : Number(qty) > 0;

  return (
    <motion.div className="pos-modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="inv-modal" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={{ duration: 0.18 }}>
        <div className="inv-modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="inv-modal-body">
          <div className="inv-prod-chip">
            <strong>{row.productName}</strong>
            <span>Existencia actual: {num(row.quantity)} {row.unit} · {row.branchName ?? ''}</span>
          </div>

          <label className="field">
            <span>{op === 'ADJUST' ? 'Cantidad (+/−)' : 'Cantidad'}</span>
            <input type="number" step="0.001" value={qty} onChange={(e) => setQty(e.target.value)}
              placeholder={op === 'ADJUST' ? 'Ej. -3 o 5' : '0'} autoFocus />
          </label>

          {op === 'TRANSFER' && (
            <label className="field">
              <span>Sucursal destino</span>
              <select value={toBranch} onChange={(e) => setToBranch(Number(e.target.value))}>
                <option value="">Selecciona…</option>
                {branches.filter((b) => b.id !== row.branchId).map((b) =>
                  <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            </label>
          )}

          {op !== 'TRANSFER' && (
            <label className="field">
              <span>Motivo</span>
              <input value={reason} onChange={(e) => setReason(e.target.value)}
                placeholder={op === 'IN' ? 'Recepción, devolución…' : op === 'OUT' ? 'Merma, consumo…' : 'Conteo físico, corrección…'} />
            </label>
          )}
        </div>
        <div className="inv-modal-foot">
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <motion.button className="btn-accent" disabled={!canRun || run.isPending} onClick={() => run.mutate()}
            whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
            {run.isPending ? 'Aplicando…' : 'Aplicar'}
          </motion.button>
        </div>
      </motion.div>
    </motion.div>
  );
}
