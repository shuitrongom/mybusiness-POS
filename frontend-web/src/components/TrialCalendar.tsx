import { useMemo } from 'react';
import { Calendar, dateFnsLocalizer, type Event } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay } from 'date-fns';
import { es } from 'date-fns/locale';
import 'react-big-calendar/lib/css/react-big-calendar.css';
import './trial-calendar.css';

/** Localizador en español (semana inicia en lunes) para react-big-calendar. */
const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek: () => startOfWeek(new Date(), { weekStartsOn: 1 }),
  getDay,
  locales: { es },
});

/** Etiquetas de la barra de herramientas y celdas, en español. */
const messages = {
  today: 'Hoy',
  previous: 'Anterior',
  next: 'Siguiente',
  month: 'Mes',
  week: 'Semana',
  day: 'Día',
  agenda: 'Agenda',
  date: 'Fecha',
  time: 'Hora',
  event: 'Vencimiento',
  noEventsInRange: 'Sin vencimientos en este rango.',
  showMore: (count: number) => `+${count} más`,
};

export interface TrialBusiness {
  id: number;
  name: string;
  status: string;
  trialEndsAt: string | null;
}

/**
 * Calendario mensual premium (react-big-calendar) que muestra, como eventos, los negocios cuya
 * prueba vence en cada día. Sustituye al calendario casero anterior: tamaño correcto, navegación
 * entre meses/semana/día/agenda y estilo alineado al sistema de diseño (claro y oscuro).
 */
export function TrialCalendar({ businesses }: { businesses: TrialBusiness[] }) {
  const events = useMemo<Event[]>(() => {
    return businesses
      .filter((b) => b.status === 'TRIAL' && b.trialEndsAt)
      .map((b) => {
        const day = new Date(b.trialEndsAt as string);
        // Evento de día completo: mismo inicio y fin en el día del vencimiento.
        const start = new Date(day.getFullYear(), day.getMonth(), day.getDate());
        const end = new Date(day.getFullYear(), day.getMonth(), day.getDate());
        return {
          title: `Vence prueba: ${b.name}`,
          start,
          end,
          allDay: true,
        } satisfies Event;
      });
  }, [businesses]);

  return (
    <div className="card trial-cal-card">
      <div className="trial-cal-shell">
        <Calendar
          localizer={localizer}
          events={events}
          culture="es"
          messages={messages}
          startAccessor="start"
          endAccessor="end"
          views={['month', 'week', 'day', 'agenda']}
          defaultView="month"
          popup
          style={{ height: 620 }}
        />
      </div>
    </div>
  );
}
