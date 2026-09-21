import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import '@/pages/dashboard.css';

/**
 * Compras y proveedores: alta de proveedor y recepción de compra (que aumenta el inventario y,
 * si es a crédito, genera la cuenta por pagar). Un renglón simple por compra en esta pantalla.
 */
export function PurchasingPage() {
  const [supplier, setSupplier] = useState({ name: '', rfc: '', phone: '', email: '' });
  const [supplierId, setSupplierId] = useState<number | null>(null);

  const [purchase, setPurchase] = useState({
    supplierId: '', branchId: '1', invoiceRef: '', onCredit: false,
    productId: '', quantity: '', unitCost: '',
  });
  const [purchaseMsg, setPurchaseMsg] = useState<string | null>(null);

  const createSupplier = useMutation({
    mutationFn: async () =>
      (await api.post<number>('/purchasing/suppliers', {
        name: supplier.name, rfc: supplier.rfc || null,
        phone: supplier.phone || null, email: supplier.email || null,
      })).data,
    onSuccess: (id) => {
      setSupplierId(id);
      setSupplier({ name: '', rfc: '', phone: '', email: '' });
    },
  });

  const receivePurchase = useMutation({
    mutationFn: async () =>
      (await api.post<number>('/purchasing/purchases', {
        supplierId: Number(purchase.supplierId),
        branchId: Number(purchase.branchId),
        invoiceRef: purchase.invoiceRef || null,
        onCredit: purchase.onCredit,
        lines: [{
          productId: Number(purchase.productId),
          quantity: Number(purchase.quantity),
          unitCost: Number(purchase.unitCost),
        }],
      })).data,
    onSuccess: (id) => {
      setPurchaseMsg(`Compra #${id} recibida. Inventario actualizado.`);
      setPurchase({ ...purchase, invoiceRef: '', productId: '', quantity: '', unitCost: '' });
    },
  });

  const total = (Number(purchase.quantity) || 0) * (Number(purchase.unitCost) || 0);

  return (
    <div>
      <h1 className="page-title">Compras</h1>
      <p className="page-sub">Proveedores y recepción de mercancía</p>

      <div className="card">
        <h3>Nuevo proveedor</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Nombre</span>
            <input value={supplier.name} onChange={(e) => setSupplier({ ...supplier, name: e.target.value })} /></label>
          <label className="field"><span>RFC</span>
            <input value={supplier.rfc} onChange={(e) => setSupplier({ ...supplier, rfc: e.target.value })} /></label>
          <label className="field"><span>Teléfono</span>
            <input value={supplier.phone} onChange={(e) => setSupplier({ ...supplier, phone: e.target.value })} /></label>
          <label className="field"><span>Email</span>
            <input value={supplier.email} onChange={(e) => setSupplier({ ...supplier, email: e.target.value })} /></label>
          <button className="btn-accent" disabled={!supplier.name || createSupplier.isPending}
                  onClick={() => createSupplier.mutate()}>Crear</button>
        </div>
        {supplierId !== null && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">Proveedor creado</span> Id: <strong>{supplierId}</strong>
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Recibir compra</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
          <label className="field"><span>Proveedor (id)</span>
            <input value={purchase.supplierId} onChange={(e) => setPurchase({ ...purchase, supplierId: e.target.value })} /></label>
          <label className="field"><span>Sucursal (id)</span>
            <input value={purchase.branchId} onChange={(e) => setPurchase({ ...purchase, branchId: e.target.value })} /></label>
          <label className="field"><span>Factura / referencia</span>
            <input value={purchase.invoiceRef} onChange={(e) => setPurchase({ ...purchase, invoiceRef: e.target.value })} /></label>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Producto (id)</span>
            <input value={purchase.productId} onChange={(e) => setPurchase({ ...purchase, productId: e.target.value })} /></label>
          <label className="field"><span>Cantidad</span>
            <input type="number" step="0.001" value={purchase.quantity} onChange={(e) => setPurchase({ ...purchase, quantity: e.target.value })} /></label>
          <label className="field"><span>Costo unitario</span>
            <input type="number" step="0.01" value={purchase.unitCost} onChange={(e) => setPurchase({ ...purchase, unitCost: e.target.value })} /></label>
          <button className="btn-accent" disabled={!purchase.supplierId || !purchase.productId || !purchase.quantity || receivePurchase.isPending}
                  onClick={() => receivePurchase.mutate()}>Recibir</button>
        </div>
        <label className="field" style={{ flexDirection: 'row', alignItems: 'center', gap: 'var(--space-2)', marginTop: 'var(--space-3)', maxWidth: 260 }}>
          <input type="checkbox" style={{ width: 'auto' }} checked={purchase.onCredit}
                 onChange={(e) => setPurchase({ ...purchase, onCredit: e.target.checked })} />
          <span style={{ color: 'var(--text)' }}>Compra a crédito (genera cuenta por pagar)</span>
        </label>
        <p className="page-sub" style={{ margin: 'var(--space-3) 0 0' }}>
          Total de la compra: <strong>{money(total)}</strong>
        </p>
        {purchaseMsg && (
          <p style={{ marginTop: 'var(--space-2)' }}>
            <span className="badge badge-success">Recibida</span> {purchaseMsg}
          </p>
        )}
      </div>
    </div>
  );
}
