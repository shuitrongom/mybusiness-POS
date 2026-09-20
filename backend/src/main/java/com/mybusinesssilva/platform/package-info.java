/**
 * Capa de plataforma (transversal). Contiene preocupaciones que atraviesan todos los
 * módulos de negocio: multi-tenancy, seguridad, auditoría, licenciamiento, facturación
 * del Super Admin, notificaciones y tipos compartidos.
 *
 * <p>No contiene reglas de negocio de un dominio específico; provee los cimientos sobre
 * los que operan los módulos ({@code catalog}, {@code sales}, {@code inventory}, etc.).
 */
package com.mybusinesssilva.platform;
