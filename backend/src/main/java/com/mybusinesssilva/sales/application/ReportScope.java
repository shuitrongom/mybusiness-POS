package com.mybusinesssilva.sales.application;

import java.util.Locale;

/**
 * Alcance (scope) de los reportes respecto al MODO PRUEBA: separa el histórico
 * REAL de las ventas/cortes de PRÁCTICA, de forma puramente INFORMATIVA (no
 * borra nada; solo filtra).
 *
 * <ul>
 * <li>{@link #REAL}: solo operación seria ({@code is_test = false}).</li>
 * <li>{@link #TEST}: solo ventas/cortes de prueba ({@code is_test = true}).</li>
 * <li>{@link #BOTH}: todo, sin filtrar por {@code is_test}.</li>
 * </ul>
 */
public enum ReportScope {
    REAL,
    TEST,
    BOTH;

    /**
     * Normaliza el parámetro {@code scope} del request. Si viene {@code null} o en
     * blanco, resuelve el DEFAULT documentado según el estado del modo prueba:
     * {@code both} cuando el modo está ACTIVO, {@code real} cuando está apagado.
     *
     * @param raw            valor crudo del request ({@code real|test|both},
     *                       case-insensitive), o {@code null}
     * @param testModeActive {@code true} si el negocio está en modo prueba
     * @return el scope resuelto
     */
    public static ReportScope resolve(String raw, boolean testModeActive) {
        if (raw != null && !raw.isBlank()) {
            switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "real":
                    return REAL;
                case "test":
                    return TEST;
                case "both":
                    return BOTH;
                default:
                    // Valor desconocido: cae al default por modo (defensivo).
                    break;
            }
        }
        return testModeActive ? BOTH : REAL;
    }

    /**
     * Predicado SQL (incluye el {@code AND} inicial) para filtrar por la columna
     * {@code is_test} de la tabla/alias indicado. Vacío para {@link #BOTH}.
     *
     * @param column columna calificada, p. ej. {@code "s.is_test"} o
     *               {@code "cc.is_test"}
     * @return {@code " AND <column> = false"}, {@code " AND <column> = true"} o
     *         {@code ""}
     */
    public String sqlPredicate(String column) {
        return switch (this) {
            case REAL -> " AND " + column + " = false";
            case TEST -> " AND " + column + " = true";
            case BOTH -> "";
        };
    }
}
