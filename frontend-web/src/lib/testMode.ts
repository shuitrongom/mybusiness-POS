import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

/**
 * Estado LIGERO del MODO PRUEBA del negocio, para pintar el banner en el POS y en
 * "Mi caja de hoy", y para resolver el default del selector de scope.
 *
 * Usa el endpoint ligero `GET /settings/test-mode/active`, que CUALQUIER usuario
 * del tenant con módulo `sales` puede leer (incluida la cajera, que DEBE ver el
 * banner). Devuelve `{ active }`. El hook es tolerante: si algo falla, deja
 * `active = false` para no romper la pantalla.
 */
export function useTestMode(): { active: boolean; isLoading: boolean } {
  const q = useQuery({
    queryKey: ['settings', 'test-mode', 'active'],
    queryFn: async () => (await api.get<{ active: boolean }>('/settings/test-mode/active')).data,
    // El estado del modo cambia poco; se revalida al enfocar la ventana.
    staleTime: 30_000,
    retry: false,
  });
  return { active: q.data?.active ?? false, isLoading: q.isLoading };
}
