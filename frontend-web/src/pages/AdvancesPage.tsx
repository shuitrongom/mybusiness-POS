import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Wallet, Search, Plus, ArrowDownCircle, ArrowUpCircle, PiggyBank } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/customers.css';
import '@/pages/advances.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Customer { id: number; name: string; rfc: string | null; }
interface Movement {
  id: number;
  direction: string;
  amount: number;
  balanceAfter: number;
  method: string | null;
  saleId: number | null;
  reference: string | null;
  actor: string | null;
  createdAt: string;
}

const DIR_LABEL: Record<string, string> = { DEPOSIT: 'Depósito', APPLY: 'Aplicado a venta', REFUND: 'Devolución' };

/**
 * Anticipos de clientes: dinero entregado por adelantado. Busca el cliente, muestra su saldo,
 * permite depositar o devolver saldo y muestra el historial de movimientos.
 */
export function AdvancesPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Customer[]>([]);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [balance, setBalance] = useState(0);
  const [movements, setMovements] = useState<Movement[]>([]);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('CASH');
  const [reference, setReference] = useState('');
  const [busy, setBusy] = useState(false);

  // Búsqueda de clientes con debounce.
  useEffect(() => {
    let cancelled = false;
    const t = setTimeout(async () => {
      if (!query.trim()) { setResults([]); return; }
      try {
        const { data } = await api.get<Customer[]>('/customers', { params: { q: query, limit: 20 } });
        if (!cancelled) setResults(data);
      } catch { if (!cancelled) setResults([]); }
    }, 250);
    return () => { cancelled = true; clearTimeout(t); };
  }, [query]);

  const loadCustomer = async (c: Customer) => {
    setCustomer(c);
    setResults([]);
    setQuery('');
    await refresh(c.id);
  };

  const refresh = async (id: number) => {
    try {
      const [bal, mov] = await Promise.all([
        api.get<{ balance: number }>(`/customers/${id}/advance`),
        api.get<Movement[]>(`/customers/${id}/advance/movements`, { params: { limit: 30 } }),
      ]);
      setBalance(bal.data.balance);
      setMovements(mov.data);
    } catch {
      toast.error('No se pudo cargar el anticipo del cliente');
    }
  };

  const run = async (kind: 'deposit' | 'refund') => {
    if (!customer) return;
    const amt = Number(amount);
    if (!amt || amt <= 0) { toast.info('Escribe un monto válido'); return; }
    setBusy(true);
    try {
      if (kind === 'deposit') {
        await api.post(`/customers/${customer.id}/advance/deposit`, { amount: amt, method, reference: reference || null });
        toast.success('Anticipo registrado', `Se depositaron ${money(amt)}.`);
      } else {
        await api.post(`/customers/${customer.id}/advance/refund`, { amount: amt, reference: reference || null });
        toast.success('Devolución registrada', `Se devolvieron ${money(amt)}.`);
      }
      setAmount(''); setReference('');
      await refresh(customer.id);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      toast.error('No se pudo completar la operación', msg || 'Revisa el saldo y el monto.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Anticipos de clientes</h1>
      <p className="page-sub">Dinero entregado por adelantado que se aplica a compras futuras</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Search size={18} /> Buscar cliente</h3>
        <div className="adv-search">
          <input value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="Nombre, RFC o teléfono del cliente…" />
        </div>
        {results.length > 0 && (
          <div className="adv-results">
            {results.map((c) => (
              <button key={c.id} className="adv-result" onClick={() => loadCustomer(c)}>
                <strong>{c.name}</strong>{c.rfc && <span> · {c.rfc}</span>}
              </button>
            ))}
          </div>
        )}
      </motion.div>

      {customer && (
        <>
          <motion.div className="card adv-balance-card" style={{ marginTop: 'var(--space-4)' }}
            variants={fadeInUp} initial="hidden" animate="visible">
            <div className="adv-balance-info">
              <span className="adv-balance-icon"><PiggyBank size={26} /></span>
              <div>
                <div className="adv-balance-name">{customer.name}</div>
                <div className="adv-balance-label">Saldo de anticipo disponible</div>
              </div>
            </div>
            <div className="adv-balance-amount">{money(balance)}</div>
          </motion.div>

          <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
            variants={fadeInUp} initial="hidden" animate="visible">
            <h3 className="sec-title"><Wallet size={18} /> Registrar movimiento</h3>
            <div className="adv-form">
              <label className="field">
                <span>Monto</span>
                <input type="number" min={0} step="0.01" value={amount}
                  onChange={(e) => setAmount(e.target.value)} placeholder="0.00" />
              </label>
              <label className="field">
                <span>Método (depósito)</span>
                <select value={method} onChange={(e) => setMethod(e.target.value)}>
                  <option value="CASH">Efectivo</option>
                  <option value="CARD">Tarjeta</option>
                  <option value="TRANSFER">Transferencia</option>
                </select>
              </label>
              <label className="field">
                <span>Referencia</span>
                <input value={reference} onChange={(e) => setReference(e.target.value)}
                  placeholder="Pedido, folio…" />
              </label>
            </div>
            <div className="adv-actions">
              <button className="btn-accent" disabled={busy} onClick={() => run('deposit')}>
                <ArrowDownCircle size={16} /> Depositar anticipo
              </button>
              <button className="btn-ghost" disabled={busy || balance <= 0} onClick={() => run('refund')}>
                <ArrowUpCircle size={16} /> Devolver saldo
              </button>
            </div>
          </motion.div>

          <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
            variants={fadeInUp} initial="hidden" animate="visible">
            <h3 className="sec-title"><Plus size={18} /> Movimientos</h3>
            <table className="ret-table">
              <thead>
                <tr>
                  <th>Tipo</th><th>Método</th><th>Venta</th><th>Referencia</th>
                  <th className="ta-right">Monto</th><th className="ta-right">Saldo</th>
                </tr>
              </thead>
              <tbody>
                {movements.map((m) => (
                  <tr key={m.id}>
                    <td>
                      <span className={`adv-dir adv-dir-${m.direction.toLowerCase()}`}>
                        {DIR_LABEL[m.direction] || m.direction}
                      </span>
                    </td>
                    <td>{m.method || '—'}</td>
                    <td>{m.saleId ? `V-${m.saleId}` : '—'}</td>
                    <td>{m.reference || <span className="muted">—</span>}</td>
                    <td className="ta-right">{money(m.amount)}</td>
                    <td className="ta-right">{money(m.balanceAfter)}</td>
                  </tr>
                ))}
                {movements.length === 0 && (
                  <tr><td colSpan={6} className="ret-empty">Sin movimientos de anticipo.</td></tr>
                )}
              </tbody>
            </table>
          </motion.div>
        </>
      )}
    </div>
  );
}
