import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ShieldAlert, Check, X } from 'lucide-react';
import { api } from '@/lib/api';
import '@/pages/pos.css';

interface SupervisorAuthModalProps {
  open: boolean;
  action: string;
  onClose: () => void;
  onAuthorized: (by: string) => void;
}

/**
 * Modal reutilizable de autorización por PIN de supervisor. Pide el PIN, lo valida contra
 * {@code /authorize/supervisor} y, si es correcto, ejecuta la acción autorizada. Se usa para
 * acciones sensibles del POS (cancelar venta, descuento mayor al permitido, devolución).
 */
export function SupervisorAuthModal({ open, action, onClose, onAuthorized }: SupervisorAuthModalProps) {
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (pin.length < 4) { setError('El PIN debe tener al menos 4 dígitos.'); return; }
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post<{ authorized: boolean; by?: string }>('/authorize/supervisor', { pin, action });
      if (data.authorized) {
        setPin('');
        onAuthorized(data.by ?? 'Supervisor');
      } else {
        setError('PIN no autorizado.');
      }
    } catch {
      setError('PIN no autorizado.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pos-modal-overlay" onClick={onClose}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
            <div className="pos-modal-head">
              <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><ShieldAlert size={18} /> Autorización requerida</h3>
              <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
            </div>
            <div style={{ padding: 'var(--space-4) var(--space-5)' }}>
              <p style={{ marginTop: 0, color: 'var(--text-muted)' }}>
                La acción <strong>{action}</strong> requiere el PIN de un supervisor.
              </p>
              <label className="field"><span>PIN de supervisor</span>
                <input type="password" inputMode="numeric" autoFocus value={pin}
                  onChange={(e) => { setPin(e.target.value.replace(/\D/g, '').slice(0, 8)); setError(null); }}
                  onKeyDown={(e) => { if (e.key === 'Enter') submit(); }} />
              </label>
              {error && <p style={{ color: 'var(--danger)', fontSize: 'var(--fs-sm)', margin: '6px 0 0' }}>{error}</p>}
            </div>
            <div className="pos-modal-actions">
              <button className="btn-ghost" onClick={onClose}><X size={15} /> Cancelar</button>
              <button className="pos-confirm" onClick={submit} disabled={busy}><Check size={15} /> {busy ? 'Verificando…' : 'Autorizar'}</button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
