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
- `GMAIL_STATE_SECRET`
- `GMAIL_OAUTH_CLIENT_ID`
- `GMAIL_OAUTH_CLIENT_SECRET`
- `GMAIL_OAUTH_REDIRECT_URI`
- `GMAIL_OAUTH_SCOPES`
- `GMAIL_OAUTH_PROMPT`
- `GMAIL_PUBSUB_TOPIC_NAME`
- `REQUIRE_APP_CHECK` (`true` / `false`)

## Seguridad

- `X-Apigateway-Api-Userinfo` se usa como fuente de identidad tras API Gateway.
- `X-Firebase-AppCheck` se verifica en los endpoints Android.
- El `state` OAuth se firma con HMAC para el callback de Gmail.

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

## Flujo Gmail

- El callback OAuth registra tokens, obtiene el perfil Gmail, y deja un `watch` activo.
- `POST /v1/sync/request` hace un full sync contra `users.messages.list`/`get`.
- `POST /pubsub/gmail` usa `users.history.list` cuando llega `historyId`; si el checkpoint venció, cae a full sync.
- `POST /internal/watch/renew` renueva el `watch` y actualiza `historyId` y expiración.
