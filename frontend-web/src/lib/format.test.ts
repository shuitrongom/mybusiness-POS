import { describe, expect, it } from 'vitest';
import { money, shortDate } from './format';

/** Pruebas de las utilidades de formato compartidas por las pantallas. */
describe('format', () => {
  it('formatea moneda mexicana', () => {
    const result = money(1234.5);
    // Contiene el símbolo de peso y los decimales.
    expect(result).toContain('$');
    expect(result).toContain('1,234.50');
  });

  it('maneja valores nulos/cero en moneda', () => {
    expect(money(0)).toContain('0.00');
  });

  it('devuelve guion para fecha vacía', () => {
    expect(shortDate(null)).toBe('—');
    expect(shortDate(undefined)).toBe('—');
  });
});
