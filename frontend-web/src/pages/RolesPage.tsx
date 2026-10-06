import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { ShieldCheck, Plus, Save, KeyRound } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Role {
  id: number; code: string; name: string; systemRole: boolean;
  maxDiscountPct: number; canAuthorize: boolean;
}
interface CatalogEntry { key: string; name: string; }
interface Catalog { modules: CatalogEntry[]; actions: CatalogEntry[]; }
interface Perm { moduleKey: string; action: string; }

/** Acciones sensibles del cajero que el admin activa (libre) o desactiva (pide autorización). */
const SENSITIVE: { module: string; action: string; label: string }[] = [
  { module: 'sales', action: 'VOID', label: 'Cancelar venta' },
  { module: 'sales', action: 'RETURN', label: 'Hacer devoluciones' },
  { module: 'sales', action: 'DISCOUNT', label: 'Aplicar descuentos' },
  { module: 'sales', action: 'REPRINT', label: 'Reimprimir tickets' },
];

/**
 * Roles y permisos: crea roles, edita la matriz de permisos (módulo x acción) de cada rol y sus
 * límites (descuento máximo, si puede autorizar). Es el centro del control de acceso del negocio.
 */
export function RolesPage() {
  const qc = useQueryClient();
  const [selected, setSelected] = useState<Role | null>(null);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [maxDiscount, setMaxDiscount] = useState('0');
  const [canAuthorize, setCanAuthorize] = useState(false);
  const [newRole, setNewRole] = useState({ code: '', name: '' });
  const [myPin, setMyPin] = useState('');

  const roles = useQuery({ queryKey: ['roles'], queryFn: async () => (await api.get<Role[]>('/roles')).data });
  const catalog = useQuery({ queryKey: ['roles', 'catalog'], queryFn: async () => (await api.get<Catalog>('/roles/catalog')).data });

  const loadPerms = async (role: Role) => {
    setSelected(role);
    setMaxDiscount(String(role.maxDiscountPct ?? 0));
    setCanAuthorize(!!role.canAuthorize);
    const { data } = await api.get<Perm[]>(`/roles/${role.id}/permissions`);
    setChecked(new Set(data.map((p) => `${p.moduleKey}:${p.action}`)));
  };

  const key = (m: string, a: string) => `${m}:${a}`;
  const toggle = (m: string, a: string) => {
    setChecked((cur) => {
      const next = new Set(cur);
      const k = key(m, a);
      if (next.has(k)) next.delete(k); else next.add(k);
      return next;
    });
  };
  const toggleModuleRow = (m: string, allActions: string[]) => {
    setChecked((cur) => {
      const next = new Set(cur);
      const allOn = allActions.every((a) => next.has(key(m, a)));
      allActions.forEach((a) => { if (allOn) next.delete(key(m, a)); else next.add(key(m, a)); });
      return next;
    });
  };

  const save = async () => {
    if (!selected) return;
    const permissions = Array.from(checked).map((k) => {
      const [moduleKey, action] = k.split(':');
      return { moduleKey, action };
    });
    try {
      await api.post(`/roles/${selected.id}/permissions/replace`, { permissions });
      await api.post(`/roles/${selected.id}/limits`, { maxDiscountPct: Number(maxDiscount), canAuthorize });
      toast.success('Rol actualizado', `Permisos y límites de ${selected.name} guardados.`);
      qc.invalidateQueries({ queryKey: ['roles'] });
    } catch {
      toast.error('No se pudo guardar', 'Revisa los permisos.');
    }
  };

  const createRole = async () => {
    if (!newRole.code || !newRole.name) { toast.error('Faltan datos', 'Código y nombre del rol.'); return; }
    try {
      await api.post('/roles', { code: newRole.code.toUpperCase(), name: newRole.name });
      toast.success('Rol creado', newRole.name);
      setNewRole({ code: '', name: '' });
      qc.invalidateQueries({ queryKey: ['roles'] });
    } catch {
      toast.error('No se pudo crear', 'El código puede ya existir.');
    }
  };

  const savePin = async () => {
    if (myPin.length < 4) { toast.error('PIN inválido', 'El PIN debe tener al menos 4 dígitos.'); return; }
    try {
      await api.post('/authorize/set-pin', { pin: myPin });
      toast.success('PIN guardado', 'Podrás autorizar acciones sensibles con este PIN.');
      setMyPin('');
    } catch {
      toast.error('No se pudo guardar el PIN', '');
    }
  };

  useEffect(() => {
    if (!selected && roles.data && roles.data.length > 0) loadPerms(roles.data[0]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roles.data]);

  const cat = catalog.data;
  const modules = useMemo(() => cat?.modules ?? [], [cat]);
  const actions = useMemo(() => cat?.actions ?? [], [cat]);

  return (
    <div>
      <h1 className="page-title">Roles y permisos</h1>
      <p className="page-sub">Define qué puede hacer cada rol. Marca por módulo y acción; ajusta límites de descuento y autorización.</p>

      <div className="inv-grid2" style={{ alignItems: 'start', gridTemplateColumns: '280px 1fr' }}>
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><ShieldCheck size={18} /> Roles</h3>
          <div style={{ marginTop: 'var(--space-3)', display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(roles.data ?? []).map((r) => (
              <button key={r.id} className={`inv-cat-tab ${selected?.id === r.id ? 'is-on' : ''}`} style={{ justifyContent: 'space-between', display: 'flex' }} onClick={() => loadPerms(r)}>
                {r.name} {r.systemRole && <span style={{ fontSize: 10, opacity: 0.7 }}>sistema</span>}
              </button>
            ))}
          </div>

          <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}><Plus size={16} /> Nuevo rol</h3>
          <label className="field" style={{ marginTop: 'var(--space-2)' }}><span>Código</span>
            <input value={newRole.code} onChange={(e) => setNewRole({ ...newRole, code: e.target.value })} placeholder="VENDEDOR" /></label>
          <label className="field"><span>Nombre</span>
            <input value={newRole.name} onChange={(e) => setNewRole({ ...newRole, name: e.target.value })} placeholder="Vendedor de piso" /></label>
          <button className="btn-ghost" onClick={createRole}><Plus size={14} /> Crear rol</button>

          <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}><KeyRound size={16} /> Mi PIN de supervisor</h3>
          <p className="page-sub" style={{ margin: '0 0 8px' }}>Para autorizar acciones sensibles en el POS.</p>
          <label className="field"><span>PIN (mín. 4 dígitos)</span>
            <input type="password" inputMode="numeric" value={myPin} onChange={(e) => setMyPin(e.target.value.replace(/\D/g, '').slice(0, 8))} /></label>
          <button className="btn-ghost" onClick={savePin}><Save size={14} /> Guardar PIN</button>
        </motion.div>

        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          {!selected ? <div className="inv-empty">Selecciona un rol.</div> : (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3 className="sec-title" style={{ margin: 0 }}>Permisos de {selected.name}</h3>
                <button className="btn-accent" onClick={save}><Save size={16} /> Guardar</button>
              </div>

              <div className="inv-grid3" style={{ marginTop: 'var(--space-3)' }}>
                <label className="field"><span>Descuento máximo (%)</span>
                  <input type="number" step="0.01" value={maxDiscount} onChange={(e) => setMaxDiscount(e.target.value)} /></label>
                <label className="field" style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 22 }}>
                  <input type="checkbox" checked={canAuthorize} onChange={(e) => setCanAuthorize(e.target.checked)} style={{ width: 18, height: 18 }} />
                  <span style={{ margin: 0 }}>Puede autorizar acciones de otros</span></label>
              </div>

              {/* Acciones sensibles: toggles claros. Activado = el rol lo hace LIBRE (sin PIN);
                  desactivado = requiere autorización de un supervisor. */}
              <div className="role-sensitive">
                <div className="role-sensitive-title">Acciones del cajero (activa = libre, sin autorización)</div>
                <div className="role-toggles">
                  {SENSITIVE.map((s) => {
                    const on = checked.has(key(s.module, s.action));
                    return (
                      <button key={s.action} className={`role-toggle ${on ? 'is-on' : ''}`} onClick={() => toggle(s.module, s.action)}>
                        <span className="role-toggle-switch"><span className="role-toggle-dot" /></span>
                        <span className="role-toggle-text">
                          <span className="role-toggle-label">{s.label}</span>
                          <span className="role-toggle-hint">{on ? 'Libre para este rol' : 'Pide autorización'}</span>
                        </span>
                      </button>
                    );
                  })}
                </div>
                <button className="btn-accent" style={{ marginTop: 'var(--space-3)' }} onClick={save}>
                  <Save size={16} /> Guardar cambios
                </button>
              </div>

              <details className="role-advanced">
                <summary>Permisos avanzados (matriz completa)</summary>
              <div className="inv-table-wrap" style={{ marginTop: 'var(--space-3)' }}>
                <table className="inv-table">
                  <thead>
                    <tr>
                      <th>Módulo</th>
                      {actions.map((a) => <th key={a.key} style={{ textAlign: 'center' }}>{a.name}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {modules.map((m) => {
                      const allActions = actions.map((a) => a.key);
                      const allOn = allActions.every((a) => checked.has(key(m.key, a)));
                      return (
                        <tr key={m.key}>
                          <td>
                            <button className="btn-ghost" style={{ padding: '2px 8px', fontSize: 12 }} onClick={() => toggleModuleRow(m.key, allActions)}>
                              {allOn ? '−' : '+'}
                            </button> {m.name}
                          </td>
                          {actions.map((a) => (
                            <td key={a.key} style={{ textAlign: 'center' }}>
                              <input type="checkbox" checked={checked.has(key(m.key, a.key))} onChange={() => toggle(m.key, a.key)} style={{ width: 16, height: 16 }} />
                            </td>
                          ))}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              </details>
            </>
          )}
        </motion.div>
      </div>
    </div>
  );
}
