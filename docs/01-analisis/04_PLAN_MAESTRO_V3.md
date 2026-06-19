# Plan maestro v3 - FlowTrack Email Backend

> Este documento complementa el plan maestro v2 con el alcance de ingesta de correo y backend NestJS.
> No invalida el v2 para el flujo Android existente; solo define la nueva linea de trabajo del backend.

## Objetivo

Agregar un backend privado en Cloud Run para ingestion de Gmail, sincronizacion remota y eventos de notificacion, sin romper la importacion manual local ya existente en Android.

## Alcance funcional

- OAuth Gmail con callback a Android.
- Sincronizacion bajo demanda desde `Sincronizar ahora`.
- Persistencia de configuracion Gmail por usuario en Firestore.
- Ingesta idempotente de eventos de correo.
- Deteccion de duplicados entre correo y cargas manuales.
- Migracion futura de la logica de notificaciones desde `functions/` a Cloud Run.

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
- Rutas publicas:
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
- Implementada validacion defensiva de `X-Apigateway-Api-Userinfo` y `X-Firebase-AppCheck`.
- Implementado `state` OAuth firmado y persistencia basica de la integracion Gmail en Firestore.
- Implementada la ingesta canonica de correos con parser BanReservas, idempotencia por `sourceEventId` y escritura de transacciones/cuentas/tarjetas en Firestore.
- Falta implementar sincronizacion Gmail real contra la API de Gmail, refresh automatico de watch y migracion total de `functions/`.
