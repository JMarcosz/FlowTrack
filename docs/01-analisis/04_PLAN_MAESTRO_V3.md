# Plan maestro v3 - FlowTrack Email Backend

> Este documento complementa el plan maestro v2 con el alcance de ingesta de correo y backend NestJS.
> No invalida el v2 para el flujo Android existente; solo define la nueva línea de trabajo del backend.

## Objetivo

Agregar un backend privado en Cloud Run para ingestión de Gmail, sincronización remota y eventos de notificación, sin romper la importación manual local ya existente en Android.

## Alcance funcional

- OAuth Gmail con callback a Android.
- Sincronización bajo demanda desde `Sincronizar ahora`.
- Persistencia de configuración Gmail por usuario en Firestore.
- Ingesta idempotente de eventos de correo.
- Detección de duplicados entre correo y cargas manuales.
- Migración futura de la lógica de notificaciones desde `functions/` a Cloud Run.

## Contratos clave

- `Transaccion` agrega:
  - `origen`
  - `sourceEventId`
  - `sourceMessageId`
  - `sourceTransactionId`
  - `actualizadoEn`
  - `estado`
  - `afectaBalance`
  - `posibleDuplicado`
  - `motivoRechazo`
- Importaciones manuales:
  - `estado = APROBADA`
  - `afectaBalance = true`
  - `origen = IMPORTACION_ARCHIVO`

## Backend

- `backend/` usa NestJS.
- Rutas públicas:
  - `GET /health`
  - `POST /v1/integrations/gmail/connect/start`
  - `GET /oauth/google/callback`
  - `GET /v1/integrations/gmail/status`
  - `POST /v1/integrations/gmail/disconnect`
  - `POST /v1/sync/request`
- Rutas privadas:
  - `POST /pubsub/gmail`
  - `POST /pubsub/email-ingestion`
  - `POST /internal/watch/renew`

## Estado actual

- Scaffold NestJS creado.
- Implementada validación defensiva de `X-Apigateway-Api-Userinfo` y `X-Firebase-AppCheck`.
- Implementado `state` OAuth firmado y persistencia básica de la integración Gmail en Firestore.
- Falta implementar sincronización Gmail real, parser de correos, watch renovado y migración total de `functions/`.
