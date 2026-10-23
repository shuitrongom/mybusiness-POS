import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  FlaskConical, ShieldAlert, Play, Square, Monitor, Smartphone, HelpCircle, History,
  CheckCircle2, Archive, PackageX, Recycle, X, AlertTriangle,
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

interface CutoverSummary {
  executed: boolean;
  active: boolean;
  businessId: number;
  practice: { salesKept: number; cashCutsKept: number };
  stock: {
    rowsAdjusted: number;
    totalUnitsRemoved: number;
    lines: { productId: number; branchId: number; previousQuantity: number }[];
  };
  waste: { totalPieces: number; productCount: number; wasteValue: number };
  recentChanges: AuditEvent[];
}

const fmtNum = (n: number | null | undefined): string => {
  if (n == null || Number.isNaN(Number(n))) return '0';
  return Number(n).toLocaleString('es-MX', { maximumFractionDigits: 2 });
};

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
  const [summary, setSummary] = useState<CutoverSummary | null>(null);

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

  // CORTE PRUEBA→REAL: finaliza el modo prueba (acción, sin body). Conserva el
  // histórico de prueba, reinicia el stock vendible a 0 (con kardex) y arrastra la
  // merma. Al terminar baja el banner del POS (invalida 'active') y abre el modal.
  const cutover = useMutation({
    mutationFn: async () =>
      (await api.post<CutoverSummary>('/settings/test-mode/cutover')).data,
    onSuccess: (data) => {
      queryClient.setQueryData(['settings', 'test-mode'], {
        active: data.active,
        businessId: data.businessId,
        recentChanges: data.recentChanges,
      });
      queryClient.invalidateQueries({ queryKey: ['settings', 'test-mode'] });
      queryClient.invalidateQueries({ queryKey: ['settings', 'test-mode', 'active'] });
      setSummary(data);
      setConfirmOff(false);
    },
    onError: () => toast.error('No se pudo finalizar el modo prueba', 'Inténtalo de nuevo.'),
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
              <strong>¿Finalizar el modo prueba e iniciar operación real?</strong>
              <p>Esto hará lo siguiente, y <b>no se puede deshacer</b>:</p>
              <ul className="tm-confirm-list">
                <li>Las ventas, cortes y movimientos de práctica <b>se conservan</b> como histórico consultable (no se borran).</li>
                <li>El <b>inventario vendible se reinicia a 0</b> en <b>todas tus sucursales</b>. Tendrás que registrar tu producción real desde Inventario → Entradas para volver a tener existencia.</li>
                <li>La <b>merma actual se conserva</b> y seguirá disponible desde Salidas.</li>
                <li>Queda registrado en la bitácora (quién, cuándo y desde qué dispositivo).</li>
              </ul>
              <div className="tm-confirm-actions">
                <button className="btn-ghost" onClick={() => setConfirmOff(false)} disabled={cutover.isPending}>
                  Cancelar
                </button>
                <motion.button className="btn-accent tm-danger" disabled={cutover.isPending}
                  onClick={() => cutover.mutate()}
                  whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                  <Square size={16} /> {cutover.isPending ? 'Finalizando…' : 'Sí, finalizar modo prueba'}
                </motion.button>
              </div>
            </div>
          ) : (
            <motion.button className="btn-accent tm-danger" disabled={cutover.isPending}
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
              const isCutover = e.action === 'TEST_MODE_CUTOVER';
              const mobile = e.device === 'mobile';
              const label = enabled
                ? 'Inició prueba'
                : isCutover
                  ? 'Finalizó modo prueba (corte)'
                  : 'Finalizó prueba';
              return (
                <div className="tm-log-row" key={i}>
                  <span className={`tm-log-action ${enabled ? 'is-on' : 'is-off'}`}>
                    {enabled ? <Play size={13} /> : <Square size={13} />}
                    {label}
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

      {summary && (
        <div className="tm-modal-overlay" role="dialog" aria-modal="true" onClick={() => setSummary(null)}>
          <motion.div className="tm-modal" onClick={(ev) => ev.stopPropagation()}
            variants={fadeInUp} initial="hidden" animate="visible">
            <button className="tm-modal-x" aria-label="Cerrar" onClick={() => setSummary(null)}>
              <X size={18} />
            </button>

            {summary.executed ? (
              <>
                <span className="tm-state-chip is-done">
                  <CheckCircle2 size={15} /> Operación real iniciada
                </span>
                <p className="tm-modal-sub">El modo prueba quedó finalizado. Desde ahora las ventas y cortes son reales.</p>

                <div className="tm-summary-cards">
                  <div className="tm-summary-card">
                    <div className="tm-summary-head">
                      <span className="tm-summary-icon violet"><Archive size={16} /></span>
                      <strong>Histórico de prueba conservado</strong>
                    </div>
                    <div className="tm-summary-chips">
                      <span className="tm-chip violet num">{fmtNum(summary.practice.salesKept)} ventas</span>
                      <span className="tm-chip violet num">{fmtNum(summary.practice.cashCutsKept)} cortes</span>
                    </div>
                    <p className="tm-summary-note">Nada se borró: tu práctica queda consultable como histórico.</p>
                  </div>

                  <div className="tm-summary-card">
                    <div className="tm-summary-head">
                      <span className="tm-summary-icon amber"><PackageX size={16} /></span>
                      <strong>Inventario reiniciado</strong>
                    </div>
                    <div className="tm-summary-chips">
                      <span className="tm-chip amber num">{fmtNum(summary.stock.rowsAdjusted)} artículos a 0</span>
                      <span className="tm-chip amber num">{fmtNum(summary.stock.totalUnitsRemoved)} piezas retiradas</span>
                    </div>
                    {summary.stock.lines.length > 0 && (
                      <div className="tm-lines">
                        <div className="tm-lines-head">
                          <span>Artículo</span>
                          <span>Sucursal</span>
                          <span className="tm-lines-num">Cantidad previa</span>
                        </div>
                        {summary.stock.lines.slice(0, 8).map((l) => (
                          <div className="tm-lines-row" key={`${l.productId}-${l.branchId}`}>
                            <span className="num">#{l.productId}</span>
                            <span className="num">#{l.branchId}</span>
                            <span className="tm-lines-num num">{fmtNum(l.previousQuantity)}</span>
                          </div>
                        ))}
                        {summary.stock.lines.length > 8 && (
                          <p className="tm-summary-note">y {fmtNum(summary.stock.lines.length - 8)} más…</p>
                        )}
                      </div>
                    )}
                    <p className="tm-summary-note">Registra tu producción real desde Inventario → Entradas.</p>
                  </div>

                  <div className="tm-summary-card">
                    <div className="tm-summary-head">
                      <span className="tm-summary-icon green"><Recycle size={16} /></span>
                      <strong>Merma conservada</strong>
                    </div>
                    <div className="tm-summary-chips">
                      <span className="tm-chip green num">{fmtNum(summary.waste.totalPieces)} piezas</span>
                      <span className="tm-chip green num">{fmtNum(summary.waste.productCount)} artículos</span>
                      <span className="tm-chip amber num">${fmtNum(summary.waste.wasteValue)}</span>
                    </div>
                    <p className="tm-summary-note">
                      Inicias fase real con {fmtNum(summary.waste.totalPieces)} piezas / $
                      {fmtNum(summary.waste.wasteValue)} en merma. Tu merma se mantiene; regístrala o
                      repártela desde Salidas.
                    </p>
                  </div>
                </div>
              </>
            ) : (
              <>
                <span className="tm-state-chip is-done">
                  <CheckCircle2 size={15} /> Ya estaba en operación real
                </span>
                <div className="tm-modal-empty">
                  <InfoBanner icon={<AlertTriangle size={18} />} tone="neutral" title="No había corte pendiente">
                    El modo prueba ya estaba finalizado, así que no se modificó el inventario ni la bitácora.
                  </InfoBanner>
                </div>
              </>
            )}

            <div className="tm-modal-actions">
              <motion.button className="btn-accent" onClick={() => setSummary(null)}
                whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                Entendido
              </motion.button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}
