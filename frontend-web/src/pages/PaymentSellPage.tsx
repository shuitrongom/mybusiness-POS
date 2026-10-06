import { useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { AxiosError } from 'axios';
import { useNavigate } from 'react-router-dom';
import { Smartphone, Zap, Check, Wallet, Search, TriangleAlert, ArrowRight, Receipt } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { BrandLogo } from '@/components/BrandLogo';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/pos.css';
import '@/pages/payments.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface CatalogItem {
  id: number; opType: string; category: string; provider: string;
  commissionPct: number; commissionFixed: number; minAmount: number; maxAmount: number;
  fixedAmounts: string | null; referenceLabel: string; brandColor: string | null; logoUrl: string | null;
}
interface Summary { balance: number; }

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as { detail?: string; error?: string; message?: string } | undefined;
    return data?.detail ?? data?.error ?? data?.message ?? err.message;
  }
  return 'Ocurrió un error inesperado.';
}

/** Deduce reglas de validación del campo de referencia a partir de su etiqueta. */
function referenceRule(label: string): { digits?: number; message: string } | null {
  const l = label.toLowerCase();
  if (l.includes('10 dígitos') || (l.includes('teléfono') && l.includes('dígitos'))) {
    return { digits: 10, message: 'El teléfono debe tener exactamente 10 dígitos.' };
  }
  if (l.includes('teléfono')) {
    return { digits: 10, message: 'El teléfono debe tener 10 dígitos.' };
  }
  return null;
}

/**
 * Vender recarga o pagar servicio: elige compañía/servicio del catálogo real (agrupado por
 * categoría), captura la referencia y el monto, valida la referencia según la compañía y realiza
 * la operación (descuenta saldo, cobra al cliente y registra la comisión).
 */
export function PaymentSellPage({ opType }: { opType: 'RECHARGE' | 'SERVICE' }) {
  const isRecharge = opType === 'RECHARGE';
  const navigate = useNavigate();
  const [catalog, setCatalog] = useState<CatalogItem[]>([]);
  const [balance, setBalance] = useState(0);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<CatalogItem | null>(null);
  const [reference, setReference] = useState('');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [touched, setTouched] = useState(false);
  const [ticket, setTicket] = useState<{ provider: string; reference: string; amount: number; commission: number; folio: string | null } | null>(null);

  const loadBalance = () => api.get<Summary>('/payments/balance').then((r) => setBalance(r.data.balance)).catch(() => {});

  useEffect(() => {
    api.get<CatalogItem[]>('/payments/catalog', { params: { type: opType } })
      .then((r) => setCatalog(r.data)).catch(() => setCatalog([]));
    loadBalance();
  }, [opType]);

  const byCategory = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = catalog.filter((c) => q === '' || c.provider.toLowerCase().includes(q) || c.category.toLowerCase().includes(q));
    const groups: Record<string, CatalogItem[]> = {};
    for (const c of filtered) (groups[c.category] ??= []).push(c);
    return groups;
  }, [catalog, search]);

  const fixed = useMemo(() => {
    if (!selected?.fixedAmounts) return [];
    return selected.fixedAmounts.split(',').map((s) => Number(s.trim())).filter((n) => n > 0);
  }, [selected]);

  const rule = selected ? referenceRule(selected.referenceLabel) : null;
  const refDigits = reference.replace(/\D/g, '');
  const referenceValid = !rule?.digits || refDigits.length === rule.digits;

  const amountNum = Number(amount) || 0;
  const commission = selected
    ? selected.commissionFixed > 0 ? selected.commissionFixed : amountNum * selected.commissionPct
    : 0;
  const cost = Math.max(amountNum - commission, 0);
  const amountValid = amountNum > 0
    && (selected == null || selected.minAmount === 0 || amountNum >= selected.minAmount)
    && (selected == null || selected.maxAmount === 0 || amountNum <= selected.maxAmount);
  const insufficient = cost > balance;
  const canSell = selected != null && reference.trim() !== '' && referenceValid && amountValid && !insufficient;

  const pick = (c: CatalogItem) => { setSelected(c); setAmount(''); setReference(''); setTouched(false); };

  const onReferenceChange = (v: string) => {
    // Para teléfonos, solo dígitos y máximo 10.
    if (rule?.digits) setReference(v.replace(/\D/g, '').slice(0, rule.digits));
    else setReference(v);
  };

  const sell = async () => {
    setTouched(true);
    if (!selected || !canSell) return;
    setBusy(true);
    try {
      const url = isRecharge ? '/payments/recharge' : '/payments/service';
      const body = isRecharge
        ? { carrier: selected.provider, phone: reference.trim(), amount: amountNum, branchId: 1 }
        : { biller: selected.provider, reference: reference.trim(), amount: amountNum, branchId: 1 };
      const { data } = await api.post<{ folio: string | null; commission: number | null }>(url, body);
      setTicket({ provider: selected.provider, reference: reference.trim(), amount: amountNum, commission: data.commission ?? commission, folio: data.folio });
      toast.success(isRecharge ? 'Recarga realizada' : 'Servicio pagado', `Folio: ${data.folio ?? '—'}.`);
      setReference(''); setAmount(''); setTouched(false);
      loadBalance();
    } catch (err) {
      const msg = err instanceof AxiosError && err.response?.status === 409
        ? 'Saldo insuficiente. Reporta un abono para cargar saldo antes de vender.'
        : errorMessage(err);
      toast.error(isRecharge ? 'No se pudo recargar' : 'No se pudo pagar', msg);
    } finally { setBusy(false); }
  };

  const totalCount = catalog.length;

  return (
    <div className="pay-page">
      <div className="page-head-row">
        <div>
          <h1 className="page-title">{isRecharge ? 'Vender recarga' : 'Pago de servicios'}</h1>
          <p className="page-sub">{isRecharge ? 'Tiempo aire de todas las compañías de México' : 'Luz, agua, gas, TV, gobierno, créditos y más'}</p>
        </div>
        <button className={`pay-balance-chip ${balance <= 0 ? 'is-empty' : ''}`} onClick={() => navigate('/pay-balance')}>
          <Wallet size={16} /> Saldo disponible <strong>{money(balance)}</strong>
        </button>
      </div>

      {balance <= 0 && (
        <motion.div className="pay-alert" variants={fadeInUp} initial="hidden" animate="visible">
          <TriangleAlert size={18} />
          <div>
            <strong>No tienes saldo para operar.</strong>
            <span> Esta es una corresponsalía prepago: primero reporta un abono y, al aprobarse, tu saldo se carga para poder vender.</span>
          </div>
          <button className="btn-accent pay-alert-cta" onClick={() => navigate('/pay-deposit')}>
            Reportar abono <ArrowRight size={15} />
          </button>
        </motion.div>
      )}

      <div className="pay-sell">
        <motion.div className="card pay-catalog" variants={fadeInUp} initial="hidden" animate="visible">
          <div className="pay-catalog-head">
            <div className="cust-search">
              <Search size={16} />
              <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Buscar compañía o servicio…" />
            </div>
            <span className="pay-count">{totalCount} disponibles</span>
          </div>
          <div className="pay-catalog-list">
            {Object.entries(byCategory).map(([cat, items]) => (
              <div key={cat} className="pay-cat">
                <div className="pay-cat-title">{cat} <span>{items.length}</span></div>
                <div className="pay-providers">
                  {items.map((c) => (
                    <button key={c.id} className={`pay-provider ${selected?.id === c.id ? 'is-on' : ''}`} onClick={() => pick(c)}>
                      <BrandLogo provider={c.provider} color={c.brandColor} logoUrl={c.logoUrl} size={38} />
                      <span className="pay-provider-name">{c.provider}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
            {Object.keys(byCategory).length === 0 && (
              <div className="ret-empty">
                {totalCount === 0 ? 'Sin catálogo disponible.' : 'Sin resultados para tu búsqueda.'}
              </div>
            )}
          </div>
        </motion.div>

        <motion.div className="card pay-form" variants={fadeInUp} initial="hidden" animate="visible">
          {!selected ? (
            <div className="pay-empty">
              <div className="pay-empty-icon">
                {isRecharge ? <Smartphone size={34} strokeWidth={1.5} /> : <Zap size={34} strokeWidth={1.5} />}
              </div>
              <p className="pay-empty-title">Elige una {isRecharge ? 'compañía' : 'servicio'}</p>
              <p className="pay-empty-sub">Selecciona a la izquierda para capturar la operación.</p>
            </div>
          ) : (
            <>
              <div className="pay-form-head">
                <BrandLogo provider={selected.provider} color={selected.brandColor} logoUrl={selected.logoUrl} size={56} />
                <div>
                  <h3 className="pay-form-title">{selected.provider}</h3>
                  <span className="pay-form-cat">{selected.category}</span>
                </div>
              </div>

              <label className="field pay-field">
                <span>{selected.referenceLabel}</span>
                <input
                  value={reference}
                  onChange={(e) => onReferenceChange(e.target.value)}
                  onBlur={() => setTouched(true)}
                  inputMode={rule?.digits ? 'numeric' : 'text'}
                  placeholder={rule?.digits ? '10 dígitos' : ''}
                  autoFocus
                  className={touched && reference && !referenceValid ? 'is-invalid' : ''}
                />
                {rule?.digits && (
                  <small className={`pay-hint ${touched && reference && !referenceValid ? 'is-error' : ''}`}>
                    {refDigits.length}/{rule.digits} dígitos
                  </small>
                )}
              </label>

              {fixed.length > 0 && (
                <div className="pay-amounts">
                  {fixed.map((f) => (
                    <button key={f} className={`pay-amount ${amountNum === f ? 'is-on' : ''}`} onClick={() => setAmount(String(f))}>
                      {money(f)}
                    </button>
                  ))}
                </div>
              )}

              <label className="field pay-field">
                <span>Monto {selected.minAmount > 0 && `(mín. ${money(selected.minAmount)}${selected.maxAmount > 0 ? `, máx. ${money(selected.maxAmount)}` : ''})`}</span>
                <input type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} onBlur={() => setTouched(true)}
                  className={touched && amount !== '' && !amountValid ? 'is-invalid' : ''} />
              </label>

              <div className="pay-breakdown">
                <div><span>Cobra al cliente</span><strong>{money(amountNum)}</strong></div>
                <div><span>Tu comisión</span><strong className="pay-commission">+{money(commission)}</strong></div>
                <div className="pay-breakdown-total"><span>Se descuenta de tu saldo</span><strong>{money(cost)}</strong></div>
              </div>

              {touched && insufficient && amountValid && (
                <div className="pay-inline-alert">
                  <TriangleAlert size={15} /> Saldo insuficiente ({money(balance)}). Reporta un abono.
                </div>
              )}

              <button className="btn-accent pay-sell-btn" disabled={!canSell || busy} onClick={sell}>
                <Check size={16} /> {busy ? 'Procesando…' : isRecharge ? 'Vender recarga' : 'Pagar servicio'}
              </button>
            </>
          )}
        </motion.div>
      </div>

      <AnimatePresence>
        {ticket && (
          <motion.div className="pos-modal-overlay" onClick={() => setTicket(null)}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
            <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
              <div className="pos-modal-head">
                <h3><Receipt size={18} /> Operación exitosa</h3>
                <button className="modal-close" onClick={() => setTicket(null)} aria-label="Cerrar">×</button>
              </div>
              <div className="pos-modal-total"><span>{ticket.provider}</span><strong>{money(ticket.amount)}</strong></div>
              <div className="pay-ticket-body">
                <p><span>Referencia</span><strong>{ticket.reference}</strong></p>
                <p><span>Folio</span><strong>{ticket.folio ?? '—'}</strong></p>
                <p><span>Comisión ganada</span><strong className="pay-commission">{money(ticket.commission)}</strong></p>
              </div>
              <div className="pos-modal-actions">
                <button className="btn-ghost" onClick={() => setTicket(null)}>Cerrar</button>
                <button className="pos-confirm" onClick={() => window.print()}>Imprimir</button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function PaymentRechargePage() { return <PaymentSellPage opType="RECHARGE" />; }
export function PaymentServicePage() { return <PaymentSellPage opType="SERVICE" />; }
