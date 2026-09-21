/**
 * Cola de ventas offline. Cuando no hay conexión, las ventas se guardan localmente y se
 * sincronizan al reconectar. La idempotencia del backend (por idempotencyKey) garantiza que,
 * aunque una venta se reintente, no se duplique.
 *
 * Para v1 se usa localStorage (simple y suficiente). Se puede migrar a IndexedDB si el volumen
 * lo requiere, manteniendo esta misma interfaz.
 */
const QUEUE_KEY = 'mbs.offlineSales';

type QueuedSale = Record<string, unknown>;

function readQueue(): QueuedSale[] {
  try {
    return JSON.parse(localStorage.getItem(QUEUE_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function writeQueue(queue: QueuedSale[]): void {
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
}

/** Agrega una venta a la cola offline. */
export function queueSale(sale: QueuedSale): void {
  const queue = readQueue();
  queue.push(sale);
  writeQueue(queue);
}

/** Número de ventas pendientes de sincronizar. */
export function pendingCount(): number {
  return readQueue().length;
}

/**
 * Envía las ventas encoladas usando la función dada. Cada venta enviada con éxito se elimina
 * de la cola. Devuelve cuántas se sincronizaron.
 */
export async function flushQueue(
  send: (sale: QueuedSale) => Promise<void>,
): Promise<number> {
  const queue = readQueue();
  const remaining: QueuedSale[] = [];
  let synced = 0;

  for (const sale of queue) {
    try {
      await send(sale);
      synced += 1;
    } catch {
      // Si falla, se conserva en la cola para el próximo intento.
      remaining.push(sale);
    }
  }

  writeQueue(remaining);
  return synced;
}
