import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import type { Variants } from 'motion/react';
import {
  ShoppingCart,
  FileText,
  Boxes,
  Truck,
  Smartphone,
  BarChart3,
  ShieldCheck,
  Cloud,
  Gift,
  Wallet,
  Store,
  CreditCard,
  MessageCircle,
  CheckCircle2,
  Mail,
  LogIn,
  Menu,
  X,
  Zap,
  Building2,
  Headphones,
  MapPin,
  Receipt,
} from 'lucide-react';
import { ThemeToggle } from '@/components/ThemeToggle';
import './landing.css';

/* =====================================================================
   PuntoNube — Landing pública (SaaS POS para México).
   Página de inicio para visitantes sin sesión. El objetivo principal es
   convertir con la prueba gratis de 15 días vía WhatsApp y dejar claro el
   alcance del producto (POS, CFDI 4.0, inventario, multi-negocio, nube).
   ===================================================================== */

/* ---- Datos de contacto centralizados ---- */
const WHATSAPP_NUMBER = '525611730566'; // 52 = México + 5611730566
const CONTACT_EMAIL = 'shuitrongomez05@gmail.com';

/** Construye un enlace de WhatsApp con un mensaje prellenado (texto codificado). */
function waLink(message: string): string {
  return `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(message)}`;
}

const WA_TRIAL = waLink(
  'Hola, me interesa probar PuntoNube (15 días gratis). ¿Me pueden dar información?',
);
const WA_QUOTE = waLink(
  'Hola, me interesa PuntoNube. ¿Me pueden enviar una cotización de los planes?',
);

/* ---- Animaciones reutilizables (entrada al hacer scroll) ---- */
const fadeUp: Variants = {
  hidden: { opacity: 0, y: 32 },
  show: { opacity: 1, y: 0, transition: { duration: 0.6, ease: 'easeOut' } },
};
const stagger: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.1 } },
};
/** Props compartidas para animar una sección al entrar en el viewport una sola vez. */
const inView = {
  initial: 'hidden' as const,
  whileInView: 'show' as const,
  viewport: { once: true, amount: 0.2 },
};

/* ---- Contenido de secciones ---- */
const MODULES = [
  {
    icon: ShoppingCart,
    title: 'Punto de venta',
    text: 'POS rápido con atajos de teclado, cobro con billetes y tickets térmicos. Vende sin fricción en mostrador.',
  },
  {
    icon: FileText,
    title: 'Facturación CFDI 4.0',
    text: 'Timbrado ante el SAT, Carta Porte, complementos de pago y remisiones. Siempre al día con la normativa.',
  },
  {
    icon: Boxes,
    title: 'Inventario',
    text: 'Existencias, kardex, lotes, series, traspasos y etiquetas. Control total de tu almacén y sucursales.',
  },
  {
    icon: Truck,
    title: 'Compras',
    text: 'Órdenes de compra, proveedores, cuentas por pagar y carga de XML. Ordena tu abasto de principio a fin.',
  },
  {
    icon: Smartphone,
    title: 'Recargas y servicios',
    text: 'Tiempo aire y pago de servicios desde el mismo punto de venta. Suma ingresos extra a tu negocio.',
  },
  {
    icon: BarChart3,
    title: 'Reportes y BI',
    text: 'Dashboards, ventas por cajero y sugerencias de compra. Decide con datos, no con corazonadas.',
  },
  {
    icon: ShieldCheck,
    title: 'Roles y caja segura',
    text: 'Permisos configurables por rol y corte de caja con seguridad "cero pérdidas". Tu dinero, bajo control.',
  },
];

const UPCOMING = [
  { icon: Smartphone, title: 'App móvil', text: 'Tu negocio en el bolsillo: vende y consulta desde el celular.' },
  { icon: Gift, title: 'Programa de lealtad', text: 'Puntos y recompensas para que tus clientes regresen.' },
  { icon: Wallet, title: 'Nómina', text: 'Gestión de empleados y pagos integrada a tu operación.' },
  { icon: Store, title: 'E-commerce', text: 'Tu tienda en línea conectada al mismo inventario.' },
  { icon: CreditCard, title: 'Terminal de pago', text: 'Cobra con tarjeta directamente desde PuntoNube.' },
  { icon: MessageCircle, title: 'Integración WhatsApp', text: 'Notifica, factura y atiende a tus clientes por WhatsApp.' },
];

const BENEFITS = [
  { icon: Cloud, title: '100% en la nube', text: 'Sin instalar nada ni servidores propios. Entra desde cualquier navegador.' },
  { icon: Receipt, title: 'CFDI 4.0 al día', text: 'Facturación siempre alineada a los cambios del SAT, sin que tú te preocupes.' },
  { icon: Building2, title: 'Multi-negocio y sucursal', text: 'Administra varios negocios y puntos de venta desde una sola cuenta.' },
  { icon: ShieldCheck, title: 'Seguridad y respaldos', text: 'Tus datos cifrados y respaldados automáticamente en la nube.' },
  { icon: Headphones, title: 'Soporte en español', text: 'Atención cercana y en tu idioma cuando lo necesites.' },
  { icon: MapPin, title: 'Pensado para México', text: 'Hecho para el SAT, los impuestos y la forma de vender del país.' },
];

const PLANS = [
  {
    name: 'Esencial',
    desc: 'Para empezar a vender y facturar sin complicarte.',
    featured: false,
    features: [
      'Punto de venta y tickets',
      'Facturación CFDI 4.0',
      'Inventario básico',
      '1 sucursal',
      'Soporte por WhatsApp',
    ],
  },
  {
    name: 'Profesional',
    desc: 'Para negocios que crecen y necesitan más control.',
    featured: true,
    features: [
      'Todo lo de Esencial',
      'Compras y proveedores',
      'Kardex, lotes y series',
      'Reportes y BI',
      'Roles y caja segura',
      'Multi-sucursal',
    ],
  },
  {
    name: 'Empresarial',
    desc: 'Para operaciones con varios negocios y alto volumen.',
    featured: false,
    features: [
      'Todo lo de Profesional',
      'Multi-negocio',
      'Recargas y servicios',
      'Carta Porte y complementos',
      'Soporte prioritario',
    ],
  },
];

const STATS = [
  { num: '15 días', label: 'de prueba gratis' },
  { num: 'CFDI 4.0', label: 'timbrado ante el SAT' },
  { num: '100%', label: 'en la nube' },
  { num: 'Multi', label: 'negocio y sucursal' },
];

export function LandingPage() {
  const [menuOpen, setMenuOpen] = useState(false);

  // Estado del formulario de contacto.
  const [form, setForm] = useState({ nombre: '', negocio: '', telefono: '', mensaje: '' });
  const [formError, setFormError] = useState<string | null>(null);

  const updateField = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  /**
   * Envío del formulario de contacto.
   *
   * De momento NO hay backend de correo: construimos el mensaje con los datos y abrimos WhatsApp
   * hacia el número de PuntoNube con el texto prellenado.
   *
   * TODO(correo): cuando exista el backend, sustituir el window.open por un POST al endpoint de
   * contacto (p. ej. `await api.post('/contact', form)`) o integrar un servicio tipo Resend para
   * enviar el correo automáticamente a shuitrongomez05@gmail.com. Mantener este handler como único
   * punto de envío para que el cambio sea localizado.
   */
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    // Validación mínima: Nombre y Teléfono/WhatsApp son obligatorios.
    if (!form.nombre.trim() || !form.telefono.trim()) {
      setFormError('Por favor completa al menos tu nombre y tu teléfono/WhatsApp.');
      return;
    }

    const message =
      `Hola, me interesa PuntoNube (prueba de 15 días gratis).\n` +
      `Nombre: ${form.nombre}\n` +
      `Negocio: ${form.negocio || '—'}\n` +
      `Teléfono/WhatsApp: ${form.telefono}\n` +
      `Mensaje: ${form.mensaje || '—'}`;

    window.open(waLink(message), '_blank', 'noopener,noreferrer');
  };

  const closeMenu = () => setMenuOpen(false);

  return (
    <div className="landing">
      {/* Decorados de fondo (no interactivos) */}
      <div className="lp-blobs" aria-hidden="true">
        <span className="lp-blob lp-blob-1" />
        <span className="lp-blob lp-blob-2" />
        <span className="lp-blob lp-blob-3" />
      </div>
      <div className="lp-grid" aria-hidden="true" />

      {/* ======================= NAV ======================= */}
      <nav className="lp-nav">
        <a className="lp-brand" href="#top" onClick={closeMenu}>
          <span className="lp-mark">PN</span>
          <span>
            <span className="lp-brand-name">PuntoNube</span>
            <span className="lp-brand-tag" style={{ display: 'block' }}>
              CLOUD POS · MÉXICO
            </span>
          </span>
        </a>

        <div className="lp-nav-links">
          <a className="lp-nav-link" href="#modulos">Módulos</a>
          <a className="lp-nav-link" href="#proximamente">Próximamente</a>
          <a className="lp-nav-link" href="#planes">Planes</a>
          <a className="lp-nav-link" href="#contacto">Contacto</a>
        </div>

        <div className="lp-nav-actions">
          <ThemeToggle />
          <Link className="lp-btn lp-btn-primary" to="/login">
            <LogIn size={17} aria-hidden="true" />
            Iniciar sesión
          </Link>
          <button
            type="button"
            className="lp-nav-toggle"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X size={20} aria-hidden="true" /> : <Menu size={20} aria-hidden="true" />}
          </button>
        </div>
      </nav>

      {/* Menú móvil */}
      {menuOpen && (
        <div className="lp-mobile-menu">
          <a className="lp-nav-link" href="#modulos" onClick={closeMenu}>Módulos</a>
          <a className="lp-nav-link" href="#proximamente" onClick={closeMenu}>Próximamente</a>
          <a className="lp-nav-link" href="#planes" onClick={closeMenu}>Planes</a>
          <a className="lp-nav-link" href="#contacto" onClick={closeMenu}>Contacto</a>
          <Link className="lp-btn lp-btn-primary" to="/login" onClick={closeMenu}>
            <LogIn size={17} aria-hidden="true" />
            Iniciar sesión
          </Link>
        </div>
      )}

      <div className="lp-shell" id="top">
        {/* ======================= HERO ======================= */}
        <header className="lp-hero">
          <motion.div
            className="lp-hero-badge"
            initial={{ opacity: 0, y: -14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: 'easeOut' }}
          >
            <span className="lp-pulse" aria-hidden="true" />
            15 días de prueba gratis
          </motion.div>

          <motion.h1
            className="lp-hero-title"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.08, ease: 'easeOut' }}
          >
            El punto de venta en la nube <br />
            <span className="lp-grad">para México</span>
          </motion.h1>

          <motion.p
            className="lp-hero-sub"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.18, ease: 'easeOut' }}
          >
            Sistema POS completo con facturación CFDI 4.0, inventario y multi-negocio.
            100% en la nube: vende, factura y controla tu operación desde cualquier lugar.
          </motion.p>

          <motion.div
            className="lp-hero-cta"
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.28, ease: 'easeOut' }}
          >
            <a className="lp-btn lp-btn-whats lp-btn-lg" href={WA_TRIAL} target="_blank" rel="noopener noreferrer">
              <MessageCircle size={20} aria-hidden="true" />
              Comenzar prueba gratis
            </a>
            <Link className="lp-btn lp-btn-ghost lp-btn-lg" to="/login">
              <LogIn size={19} aria-hidden="true" />
              Iniciar sesión
            </Link>
          </motion.div>

          <motion.p
            className="lp-hero-note"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.7, delay: 0.4 }}
          >
            La prueba dura 15 días y puede extenderse según acuerdo con el dueño. Sin tarjeta para empezar.
          </motion.p>

          <motion.div className="lp-hero-stats" {...inView} variants={stagger}>
            {STATS.map((s) => (
              <motion.div key={s.label} variants={fadeUp}>
                <div className="lp-stat-num">{s.num}</div>
                <div className="lp-stat-label">{s.label}</div>
              </motion.div>
            ))}
          </motion.div>
        </header>

        {/* ======================= MÓDULOS ======================= */}
        <motion.section className="lp-section" id="modulos" {...inView} variants={stagger}>
          <motion.div className="lp-section-head" variants={fadeUp}>
            <span className="lp-eyebrow">Módulos actuales</span>
            <h2 className="lp-section-title">Todo tu negocio en un solo lugar</h2>
            <p className="lp-section-sub">
              Siete módulos que cubren la venta, la facturación y el control de tu operación diaria.
            </p>
          </motion.div>

          <motion.div className="lp-grid-cards" variants={stagger}>
            {MODULES.map(({ icon: Icon, title, text }) => (
              <motion.article key={title} className="lp-card" variants={fadeUp}>
                <div className="lp-card-icon" aria-hidden="true">
                  <Icon size={26} strokeWidth={1.9} />
                </div>
                <h3 className="lp-card-title">{title}</h3>
                <p className="lp-card-text">{text}</p>
              </motion.article>
            ))}
          </motion.div>
        </motion.section>

        {/* ======================= PRÓXIMAMENTE ======================= */}
        <motion.section className="lp-section" id="proximamente" {...inView} variants={stagger}>
          <motion.div className="lp-section-head" variants={fadeUp}>
            <span className="lp-eyebrow">Roadmap</span>
            <h2 className="lp-section-title">Lo que viene para PuntoNube</h2>
            <p className="lp-section-sub">
              Estamos construyendo más herramientas para que tu negocio crezca sin cambiar de sistema.
            </p>
          </motion.div>

          <motion.div className="lp-grid-cards" variants={stagger}>
            {UPCOMING.map(({ icon: Icon, title, text }) => (
              <motion.article key={title} className="lp-card is-soon" variants={fadeUp}>
                <span className="lp-card-badge">Próximamente</span>
                <div className="lp-card-icon" aria-hidden="true">
                  <Icon size={26} strokeWidth={1.9} />
                </div>
                <h3 className="lp-card-title">{title}</h3>
                <p className="lp-card-text">{text}</p>
              </motion.article>
            ))}
          </motion.div>
        </motion.section>

        {/* ======================= BENEFICIOS ======================= */}
        <motion.section className="lp-section" id="beneficios" {...inView} variants={stagger}>
          <motion.div className="lp-section-head" variants={fadeUp}>
            <span className="lp-eyebrow">Por qué PuntoNube</span>
            <h2 className="lp-section-title">Pensado para vender tranquilo</h2>
            <p className="lp-section-sub">
              Tecnología seria con la cercanía de un equipo que entiende cómo se vende en México.
            </p>
          </motion.div>

          <motion.div className="lp-benefits" variants={stagger}>
            {BENEFITS.map(({ icon: Icon, title, text }) => (
              <motion.div key={title} className="lp-benefit" variants={fadeUp}>
                <div className="lp-benefit-icon" aria-hidden="true">
                  <Icon size={22} strokeWidth={1.9} />
                </div>
                <div>
                  <h3 className="lp-benefit-title">{title}</h3>
                  <p className="lp-benefit-text">{text}</p>
                </div>
              </motion.div>
            ))}
          </motion.div>
        </motion.section>

        {/* ======================= PLANES ======================= */}
        <motion.section className="lp-section" id="planes" {...inView} variants={stagger}>
          <motion.div className="lp-section-head" variants={fadeUp}>
            <span className="lp-eyebrow">Planes</span>
            <h2 className="lp-section-title">Un plan a tu medida</h2>
            <p className="lp-section-sub">
              Elige el plan que acompaña tu etapa. Empieza con 15 días de prueba gratis y pide tu cotización sin compromiso.
            </p>
          </motion.div>

          <motion.div className="lp-plans" variants={stagger}>
            {PLANS.map((plan) => (
              <motion.div
                key={plan.name}
                className={`lp-plan ${plan.featured ? 'is-featured' : ''}`}
                variants={fadeUp}
              >
                {plan.featured && <span className="lp-plan-tag">Más popular</span>}
                <h3 className="lp-plan-name">{plan.name}</h3>
                <p className="lp-plan-desc">{plan.desc}</p>
                <div className="lp-plan-price">
                  A tu medida
                  <small>Consultar · 15 días de prueba gratis</small>
                </div>
                <ul className="lp-plan-features">
                  {plan.features.map((f) => (
                    <li key={f}>
                      <CheckCircle2 size={18} aria-hidden="true" />
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>
                <a
                  className={`lp-btn ${plan.featured ? 'lp-btn-primary' : 'lp-btn-ghost'}`}
                  href={WA_QUOTE}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Solicitar cotización
                </a>
              </motion.div>
            ))}
          </motion.div>
        </motion.section>

        {/* ======================= CONTACTO ======================= */}
        <motion.section className="lp-section" id="contacto" {...inView} variants={stagger}>
          <motion.div className="lp-section-head" variants={fadeUp}>
            <span className="lp-eyebrow">Contacto</span>
            <h2 className="lp-section-title">Hablemos de tu negocio</h2>
            <p className="lp-section-sub">
              Escríbenos por WhatsApp o déjanos tus datos y nosotros te contactamos para tu prueba gratis.
            </p>
          </motion.div>

          <div className="lp-contact-grid">
            {/* Vías directas */}
            <motion.div className="lp-contact-card" variants={fadeUp}>
              <h3>Contáctanos directo</h3>
              <p>Resolvemos tus dudas y activamos tu prueba de 15 días en minutos.</p>

              <a className="lp-btn lp-btn-whats lp-btn-lg" href={WA_TRIAL} target="_blank" rel="noopener noreferrer">
                <MessageCircle size={20} aria-hidden="true" />
                Escríbenos por WhatsApp
              </a>

              <div className="lp-contact-direct">
                <a className="lp-contact-line" href={WA_TRIAL} target="_blank" rel="noopener noreferrer">
                  <span className="lp-contact-ico" aria-hidden="true"><MessageCircle size={20} /></span>
                  <span>WhatsApp: 56 1173 0566</span>
                </a>
                <a className="lp-contact-line" href={`mailto:${CONTACT_EMAIL}`}>
                  <span className="lp-contact-ico" aria-hidden="true"><Mail size={20} /></span>
                  <span>{CONTACT_EMAIL}</span>
                </a>
              </div>
            </motion.div>

            {/* Formulario */}
            <motion.div className="lp-contact-card" variants={fadeUp}>
              <h3>Déjanos tus datos y te contactamos</h3>
              <p>Cuéntanos de tu negocio y te escribimos con la información de tu prueba gratis.</p>

              <form className="lp-form" onSubmit={handleSubmit} noValidate>
                <div className="lp-field">
                  <label htmlFor="lp-nombre">Nombre *</label>
                  <input
                    id="lp-nombre"
                    type="text"
                    value={form.nombre}
                    onChange={updateField('nombre')}
                    placeholder="Tu nombre"
                    autoComplete="name"
                    required
                  />
                </div>
                <div className="lp-field">
                  <label htmlFor="lp-negocio">Negocio</label>
                  <input
                    id="lp-negocio"
                    type="text"
                    value={form.negocio}
                    onChange={updateField('negocio')}
                    placeholder="Nombre de tu negocio"
                    autoComplete="organization"
                  />
                </div>
                <div className="lp-field">
                  <label htmlFor="lp-telefono">Teléfono / WhatsApp *</label>
                  <input
                    id="lp-telefono"
                    type="tel"
                    value={form.telefono}
                    onChange={updateField('telefono')}
                    placeholder="10 dígitos"
                    autoComplete="tel"
                    inputMode="tel"
                    required
                  />
                </div>
                <div className="lp-field">
                  <label htmlFor="lp-mensaje">Mensaje (opcional)</label>
                  <textarea
                    id="lp-mensaje"
                    value={form.mensaje}
                    onChange={updateField('mensaje')}
                    placeholder="¿Qué te gustaría saber?"
                  />
                </div>

                {formError && (
                  <div className="lp-form-error" role="alert">
                    {formError}
                  </div>
                )}

                <button type="submit" className="lp-btn lp-btn-primary lp-btn-lg">
                  <MessageCircle size={19} aria-hidden="true" />
                  Enviar por WhatsApp
                </button>
              </form>
            </motion.div>
          </div>
        </motion.section>
      </div>

      {/* ======================= FOOTER ======================= */}
      <footer className="lp-footer">
        <div className="lp-footer-inner">
          <div className="lp-footer-brand">
            <div className="lp-brand">
              <span className="lp-mark">PN</span>
              <span>
                <span className="lp-brand-name">PuntoNube</span>
                <span className="lp-brand-tag" style={{ display: 'block' }}>
                  CLOUD POS · MÉXICO
                </span>
              </span>
            </div>
            <p>Punto de venta en la nube para México. Vende, factura y controla tu negocio desde cualquier lugar.</p>
          </div>

          <div className="lp-footer-col">
            <h4>Producto</h4>
            <a href="#modulos">Módulos</a>
            <a href="#proximamente">Próximamente</a>
            <a href="#planes">Planes</a>
            <a href="#beneficios">Beneficios</a>
          </div>

          <div className="lp-footer-col">
            <h4>Contacto</h4>
            <a href={WA_TRIAL} target="_blank" rel="noopener noreferrer">WhatsApp: 56 1173 0566</a>
            <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
          </div>

          <div className="lp-footer-col">
            <h4>Acceso</h4>
            <Link className="lp-btn lp-btn-ghost" to="/login">
              <LogIn size={17} aria-hidden="true" />
              Iniciar sesión
            </Link>
          </div>
        </div>

        <div className="lp-footer-bottom">
          <span className="lp-footer-copy">
            PuntoNube © 2026 · Punto de venta en la nube para México
          </span>
          <span className="lp-footer-copy" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Zap size={14} aria-hidden="true" />
            Hecho para México
          </span>
        </div>
      </footer>
    </div>
  );
}
