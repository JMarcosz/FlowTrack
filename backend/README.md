# FlowTrack Backend

Scaffold NestJS para el backend de ingesta Gmail y eventos privados.

## Scripts

- `npm run build`
- `npm run start`
- `npm run start:dev`
- `npm run lint`

## Variables sugeridas

- `PORT`
- `SERVICE_MODE` (`web`, `worker`, `webhook`)
- `FIREBASE_PROJECT_ID`
- `GOOGLE_APPLICATION_CREDENTIALS`
- `REQUIRE_APP_CHECK`

## Rutas

- `GET /health`
- `POST /v1/integrations/gmail/connect/start`
- `GET /oauth/google/callback`
- `GET /v1/integrations/gmail/status`
- `POST /v1/integrations/gmail/disconnect`
- `POST /v1/sync/request`
- `POST /pubsub/gmail`
- `POST /pubsub/email-ingestion`
- `POST /internal/watch/renew`

