import { useRef, useState } from 'react';
import { AxiosError } from 'axios';
import { useLocation } from 'react-router-dom';
import { motion } from 'motion/react';
import { Bug, Lightbulb, LifeBuoy, ImagePlus, X, Loader2 } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { InfoBanner } from '@/components/InfoBanner';
import { popIn, quick, pressable } from '@/lib/motion';
import './support.css';

type TicketType = 'ERROR' | 'MEJORA';

/** Tipos de imagen admitidos (coinciden con los magic-bytes validados en el backend). */
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5 MB, alineado con SUPPORT_IMAGE_MAX_BYTES por defecto
const MAX_IMAGES = 5; // alineado con SUPPORT_IMAGE_MAX_COUNT por defecto
const DESC_MAX = 4000;
const TITLE_MAX = 160;

interface Preview {
  file: File;
  url: string;
}

/**
 * Modal premium para crear un ticket de soporte/feedback. Sigue las directrices de diseño:
 * InfoBanner de contexto, tipo ERROR/MEJORA como chips (no <select>), inputs con placeholder tenue,
 * contador de caracteres con aviso al acercarse al límite, zona de adjuntar imágenes con previews.
 *
 * El envío va por multipart/form-data construido con FormData SIN fijar Content-Type manual: axios
 * detecta el FormData y arma el boundary (si se fijara 'multipart/form-data' a mano, el boundary se
 * perdería). Se anula el default JSON de la instancia con Content-Type undefined para esta petición.
 */
export function SupportTicketModal({ onClose }: { onClose: () => void }) {
  const location = useLocation();
  const [type, setType] = useState<TicketType>('ERROR');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [previews, setPreviews] = useState<Preview[]>([]);
  const [dragging, setDragging] = useState(false);
  const [sending, setSending] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const descNearLimit = description.length > DESC_MAX * 0.9;
  const canSubmit = title.trim().length >= 3 && description.trim().length >= 5 && !sending;

  const revokeAll = () => previews.forEach((p) => URL.revokeObjectURL(p.url));

  const close = () => {
    revokeAll();
    onClose();
  };

  // Validación de cliente de las imágenes: tipo, tamaño y cantidad. Es solo UX; la barrera dura
  // vive en el backend (magic-bytes/tamaño/cantidad). Avisa con toast y descarta las no válidas.
  const addFiles = (incoming: FileList | File[]) => {
    const files = Array.from(incoming);
    const accepted: Preview[] = [];
    for (const file of files) {
      if (previews.length + accepted.length >= MAX_IMAGES) {
        toast.info('Máximo 5 imágenes', 'No se agregaron las imágenes extra.');
        break;
      }
      if (!ACCEPTED_TYPES.includes(file.type)) {
        toast.error('Formato no admitido', `“${file.name}” debe ser JPG, PNG o WEBP.`);
        continue;
      }
      if (file.size > MAX_IMAGE_BYTES) {
        toast.error('Imagen muy pesada', `“${file.name}” supera los 5 MB.`);
        continue;
      }
      accepted.push({ file, url: URL.createObjectURL(file) });
    }
    if (accepted.length) setPreviews((cur) => [...cur, ...accepted]);
  };

  const removeImage = (idx: number) => {
    setPreviews((cur) => {
      const next = [...cur];
      const [removed] = next.splice(idx, 1);
      if (removed) URL.revokeObjectURL(removed.url);
      return next;
    });
  };

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    if (e.dataTransfer.files?.length) addFiles(e.dataTransfer.files);
  };

  const submit = async () => {
    if (!canSubmit) return;
    setSending(true);
    try {
      // Contexto automático que aporta el cliente: pantalla actual. El resto (tenant, usuario, rol,
      // User-Agent, dispositivo, fecha) lo deriva el backend. appVersion se omite (VITE_APP_VERSION
      // no existe y no se toca vite.config.ts).
      const payload = {
        type,
        title: title.trim(),
        description: description.trim(),
        screenPath: location.pathname,
      };
      const fd = new FormData();
      fd.append('data', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
      previews.forEach((p) => fd.append('images', p.file));
      // Sin header manual: se anula el default JSON para que axios arme el boundary del multipart.
      await api.post('/support/tickets', fd, { headers: { 'Content-Type': undefined } });
      toast.success('Reporte enviado', 'Gracias, lo revisaremos muy pronto.');
      close();
    } catch (err) {
      const ax = err as AxiosError<{ title?: string; detail?: string }>;
      const problemTitle = ax.response?.data?.title;
      if (problemTitle === 'IMAGE_TOO_LARGE') {
        toast.error('Imagen muy pesada', 'Alguna imagen supera el tamaño permitido (5 MB).');
      } else if (problemTitle === 'IMAGE_INVALID') {
        toast.error('Imagen no válida', 'Revisa el formato (JPG/PNG/WEBP) y el tamaño de las imágenes.');
      } else {
        toast.error('No se pudo enviar el reporte', ax.response?.data?.detail ?? 'Inténtalo de nuevo.');
      }
      setSending(false);
    }
  };

  return (
    <motion.div
      className="modal-overlay"
      onClick={close}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={quick}
    >
      <motion.div
        className="modal-card modal-wide sup-modal"
        onClick={(e) => e.stopPropagation()}
        variants={popIn}
        initial="hidden"
        animate="visible"
        exit="exit"
      >
        <div className="modal-head">
          <h3>Soporte y mejoras</h3>
          <button className="modal-close" onClick={close} aria-label="Cerrar">×</button>
        </div>

        <div className="modal-body">
          <InfoBanner icon={<LifeBuoy size={20} />} title="Reporta un error o pide una mejora" tone="brand">
            Adjunta capturas si ayudan. Registramos solo el contexto técnico (pantalla, dispositivo,
            versión) para atenderte de inmediato.
          </InfoBanner>

          {/* Tipo: chips de color, no un select crudo. */}
          <div className="sup-type-group" role="radiogroup" aria-label="Tipo de reporte">
            <button
              type="button"
              role="radio"
              aria-checked={type === 'ERROR'}
              className={`sup-type-chip is-error ${type === 'ERROR' ? 'is-active' : ''}`}
              onClick={() => setType('ERROR')}
            >
              <Bug size={16} /> Reportar un error
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={type === 'MEJORA'}
              className={`sup-type-chip is-mejora ${type === 'MEJORA' ? 'is-active' : ''}`}
              onClick={() => setType('MEJORA')}
            >
              <Lightbulb size={16} /> Pedir una mejora
            </button>
          </div>

          <label className="field sup-field">
            <span>Título</span>
            <input
              value={title}
              maxLength={TITLE_MAX}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Describe el problema en una línea"
            />
          </label>

          <label className="field sup-field">
            <span className="sup-label-row">
              Descripción
              <small className={`sup-counter ${descNearLimit ? 'is-near' : ''}`}>
                {description.length} / {DESC_MAX}
              </small>
            </span>
            <textarea
              value={description}
              maxLength={DESC_MAX}
              rows={5}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="¿Qué pasó? ¿Qué esperabas que pasara?"
            />
          </label>

          {/* Zona de adjuntar imágenes: drag&drop + botón, previews con esquinas suaves. */}
          <div
            className={`sup-dropzone ${dragging ? 'is-dragging' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
          >
            <ImagePlus size={22} className="sup-dropzone-icon" />
            <p className="sup-dropzone-text">
              Arrastra imágenes aquí o
              <button type="button" className="sup-dropzone-btn" onClick={() => fileInput.current?.click()}>
                agrégalas
              </button>
            </p>
            <small className="sup-dropzone-hint">JPG, PNG o WEBP · máx. 5 MB · hasta 5 imágenes</small>
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              hidden
              onChange={(e) => {
                if (e.target.files?.length) addFiles(e.target.files);
                e.target.value = '';
              }}
            />
          </div>

          {previews.length > 0 && (
            <div className="sup-previews">
              {previews.map((p, i) => (
                <div key={p.url} className="sup-preview">
                  <img src={p.url} alt={p.file.name} />
                  <button
                    type="button"
                    className="sup-preview-remove"
                    aria-label={`Quitar ${p.file.name}`}
                    onClick={() => removeImage(i)}
                  >
                    <X size={13} />
                  </button>
                </div>
              ))}
            </div>
          )}

          <div className="admin-modal-actions">
            <motion.button
              className="btn-ghost"
              onClick={close}
              whileHover={pressable.whileHover}
              whileTap={pressable.whileTap}
              transition={pressable.transition}
            >
              Cancelar
            </motion.button>
            <motion.button
              className="btn-accent"
              disabled={!canSubmit}
              onClick={submit}
              whileHover={canSubmit ? pressable.whileHover : undefined}
              whileTap={canSubmit ? pressable.whileTap : undefined}
              transition={pressable.transition}
            >
              {sending ? <><Loader2 size={15} className="sup-spin" /> Enviando…</> : 'Enviar reporte'}
            </motion.button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
