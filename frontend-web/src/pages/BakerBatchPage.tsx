import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  Minus, Plus, Wheat, Croissant, Check, X, ClipboardList, PackageOpen,
} from 'lucide-react';
import { api } from '@/lib/api';
import { avatarColor, avatarInitials } from '@/lib/saleVisuals';
import { InfoBanner } from '@/components/InfoBanner';
import { fadeInUp } from '@/lib/motion';
import { storedBranchId } from '@/lib/cashierBranch';
import '@/pages/dashboard.css';
import '@/pages/baker.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface RawMaterialRow { rawMaterialId: number; name: string; unit: string; quantity: number; unitCost: number; }
interface ProductRow { productId: number; name: string; unit: string; entryPrice: number; priceReady: boolean; }

/** Error de negocio devuelto por el backend (ProblemDetail). */
interface ApiProblem { status?: number; title?: string; detail?: string; available?: number; requested?: number; rawMaterialName?: string; }

type Result =
  | { kind: 'ok'; folio: string }
  | { kind: 'err'; title: string; message: string };

/**
 * Captura de TANDA del panadero, mobile-first en 3 pasos: ¿qué tomaste? (insumos),
 * ¿qué salió? (panes) y guardar. Botones y steppers grandes, a prueba de usuario no
 * técnico. El precio del pan lo manda la ficha (bloqueado); no se opera con lo que no
 * hay (tope de existencia en captura) ni con precio 0 (pan bloqueado antes del submit).
 */
export function BakerBatchPage() {
  const branchId = storedBranchId() || 1;

  // Resuelve la sucursal de la sesión (respaldo: primera activa) de forma no bloqueante.
  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });
  const resolvedBranch = useMemo(() => {
    const active = (branches.data ?? []).filter((b) => b.active);
    if (active.some((b) => b.id === branchId)) return branchId;
    return active[0]?.id ?? branchId;
  }, [branches.data, branchId]);

  const rawMaterials = useQuery({
    queryKey: ['baker', 'raw-materials', resolvedBranch],
    queryFn: async () => (await api.get<RawMaterialRow[]>('/bakery/batches/raw-materials', {
      params: { branchId: resolvedBranch },
    })).data,
  });
  const products = useQuery({
    queryKey: ['baker', 'products', resolvedBranch],
    queryFn: async () => (await api.get<ProductRow[]>('/bakery/batches/products', {
      params: { branchId: resolvedBranch },
    })).data,
  });

  const [step, setStep] = useState<1 | 2 | 3>(1);
  // Cantidades capturadas por id (0 = sin captura).
  const [inputs, setInputs] = useState<Record<number, number>>({});
  const [outputs, setOutputs] = useState<Record<number, number>>({});
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<Result | null>(null);

  const rmById = useMemo(() => new Map((rawMaterials.data ?? []).map((r) => [r.rawMaterialId, r])), [rawMaterials.data]);
  const prodById = useMemo(() => new Map((products.data ?? []).map((p) => [p.productId, p])), [products.data]);

  const chosenInputs = Object.entries(inputs).filter(([, q]) => q > 0);
  const chosenOutputs = Object.entries(outputs).filter(([, q]) => q > 0);

  const totalInputCost = chosenInputs.reduce((s, [id, q]) => s + q * (rmById.get(Number(id))?.unitCost ?? 0), 0);
  const totalOutputValue = chosenOutputs.reduce((s, [id, q]) => s + q * (prodById.get(Number(id))?.entryPrice ?? 0), 0);

  // Tope de existencia en captura: el stepper no supera la existencia disponible.
  const setInputQty = (id: number, q: number) => {
    const max = rmById.get(id)?.quantity ?? 0;
    const clamped = Math.max(0, Math.min(q, max));
    setInputs((cur) => ({ ...cur, [id]: clamped }));
  };
  const setOutputQty = (id: number, q: number) => {
    setOutputs((cur) => ({ ...cur, [id]: Math.max(0, q) }));
  };

  const save = async () => {
    setSaving(true);
    try {
      const body = {
        branchId: resolvedBranch,
        notes: notes.trim() || null,
        inputs: chosenInputs.map(([id, q]) => ({ rawMaterialId: Number(id), quantity: q })),
        outputs: chosenOutputs.map(([id, q]) => ({ productId: Number(id), quantity: q })),
      };
      const { data } = await api.post<{ batchId: number; folio: string }>('/bakery/batches', body);
      setResult({ kind: 'ok', folio: data.folio });
      setInputs({});
      setOutputs({});
      setNotes('');
      setStep(1);
      rawMaterials.refetch();
    } catch (e) {
      const problem = (e as { response?: { data?: ApiProblem; status?: number } }).response;
      const data = problem?.data;
      const status = problem?.status;
      if (status === 409 && data) {
        setResult({
          kind: 'err',
          title: 'No hay suficiente insumo',
          message: `No hay suficiente ${data.rawMaterialName ?? 'insumo'}: disponible ${int(data.available ?? 0)}, pediste ${int(data.requested ?? 0)}.`,
        });
      } else if (status === 422) {
        setResult({
          kind: 'err',
          title: 'Falta capturar un precio',
          message: data?.detail ?? 'Captura primero el precio del artículo antes de registrar su producción.',
        });
      } else {
        setResult({ kind: 'err', title: 'No se pudo guardar', message: 'Intenta de nuevo en un momento.' });
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bk-wrap">
      <h1 className="page-title">Nueva tanda</h1>
      <InfoBanner icon={<ClipboardList size={18} />} tone="brand" title="Registra tu tanda">
        Captura lo que tomaste y lo que salió. El sistema baja los insumos, sube el pan y guarda todo de una vez.
      </InfoBanner>

      <div className="bk-steps">
        <div className={`bk-step ${step === 1 ? 'is-active' : step > 1 ? 'is-done' : ''}`}>1. ¿Qué tomaste?</div>
        <div className={`bk-step ${step === 2 ? 'is-active' : step > 2 ? 'is-done' : ''}`}>2. ¿Qué salió?</div>
        <div className={`bk-step ${step === 3 ? 'is-active' : ''}`}>3. Guardar</div>
      </div>

      {step === 1 && (
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Wheat size={18} /> Insumos que tomaste</h3>
          <div className="bk-list">
            <div className="bk-row-head" style={{ gridTemplateColumns: 'minmax(0,1fr) 170px' }}>
              <span>Insumo</span>
              <span className="bk-h-center">Cantidad</span>
            </div>
            {(rawMaterials.data ?? []).map((r) => {
              const q = inputs[r.rawMaterialId] ?? 0;
              return (
                <div className="bk-row" key={r.rawMaterialId} style={{ gridTemplateColumns: 'minmax(0,1fr) 170px' }}>
                  <div className="bk-article">
                    <span className="bk-avatar" style={{ background: avatarColor(r.name) }}>{avatarInitials(r.name)}</span>
                    <div className="bk-article-text">
                      <div className="bk-article-name">{r.name}</div>
                      <div className="bk-article-unit">
                        <span className="bk-chip is-gray">Disponible: {int(r.quantity)} {r.unit}</span>
                      </div>
                    </div>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'center' }}>
                    <div className="bk-stepper">
                      <button type="button" aria-label="Menos" disabled={q <= 0}
                        onClick={() => setInputQty(r.rawMaterialId, q - 1)}><Minus size={18} /></button>
                      <input type="number" min={0} max={r.quantity} placeholder="0" value={q > 0 ? q : ''}
                        onChange={(e) => setInputQty(r.rawMaterialId, Math.floor(Number(e.target.value)) || 0)} />
                      <button type="button" aria-label="Más" disabled={q >= r.quantity}
                        onClick={() => setInputQty(r.rawMaterialId, q + 1)}><Plus size={18} /></button>
                    </div>
                  </div>
                </div>
              );
            })}
            {!rawMaterials.isLoading && (rawMaterials.data ?? []).length === 0 && (
              <div className="bk-empty">
                <PackageOpen size={28} strokeWidth={1.5} />
                <strong>No hay insumos con existencia</strong>
                <span>Pídele al admin que dé de alta y surta los insumos en Inventario → Materia prima.</span>
              </div>
            )}
          </div>
          <div className="bk-actions">
            <button className="bk-btn bk-btn-primary" disabled={chosenInputs.length === 0}
              onClick={() => setStep(2)}>Siguiente</button>
          </div>
        </motion.div>
      )}

      {step === 2 && (
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Croissant size={18} /> Pan que salió</h3>
          <div className="bk-list">
            <div className="bk-row-head" style={{ gridTemplateColumns: 'minmax(0,1fr) 120px 170px' }}>
              <span>Pan</span>
              <span className="bk-h-right">Precio</span>
              <span className="bk-h-center">Cantidad</span>
            </div>
            {(products.data ?? []).map((p) => {
              const q = outputs[p.productId] ?? 0;
              return (
                <div className={`bk-row ${!p.priceReady ? 'is-disabled' : ''}`} key={p.productId}
                  style={{ gridTemplateColumns: 'minmax(0,1fr) 120px 170px' }}>
                  <div className="bk-article">
                    <span className="bk-avatar" style={{ background: avatarColor(p.name) }}>{avatarInitials(p.name)}</span>
                    <div className="bk-article-text">
                      <div className="bk-article-name">{p.name}</div>
                      <div className="bk-article-unit">
                        {p.priceReady ? p.unit : <span className="bk-chip is-gray">Captura primero el precio</span>}
                      </div>
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    {p.priceReady
                      ? <span className="bk-chip is-green">{money(p.entryPrice)}</span>
                      : <span className="bk-chip is-gray">—</span>}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'center' }}>
                    <div className="bk-stepper">
                      <button type="button" aria-label="Menos" disabled={!p.priceReady || q <= 0}
                        onClick={() => setOutputQty(p.productId, q - 1)}><Minus size={18} /></button>
                      <input type="number" min={0} placeholder="0" disabled={!p.priceReady}
                        value={q > 0 ? q : ''}
                        onChange={(e) => setOutputQty(p.productId, Math.floor(Number(e.target.value)) || 0)} />
                      <button type="button" aria-label="Más" disabled={!p.priceReady}
                        onClick={() => setOutputQty(p.productId, q + 1)}><Plus size={18} /></button>
                    </div>
                  </div>
                </div>
              );
            })}
            {!products.isLoading && (products.data ?? []).length === 0 && (
              <div className="bk-empty">
                <PackageOpen size={28} strokeWidth={1.5} />
                <strong>No hay panes en el catálogo</strong>
                <span>Pídele al admin que dé de alta los productos de pan con su precio.</span>
              </div>
            )}
          </div>
          <div className="bk-actions">
            <button className="bk-btn" onClick={() => setStep(1)}>Atrás</button>
            <button className="bk-btn bk-btn-primary" disabled={chosenOutputs.length === 0}
              onClick={() => setStep(3)}>Siguiente</button>
          </div>
        </motion.div>
      )}

      {step === 3 && (
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Check size={18} /> Revisa y guarda</h3>
          <div className="bk-list">
            {chosenInputs.map(([id, q]) => {
              const r = rmById.get(Number(id));
              return (
                <div className="bk-row" key={`in-${id}`} style={{ gridTemplateColumns: 'minmax(0,1fr) auto' }}>
                  <div className="bk-article-name">{r?.name}</div>
                  <span className="bk-chip is-amber">{int(q)} {r?.unit}</span>
                </div>
              );
            })}
            {chosenOutputs.map(([id, q]) => {
              const p = prodById.get(Number(id));
              return (
                <div className="bk-row" key={`out-${id}`} style={{ gridTemplateColumns: 'minmax(0,1fr) auto' }}>
                  <div className="bk-article-name">{p?.name}</div>
                  <span className="bk-chip is-green">{int(q)} pz</span>
                </div>
              );
            })}
          </div>

          <label className="field" style={{ marginTop: 'var(--space-3)' }}>
            <span>Notas (opcional)</span>
            <input type="text" placeholder="Hornada matutina…" value={notes}
              onChange={(e) => setNotes(e.target.value)} />
          </label>

          <div className="bk-foot">
            <div className="bk-foot-row"><span>Costo de insumos</span><span className="bk-num">{money(totalInputCost)}</span></div>
            <div className="bk-foot-row"><span>Valor del pan</span><strong className="bk-num">{money(totalOutputValue)}</strong></div>
          </div>

          <div className="bk-actions">
            <button className="bk-btn" onClick={() => setStep(2)} disabled={saving}>Atrás</button>
            <button className="bk-btn bk-btn-primary" disabled={saving || chosenInputs.length === 0 || chosenOutputs.length === 0}
              onClick={save}>{saving ? 'Guardando…' : 'Guardar tanda'}</button>
          </div>
        </motion.div>
      )}

      {result && (
        <div className="bk-modal-backdrop" onClick={() => setResult(null)}>
          <motion.div className="bk-modal" variants={fadeInUp} initial="hidden" animate="visible"
            onClick={(e) => e.stopPropagation()}>
            {result.kind === 'ok' ? (
              <>
                <div className="bk-modal-icon is-ok"><Check size={30} /></div>
                <h3>¡Tanda guardada!</h3>
                <p>Folio {result.folio}. El pan ya está en existencias y los insumos bajaron.</p>
              </>
            ) : (
              <>
                <div className="bk-modal-icon is-err"><X size={30} /></div>
                <h3>{result.title}</h3>
                <p>{result.message}</p>
              </>
            )}
            <button className="bk-btn bk-btn-primary" onClick={() => setResult(null)}>Entendido</button>
          </motion.div>
        </div>
      )}
    </div>
  );
}
