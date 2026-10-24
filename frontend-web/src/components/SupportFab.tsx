import { useState } from 'react';
import { motion } from 'motion/react';
import { LifeBuoy } from 'lucide-react';
import { SupportTicketModal } from '@/pages/SupportTicketModal';
import { pressable } from '@/lib/motion';
import './support-fab.css';

/**
 * Botón flotante de soporte, global para los usuarios del negocio (Dueño/Cajera). Se renderiza
 * dentro de AppLayout, fuera del <Outlet/>, de modo que acompaña a toda la operación (POS, caja,
 * inventario, etc.). Al pulsarlo abre el modal premium para reportar un error o pedir una mejora.
 *
 * La visibilidad por rol la decide AppLayout (solo se monta cuando !superAdmin); aquí solo se
 * gestiona el estado de apertura del modal.
 */
export function SupportFab() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <motion.button
        type="button"
        className="support-fab"
        onClick={() => setOpen(true)}
        aria-label="Reportar un error o pedir una mejora"
        title="Soporte: reporta un error o pide una mejora"
        whileHover={pressable.whileHover}
        whileTap={pressable.whileTap}
        transition={pressable.transition}
      >
        <LifeBuoy size={22} />
      </motion.button>
      {open && <SupportTicketModal onClose={() => setOpen(false)} />}
    </>
  );
}
