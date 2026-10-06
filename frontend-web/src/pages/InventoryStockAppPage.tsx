import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Smartphone, QrCode, FileSpreadsheet, Check, ArrowDownToLine, ClipboardList } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface Item { code: string; quantity: number; cost: number; }

/**
 * Inventario Físico StockApp: importa conteos y entradas capturados con la app móvil / lector.
 * Acepta dos fuentes: filas pegadas de Excel (código, cantidad, costo) o el JSON que genera el
 * QR de la StockApp. Elige almacén y modo: ENTRADA (suma existencias) o INVENTARIO FÍSICO
 * (ajusta a lo contado). Resuelve cada producto por SKU, código de barras o id.
 */
export function InventoryStockAppPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branchId, setBranchId] = useState<number>(0);
  const [mode, setMode] = useState<'ENTRY' | 'PHYSICAL'>('ENTRY');
  const [source, setSource] = useState<'excel' | 'qr'>('excel');
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Branch[]>('/branches').then((r) => {
      const active = r.data.filter((b) => b.active);
      setBranches(active); if (active[0]) setBranchId(active[0].id);
    }).catch(() => {});
  }, []);

  const parsed = useMemo<Item[]>(() => {
    if (!text.trim()) return [];
    if (source === 'qr') {
      // JSON de la StockApp: array de {code|sku|productId, qty|quantity, cost?}.
      try {
        const raw = JSON.parse(text);
        const arr = Array.isArray(raw) ? raw : Array.isArray(raw.items) ? raw.items : [];
        return arr.map((o: Record<string, unknown>) => ({
          code: String(o.code ?? o.sku ?? o.productId ?? '').trim(),
          quantity: Number(o.qty ?? o.quantity ?? 0),
          cost: Number(o.cost ?? 0),
        })).filter((i: Item) => i.code && i.quantity > 0);
      } catch {
        return [];
      }
    }
    // Excel: líneas "código<TAB|,|;>cantidad<...>costo".
    return text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).map((l) => {
      const c = l.split(/\t|,|;/).map((x) => x.trim());
      return { code: c[0] ?? '', quantity: Number(c[1]) || 0, cost: c[2] ? Number(c[2]) : 0 };
    }).filter((i) => i.code && i.quantity > 0);
  }, [text, source]);

  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setText(String(reader.result));
    reader.readAsText(file);
  };

  const run = async () => {
    if (!branchId) { toast.info('Elige un almacén'); return; }
    if (parsed.length === 0) { toast.info('No hay líneas válidas para importar'); return; }
    setBusy(true);
    try {
      const { data } = await api.post<{ imported: number; notFound: string[]; documentId: number }>(
        '/inventory/stockapp/import',
        { mode, branchId, items: parsed.map((i) => ({ code: i.code, quantity: i.quantity, cost: i.cost })) });
      const nf = data.notFound?.length ?? 0;
      toast.success(
        mode === 'ENTRY' ? 'Entrada generada desde StockApp' : 'Inventario físico aplicado',
        `${data.imported} línea(s) importada(s)${nf ? `, ${nf} sin coincidencia` : ''}.`);
      setText('');
    } catch {
      toast.error('No se pudo importar', 'Verifica el formato y que los códigos existan.');
    } finally { setBusy(false); }
  };

  const totalUnits = parsed.reduce((s, i) => s + i.quantity, 0);
  const totalValue = parsed.reduce((s, i) => s + i.quantity * i.cost, 0);

  return (
    <div>
      <h1 className="page-title">Inventario físico StockApp</h1>
      <p className="page-sub">Importa conteos y entradas capturados con la app móvil o lector QR</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Smartphone size={18} /> Importar</h3>

        <div className="pur-head" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field"><span>Almacén al que ingresa</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field"><span>Modo</span>
            <select value={mode} onChange={(e) => setMode(e.target.value as 'ENTRY' | 'PHYSICAL')}>
              <option value="ENTRY">Entrada de inventario (suma)</option>
              <option value="PHYSICAL">Inventario físico (ajusta a lo contado)</option>
            </select></label>
        </div>

        <div className="pos-methods" style={{ padding: 'var(--space-4) 0 0', gridTemplateColumns: '1fr 1fr', maxWidth: 360 }}>
          <button className={`pos-method ${source === 'excel' ? 'is-active' : ''}`} onClick={() => setSource('excel')}>
            <FileSpreadsheet size={16} /> Desde Excel
          </button>
          <button className={`pos-method ${source === 'qr' ? 'is-active' : ''}`} onClick={() => setSource('qr')}>
            <QrCode size={16} /> Desde QR (JSON)
          </button>
        </div>

        <p className="drawer-note">
          {source === 'excel'
            ? 'Pega o sube filas: código/SKU, cantidad, costo (separados por tab, coma o punto y coma).'
            : 'Pega el JSON del QR de la StockApp: arreglo de objetos {code, quantity, cost}.'}
        </p>

        {source === 'excel' && (
          <div style={{ marginBottom: 'var(--space-2)' }}>
            <label className="btn-ghost" style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <FileSpreadsheet size={16} /> Subir archivo
              <input type="file" accept=".csv,.txt,text/csv" onChange={onFile} hidden />
            </label>
          </div>
        )}

        <textarea rows={7} value={text} onChange={(e) => setText(e.target.value)}
          placeholder={source === 'excel' ? '7501055310419\t12\t8.50\nSN-001\t3\t120' : '[{"code":"7501055310419","quantity":12,"cost":8.5}]'}
          style={{ width: '100%', fontFamily: 'monospace', fontSize: 13 }} />

        {parsed.length > 0 && (
          <table className="pur-table" style={{ marginTop: 'var(--space-3)' }}>
            <thead><tr><th>Código / SKU</th><th className="ta-right">Cantidad</th><th className="ta-right">Costo</th><th className="ta-right">Importe</th></tr></thead>
            <tbody>
              {parsed.slice(0, 50).map((i, idx) => (
                <tr key={idx}>
                  <td>{i.code}</td>
                  <td className="ta-right">{i.quantity}</td>
                  <td className="ta-right">{money(i.cost)}</td>
                  <td className="ta-right">{money(i.quantity * i.cost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="pur-foot">
          <div />
          <div className="pur-totals">
            <div className="pur-total-row"><span>Líneas</span><span>{parsed.length}</span></div>
            <div className="pur-total-row"><span>Unidades</span><span>{totalUnits}</span></div>
            <div className="pur-total-row pur-total-grand"><span>Valor</span><strong>{money(totalValue)}</strong></div>
            <div className="pur-actions">
              <button className="btn-accent" disabled={busy || parsed.length === 0} onClick={run}>
                {mode === 'ENTRY' ? <ArrowDownToLine size={16} /> : <ClipboardList size={16} />}
                {busy ? ' Importando…' : mode === 'ENTRY' ? ' Generar entrada' : ' Aplicar inventario'}
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Check size={18} /> Cómo funciona</h3>
        <ul className="drawer-note" style={{ lineHeight: 1.7 }}>
          <li><strong>Entrada de inventario:</strong> suma las cantidades importadas a las existencias del almacén (útil para recibir mercancía contada con la app).</li>
          <li><strong>Inventario físico:</strong> toma lo importado como conteo real y ajusta las existencias a esa cantidad (sube o baja según la diferencia).</li>
          <li>Cada código se busca por <strong>SKU</strong>, <strong>código de barras</strong> o <strong>id</strong> del producto. Los que no coincidan se reportan al final.</li>
        </ul>
      </motion.div>
    </div>
  );
}
