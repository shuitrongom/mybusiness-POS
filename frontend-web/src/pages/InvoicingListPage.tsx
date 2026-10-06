import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { FileText, Search, Ban, FileDown } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Invoice {
  id: number; docType: string; series: string | null; folio: number | null;
  receiverRfc: string; receiverName: string; total: number; status: string;
  uuid: string | null; createdAt: string; error: string | null;
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

function statusBadge(status: string) {
  const map: Record<string, string> = { STAMPED: 'inv-badge-stamped', PENDING: 'inv-badge-pending', CANCELED: 'inv-badge-canceled', ERROR: 'inv-badge-error' };
  const label: Record<string, string> = { STAMPED: 'Timbrada', PENDING: 'Pendiente', CANCELED: 'Cancelada', ERROR: 'Error' };
  return <span className={`inv-badge ${map[status] ?? 'inv-badge-pending'}`}>{label[status] ?? status}</span>;
}

/** Lista de facturas con filtros de fecha y cliente, PDF (XML) y cancelación. */
export function InvoicingListPage() {
  const qc = useQueryClient();
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [client, setClient] = useState('');

  const invoices = useQuery({
    queryKey: ['invoicing', 'invoices', from, to, client],
    queryFn: async () => (await api.get<Invoice[]>('/invoicing/invoices', { params: { kind: 'INVOICE', from, to, client } })).data,
  });

  const cancel = useMutation({
    mutationFn: async (id: number) => api.post(`/invoicing/invoices/${id}/cancel`),
    onSuccess: () => { toast.success('Factura cancelada', 'El CFDI se canceló ante el SAT.'); qc.invalidateQueries({ queryKey: ['invoicing', 'invoices'] }); },
    onError: () => toast.error('No se pudo cancelar', 'Verifica el estado del comprobante.'),
  });

  const rows = invoices.data ?? [];

  return (
    <div>
      <h1 className="page-title">Lista de facturas</h1>
      <p className="page-sub">Consulta, descarga y cancela tus CFDI 4.0</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <label className="field"><span>Fecha inicial</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Fecha final</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <label className="field" style={{ flex: 1, minWidth: 220 }}><span>Cliente (RFC o nombre)</span>
            <div className="cust-search"><Search size={16} /><input value={client} onChange={(e) => setClient(e.target.value)} placeholder="Buscar cliente…" /></div></label>
        </div>

        <div className="inv-table-wrap">
          <table className="inv-table">
            <thead>
              <tr><th>#</th><th>Serie-Folio</th><th>Cliente</th><th>RFC</th><th className="num">Importe</th><th>Estado</th><th>UUID</th><th></th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.id}</td>
                  <td>{r.series ?? '—'}{r.folio != null ? `-${r.folio}` : ''}</td>
                  <td>{r.receiverName}</td>
                  <td>{r.receiverRfc}</td>
                  <td className="num">{money(r.total)}</td>
                  <td>{statusBadge(r.status)}</td>
                  <td style={{ fontSize: 11, color: 'var(--text-muted)' }}>{r.uuid ?? '—'}</td>
                  <td>
                    <div className="inv-actions">
                      {r.uuid && <a className="btn-ghost" href={`/api/v1/invoicing/invoices/${r.id}/xml`} target="_blank" rel="noreferrer"><FileDown size={14} /> XML</a>}
                      {r.status === 'STAMPED' && (
                        <button className="btn-ghost" onClick={() => cancel.mutate(r.id)} disabled={cancel.isPending}><Ban size={14} /> Cancelar</button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={8}><div className="inv-empty"><FileText size={26} /><p>Sin facturas en el rango seleccionado.</p></div></td></tr>}
            </tbody>
          </table>
        </div>
      </motion.div>
    </div>
  );
}
