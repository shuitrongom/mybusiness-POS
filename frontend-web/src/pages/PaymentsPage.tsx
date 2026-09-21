import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import '@/pages/dashboard.css';

interface PaymentResult {
  operationId: number | null;
  status: string;
  folio: string | null;
  commission: number | null;
  error: string | null;
}

interface CommissionsTotal {
  total: number;
}

const CARRIERS = ['Telcel', 'Movistar', 'AT&T'];
const BILLERS = ['CFE', 'Telmex', 'Sky', 'Izzi', 'Gas'];

/** Extrae un mensaje de error legible de una respuesta de axios (p.ej. 422 del backend). */
function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as { error?: string; message?: string } | undefined;
    return data?.error ?? data?.message ?? err.message;
  }
  return 'Ocurrió un error inesperado.';
}

/**
 * Recargas de tiempo aire y pago de servicios. Muestra folio y comisión en operaciones exitosas
 * y el mensaje de error del backend cuando la operación falla (422). Consulta el total de comisiones.
 */
export function PaymentsPage() {
  const queryClient = useQueryClient();

  const [recharge, setRecharge] = useState({ carrier: 'Telcel', phone: '', amount: '' });
  const [rechargeResult, setRechargeResult] = useState<PaymentResult | null>(null);
  const [rechargeError, setRechargeError] = useState<string | null>(null);

  const [service, setService] = useState({ biller: 'CFE', reference: '', amount: '' });
  const [serviceResult, setServiceResult] = useState<PaymentResult | null>(null);
  const [serviceError, setServiceError] = useState<string | null>(null);

  const commissions = useQuery({
    queryKey: ['payments', 'commissions', 'total'],
    queryFn: async () => (await api.get<CommissionsTotal>('/payments/commissions/total')).data,
  });

  const sellRecharge = useMutation({
    mutationFn: async () =>
      (await api.post<PaymentResult>('/payments/recharge', {
        carrier: recharge.carrier,
        phone: recharge.phone,
        amount: Number(recharge.amount),
        branchId: 1,
      })).data,
    onSuccess: (data) => {
      setRechargeResult(data);
      setRechargeError(null);
      queryClient.invalidateQueries({ queryKey: ['payments', 'commissions', 'total'] });
    },
    onError: (err) => {
      setRechargeResult(null);
      setRechargeError(errorMessage(err));
    },
  });

  const payService = useMutation({
    mutationFn: async () =>
      (await api.post<PaymentResult>('/payments/service', {
        biller: service.biller,
        reference: service.reference,
        amount: Number(service.amount),
        branchId: 1,
      })).data,
    onSuccess: (data) => {
      setServiceResult(data);
      setServiceError(null);
      queryClient.invalidateQueries({ queryKey: ['payments', 'commissions', 'total'] });
    },
    onError: (err) => {
      setServiceResult(null);
      setServiceError(errorMessage(err));
    },
  });

  return (
    <div>
      <h1 className="page-title">Recargas</h1>
      <p className="page-sub">Recargas de tiempo aire y pago de servicios</p>

      <div className="card">
        <h3>Vender recarga</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Compañía</span>
            <select value={recharge.carrier} onChange={(e) => setRecharge({ ...recharge, carrier: e.target.value })}>
              {CARRIERS.map((c) => <option key={c} value={c}>{c}</option>)}
            </select></label>
          <label className="field"><span>Teléfono</span>
            <input value={recharge.phone} onChange={(e) => setRecharge({ ...recharge, phone: e.target.value })} /></label>
          <label className="field"><span>Monto</span>
            <input type="number" step="0.01" value={recharge.amount} onChange={(e) => setRecharge({ ...recharge, amount: e.target.value })} /></label>
          <button className="btn-accent" disabled={!recharge.phone || !recharge.amount || sellRecharge.isPending}
                  onClick={() => sellRecharge.mutate()}>
            {sellRecharge.isPending ? 'Vendiendo…' : 'Vender'}
          </button>
        </div>
        {rechargeResult && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">{rechargeResult.status}</span>{' '}
            Folio: <strong>{rechargeResult.folio ?? '—'}</strong>{' · '}
            Comisión: <strong>{money(rechargeResult.commission ?? 0)}</strong>
          </p>
        )}
        {rechargeError && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">Error</span> {rechargeError}
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Pago de servicio</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Servicio</span>
            <select value={service.biller} onChange={(e) => setService({ ...service, biller: e.target.value })}>
              {BILLERS.map((b) => <option key={b} value={b}>{b}</option>)}
            </select></label>
          <label className="field"><span>Referencia</span>
            <input value={service.reference} onChange={(e) => setService({ ...service, reference: e.target.value })} /></label>
          <label className="field"><span>Monto</span>
            <input type="number" step="0.01" value={service.amount} onChange={(e) => setService({ ...service, amount: e.target.value })} /></label>
          <button className="btn-accent" disabled={!service.reference || !service.amount || payService.isPending}
                  onClick={() => payService.mutate()}>
            {payService.isPending ? 'Pagando…' : 'Pagar'}
          </button>
        </div>
        {serviceResult && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">{serviceResult.status}</span>{' '}
            Folio: <strong>{serviceResult.folio ?? '—'}</strong>{' · '}
            Comisión: <strong>{money(serviceResult.commission ?? 0)}</strong>
          </p>
        )}
        {serviceError && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">Error</span> {serviceError}
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Comisiones</h3>
        <p className="metric-value" style={{ marginTop: 'var(--space-3)' }}>
          {money(commissions.data?.total ?? 0)}
        </p>
        <p className="page-sub" style={{ margin: 0 }}>Total acumulado de comisiones generadas.</p>
      </div>
    </div>
  );
}
