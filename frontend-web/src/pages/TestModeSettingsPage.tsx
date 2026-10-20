import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  FlaskConical, ShieldAlert, Play, Square, Monitor, Smartphone, HelpCircle, History,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, pressable } from '@/lib/motion';
import { useSession } from '@/store/session';
import { decodeToken, isOwner } from '@/lib/jwt';
import { InfoBanner } from '@/components/InfoBanner';
import './dashboard.css';
import './test-mode.css';

interface AuditEvent {
  actor: string | null;
  action: 'ENABLE_TEST_MODE' | 'DISABLE_TEST_MODE' | string;
  device: string;
  userAgent: string;
  at: string | null;
}
interface TestModeStatus {
  active: boolean;
  businessId: number;
  recentChanges: AuditEvent[];
}

const fmtDateTime = (iso: string | null): string => {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('es-MX', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
};

/**
 * Configuración del MODO PRUEBA (solo el DUEÑO). Mientras está ACTIVO, cada venta
 * nueva se registra como PRÁCTICA (is_test) pero se conserva como histórico (Opción
 * B). El dueño lo FINALIZA manualmente: desde ese instante las ventas son reales.
 * Muestra la bitácora de auditoría (quién, cuándo, desde qué dispositivo).
 */
export function TestModeSettingsPage() {
  const token = useSession((s) => s.token);
  const owner = isOwner(decodeToken(token));
  const queryClient = useQueryClient();
  const [confirmOff, setConfirmOff] = useState(false);

  const status = useQuery({
    queryKey: ['settings', 'test-mode'],
    queryFn: async () => (await api.get<TestModeStatus>('/settings/test-mode')).data,
    enabled: owner,
  });

  const mutate = useMutation({
    mutationFn: async (active: boolean) =>
      (await api.put<TestModeStatus>('/settings/test-mode', { active })).data,
    onSuccess: (data) => {
      queryClient.setQueryData(['settings', 'test-mode'], data);
      queryClient.invalidateQueries({ queryKey: ['settings', 'test-mode'] });
      queryClient.invalidateQueries({ queryKey: ['settings', 'test-mode', 'active'] });
      toast.success(
        data.active ? 'Modo prueba iniciado' : 'Modo prueba finalizado',
        data.active
          ? 'Las ventas nuevas se registran como práctica.'
          : 'Desde ahora las ventas son reales. Las de prueba se conservan como histórico.',
      );
      setConfirmOff(false);
    },
    onError: () => toast.error('No se pudo cambiar el modo prueba', 'Inténtalo de nuevo.'),
  });

  // Gate de render: solo el dueño. El backend lo refuerza con 403.
  if (!owner) {
    return (
      <div>
        <div className="sec-title"><FlaskConical size={20} /><h1 className="page-title">Modo prueba</h1></div>
        <motion.div className="card tm-blocked" variants={fadeInUp} initial="hidden" animate="visible">
          <ShieldAlert size={40} strokeWidth={1.5} />
          <h3>Solo el dueño puede administrar el modo prueba</h3>
          <p>Esta configuración corta las ventas de práctica a reales, por eso la controla únicamente el dueño del negocio.</p>
        </motion.div>
      </div>
    );
  }

  const active = status.data?.active ?? false;
  const changes = status.data?.recentChanges ?? [];

  return (
    <div>
      <div className="sec-title"><FlaskConical size={20} /><h1 className="page-title">Modo prueba</h1></div>
      <p className="page-sub">Registra ventas de práctica sin mezclarlas con tu histórico real</p>

      <div style={{ marginTop: 'var(--space-3)' }}>
        {active ? (
          <InfoBanner icon={<FlaskConical size={18} />} tone="amber" title="MODO PRUEBA ACTIVO">
            Las ventas nuevas se registran como práctica (no afectan tu histórico real). Cuando
            termines de capacitar, finaliza el modo prueba: desde ese momento las ventas son reales.
          </InfoBanner>
        ) : (
          <InfoBanner icon={<FlaskConical size={18} />} tone="sky" title="Modo prueba">
            Actívalo para capacitar a tus cajeras: las ventas quedarán marcadas como práctica y
            separadas del histórico real. Las ventas reales no se ven afectadas.
          </InfoBanner>
        )}
      </div>

      <motion.div className="card tm-panel" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="tm-state">
          <span className={`tm-state-chip ${active ? 'is-on' : 'is-off'}`}>
            <span className="tm-dot" />
            {active ? 'Modo prueba ACTIVO' : 'Operación real'}
          </span>
          <p className="tm-state-text">
            {active
              ? 'Cada venta y corte nuevo se guarda como prueba. El inventario y los cortes NO se borran ni se resetean.'
              : 'Todas las ventas nuevas se registran como histórico real.'}
          </p>
        </div>

        {active ? (
          confirmOff ? (
            <div className="tm-confirm">
              <strong>¿Finalizar el modo prueba?</strong>
              <p>
                A partir de ahora las ventas serán <b>REALES</b>. Las ventas de prueba se conservan
                como histórico (no se borran). Esta acción queda registrada en la bitácora.
              </p>
              <div className="tm-confirm-actions">
                <button className="btn-ghost" onClick={() => setConfirmOff(false)} disabled={mutate.isPending}>
                  Cancelar
                </button>
                <motion.button className="btn-accent tm-danger" disabled={mutate.isPending}
                  onClick={() => mutate.mutate(false)}
                  whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                  <Square size={16} /> {mutate.isPending ? 'Finalizando…' : 'Sí, finalizar modo prueba'}
                </motion.button>
              </div>
            </div>
          ) : (
            <motion.button className="btn-accent tm-danger" disabled={mutate.isPending}
              onClick={() => setConfirmOff(true)}
              whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
              <Square size={16} /> Finalizar modo prueba
            </motion.button>
          )
        ) : (
          <motion.button className="btn-accent" disabled={mutate.isPending}
            onClick={() => mutate.mutate(true)}
            whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
            <Play size={16} /> {mutate.isPending ? 'Iniciando…' : 'Iniciar modo prueba'}
          </motion.button>
        )}
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <div className="sec-title"><History size={18} /><h3>Bitácora de cambios</h3></div>
        <p className="page-sub" style={{ marginTop: 0 }}>Quién activó o finalizó el modo prueba, cuándo y desde qué dispositivo</p>

        {status.isLoading ? (
          <p className="tm-muted">Cargando bitácora…</p>
        ) : changes.length === 0 ? (
          <div className="tm-empty">
            <span className="tm-empty-icon"><History size={26} /></span>
            <strong>Sin cambios registrados todavía</strong>
            <p>Cuando inicies o finalices el modo prueba, el registro aparecerá aquí.</p>
          </div>
        ) : (
          <div className="tm-log">
            {changes.map((e, i) => {
              const enabled = e.action === 'ENABLE_TEST_MODE';
              const mobile = e.device === 'mobile';
              return (
                <div className="tm-log-row" key={i}>
                  <span className={`tm-log-action ${enabled ? 'is-on' : 'is-off'}`}>
                    {enabled ? <Play size={13} /> : <Square size={13} />}
                    {enabled ? 'Inició prueba' : 'Finalizó prueba'}
                  </span>
                  <span className="tm-log-actor">{e.actor || '—'}</span>
                  <span className={`tm-chip-device ${mobile ? 'is-mobile' : 'is-desktop'}`}>
                    {mobile ? <Smartphone size={12} /> : <Monitor size={12} />}
                    {mobile ? 'Móvil' : e.device === 'desktop' ? 'Computadora' : 'Desconocido'}
                  </span>
                  <span className="tm-log-when num">{fmtDateTime(e.at)}</span>
                </div>
              );
            })}
          </div>
        )}

        <p className="tm-ua-hint">
          <HelpCircle size={13} /> El dispositivo se detecta del navegador que hizo el cambio.
        </p>
      </motion.div>
    </div>
  );
}
