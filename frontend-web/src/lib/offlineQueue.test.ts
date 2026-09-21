import { beforeEach, describe, expect, it, vi } from 'vitest';
import { queueSale, pendingCount, flushQueue } from './offlineQueue';

/**
 * Pruebas de la cola de ventas offline (crítica para el POS). Verifican que las ventas se
 * encolan, se cuentan, y que al sincronizar se envían y se eliminan de la cola; y que las que
 * fallan permanecen para el próximo intento.
 */
describe('offlineQueue', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('encola ventas y las cuenta', () => {
    expect(pendingCount()).toBe(0);
    queueSale({ idempotencyKey: 'a', total: 100 });
    queueSale({ idempotencyKey: 'b', total: 50 });
    expect(pendingCount()).toBe(2);
  });

  it('sincroniza y vacía la cola cuando el envío tiene éxito', async () => {
    queueSale({ idempotencyKey: 'a' });
    queueSale({ idempotencyKey: 'b' });

    const send = vi.fn().mockResolvedValue(undefined);
    const synced = await flushQueue(send);

    expect(synced).toBe(2);
    expect(send).toHaveBeenCalledTimes(2);
    expect(pendingCount()).toBe(0);
  });

  it('conserva en la cola las ventas cuyo envío falla', async () => {
    queueSale({ idempotencyKey: 'ok' });
    queueSale({ idempotencyKey: 'fail' });

    // La segunda venta falla al enviarse.
    const send = vi.fn()
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('sin conexión'));

    const synced = await flushQueue(send);

    expect(synced).toBe(1);
    expect(pendingCount()).toBe(1); // la fallida permanece
  });
});
