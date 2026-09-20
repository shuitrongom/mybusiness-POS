# MyBusiness Silva — POS en la Nube

Sistema de Punto de Venta SaaS multi-empresa (multi-tenant), multi-giro, para el mercado mexicano.

## Stack tecnológico (punta 2026)

| Capa | Tecnología |
|------|-----------|
| Backend | Java 25 (LTS) + Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11), arquitectura hexagonal |
| Build | Maven |
| Base de datos | PostgreSQL (schema-por-empresa + Row-Level Security) |
| Migraciones | Flyway |
| Frontend web | React + Vite + TypeScript (PWA, offline) |
| App móvil | Flutter (iOS/Android) — dueño del negocio |
| Contenedores | Docker / Docker Compose |

## Estructura del repositorio

```
.
├── backend/         # API Spring Boot (hexagonal): dominio + módulos + adaptadores
├── frontend-web/    # Aplicación web PWA (React + Vite)
├── mobile/          # App móvil del dueño (Flutter)
├── infra/           # Docker Compose, scripts de infraestructura y despliegue
├── docs/            # Documentación adicional
└── .kiro/specs/     # Especificación (requisitos, diseño, tareas)
```

## Modelo de negocio

- Venta de **licencia definitiva por módulos** (pago único), con **periodo de prueba** configurable.
- Módulos adicionales posteriores se venden como **excedente** (pago único adicional).
- El **Super Admin** crea negocios, elige plan, define meses de prueba y factura sus ventas (CFDI o PDF).

## Estado del proyecto

En construcción por etapas (ver `.kiro/specs/pos-cloud/tasks.md`). Cada etapa se compila y verifica
antes de avanzar.

## Documentación

- Requisitos: `.kiro/specs/pos-cloud/requirements.md`
- Diseño técnico: `.kiro/specs/pos-cloud/design.md`
- Plan de implementación: `.kiro/specs/pos-cloud/tasks.md`
