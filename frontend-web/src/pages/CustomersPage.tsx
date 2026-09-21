import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';
import '@/pages/dashboard.css';

interface LoyaltyBalance {
  balance: number;
}

const money = (n: number): string =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/**
 * Módulo de clientes / CRM: alta de clientes, consulta de saldo de lealtad y
 * acumulación/canje de puntos. El backend aún no expone un listado de clientes,
 * por lo que la pantalla se centra en los formularios y en mostrar resultados.
 */
export function CustomersPage() {
  const [form, setForm] = useState({ name: '', rfc: '', phone: '', email: '', creditLimit: '' });
  const [createdId, setCreatedId] = useState<number | null>(null);

  const [loyaltyId, setLoyaltyId] = useState('');
  const [loyaltyBalance, setLoyaltyBalance] = useState<number | null>(null);

  const [points, setPoints] = useState({ customerId: '', amount: '', reference: '' });
  const [pointsMsg, setPointsMsg] = useState<string | null>(null);

  const createCustomer = useMutation({
    mutationFn: async () =>
      (await api.post<number>('/customers', {
        name: form.name,
        rfc: form.rfc || null,
        phone: form.phone || null,
        email: form.email || null,
        creditLimit: form.creditLimit ? Number(form.creditLimit) : null,
      })).data,
    onSuccess: (id) => {
      setCreatedId(id);
      setForm({ name: '', rfc: '', phone: '', email: '', creditLimit: '' });
    },
  });

  const queryLoyalty = useMutation({
    mutationFn: async () =>
      (await api.get<LoyaltyBalance>(`/customers/${Number(loyaltyId)}/loyalty`)).data,
    onSuccess: (data) => setLoyaltyBalance(data.balance),
  });

  const earn = useMutation({
    mutationFn: async () =>
      api.post(`/customers/${Number(points.customerId)}/loyalty/earn`, {
        amount: Number(points.amount),
        reference: points.reference,
      }),
    onSuccess: () => setPointsMsg(`Se acumularon ${money(Number(points.amount))} en puntos.`),
  });

  const redeem = useMutation({
    mutationFn: async () =>
      api.post(`/customers/${Number(points.customerId)}/loyalty/redeem`, {
        amount: Number(points.amount),
        reference: points.reference,
      }),
    onSuccess: () => setPointsMsg(`Se canjearon ${money(Number(points.amount))} en puntos.`),
  });

  return (
    <div>
      <h1 className="page-title">Clientes</h1>
      <p className="page-sub">Alta de clientes, lealtad y puntos</p>

      <div className="card">
        <h3>Nuevo cliente</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label className="field"><span>RFC</span>
            <input value={form.rfc} onChange={(e) => setForm({ ...form, rfc: e.target.value })} /></label>
          <label className="field"><span>Teléfono</span>
            <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
          <label className="field"><span>Email</span>
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
          <label className="field"><span>Límite de crédito</span>
            <input type="number" step="0.01" value={form.creditLimit} onChange={(e) => setForm({ ...form, creditLimit: e.target.value })} /></label>
          <button className="btn-accent" disabled={!form.name || createCustomer.isPending}
                  onClick={() => createCustomer.mutate()}>
            {createCustomer.isPending ? 'Creando…' : 'Crear'}
          </button>
        </div>
        {createdId !== null && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">Cliente creado</span> Id asignado: <strong>{createdId}</strong>
          </p>
        )}
        {createCustomer.isError && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">No se pudo crear el cliente</span>
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Consultar lealtad</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end', maxWidth: 420 }}>
          <label className="field"><span>Cliente (id)</span>
            <input value={loyaltyId} onChange={(e) => setLoyaltyId(e.target.value)} /></label>
          <button className="btn-primary" disabled={!loyaltyId || queryLoyalty.isPending}
                  onClick={() => queryLoyalty.mutate()}>
            {queryLoyalty.isPending ? 'Consultando…' : 'Consultar'}
          </button>
        </div>
        {loyaltyBalance !== null && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            Saldo de lealtad: <strong>{money(loyaltyBalance)}</strong>
          </p>
        )}
        {queryLoyalty.isError && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">No se pudo consultar el saldo</span>
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Acumular / Canjear puntos</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 2fr auto auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Cliente (id)</span>
            <input value={points.customerId} onChange={(e) => setPoints({ ...points, customerId: e.target.value })} /></label>
          <label className="field"><span>Monto</span>
            <input type="number" step="0.01" value={points.amount} onChange={(e) => setPoints({ ...points, amount: e.target.value })} /></label>
          <label className="field"><span>Referencia</span>
            <input value={points.reference} onChange={(e) => setPoints({ ...points, reference: e.target.value })} /></label>
          <button className="btn-accent" disabled={!points.customerId || !points.amount || earn.isPending}
                  onClick={() => { setPointsMsg(null); earn.mutate(); }}>
            {earn.isPending ? 'Acumulando…' : 'Acumular'}
          </button>
          <button className="btn-ghost" disabled={!points.customerId || !points.amount || redeem.isPending}
                  onClick={() => { setPointsMsg(null); redeem.mutate(); }}>
            {redeem.isPending ? 'Canjeando…' : 'Canjear'}
          </button>
        </div>
        {pointsMsg && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">Listo</span> {pointsMsg}
          </p>
        )}
        {(earn.isError || redeem.isError) && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">No se pudo completar la operación de puntos</span>
          </p>
        )}
      </div>
    </div>
  );
}
