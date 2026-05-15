# Plan de acción para Claude Code — App de Finanzas Personales RD

> **Documento de orquestación para Claude Code.** Contiene el contexto completo del proyecto, instrucciones de ejecución secuencial y comandos específicos del MCP de Firebase para automatizar setup y modelado.

---

## 1. Contexto del proyecto

### Visión

App Android nativa en Kotlin que permite a un usuario consolidar, visualizar, analizar y exportar las transacciones de sus cuentas bancarias y tarjetas de crédito en República Dominicana. El usuario sube estados de cuenta (PDF, CSV, XLS) y la app los parsea, normaliza, almacena y presenta en dashboards.

### Bancos soportados en MVP

- **BanReservas** — PDF tabular (cuenta corriente)
- **Banco Popular Dominicano** — CSV de consulta web (cuenta corriente/ahorro)
- **Qik Banco Digital** — PDF narrativo (tarjeta de crédito)
- **Genérico de tarjeta de crédito** — XLS legacy sin identificador de banco (probablemente Asociación Cibao y otros)
- **BHD** — pendiente de muestra, se agregará post-MVP

### Restricciones técnicas no negociables

| Decisión | Valor |
|----------|-------|
| Lenguaje | Kotlin 2.0.20 |
| UI | Jetpack Compose |
| Arquitectura | Clean Architecture + MVVM |
| Inyección de dependencias | Hilt 2.52 |
| Backend | Firebase Firestore (sin Cloud Storage, sin Functions) |
| Auth | Google Sign-In via Credential Manager |
| Procesamiento | 100% en el cliente (sin servidor) |
| Multi-usuario | Sí, datos aislados por `uid` |
| Monedas | DOP (default) + USD (con conversión BCRD) |
| Categorización | Reglas precargadas globales + reglas personales aprendidas |
| Exportación | XLSX local con `fastexcel` |
| Mínimo SDK | API 26 (Android 8 Oreo) |
| Target SDK | API 35 |

### Decisiones de diseño consolidadas

1. **Transacciones planas** bajo `/usuarios/{uid}/transacciones` — no anidadas en cuentas, para queries cruzadas eficientes.
2. **Hash determinístico SHA-256** como ID de transacción para idempotencia en re-cargas.
3. **Parsers como plugins**: contrato `BankParser` + `ParserRegistry` con detección por confianza.
4. **Detección con fallback**: si confianza < 0.4, se muestra catálogo completo de bancos al usuario para selección manual.
5. **Cargas inmutables**: una vez registrada una carga, no se modifica (auditoría).
6. **Transacciones derivadas conservadas**: el impuesto DGII 0.15% se mantiene como transacción separada con `transaccionPadreId` apuntando a la operación origen.
7. **Datos de tarjeta**: auto-extraídos del documento cuando el banco los incluye (caso Qik); manuales como fallback con persistencia.
8. **Catálogo de bancos dinámico** en Firestore — no hardcoded.

---

## 2. Herramientas disponibles en Claude Code

Claude Code tiene acceso a los siguientes recursos durante esta ejecución:

- **MCP de Firebase**: para crear proyecto, configurar Firestore, deployar reglas e índices, sembrar colecciones públicas (catálogos).
- **Bash + sistema de archivos**: para clonar proyecto Android, instalar dependencias, ejecutar Gradle.
- **Editor de código**: para crear los archivos Kotlin de cada capa del proyecto.

> **Importante**: Cuando este plan diga "usa el MCP de Firebase para X", Claude Code debe invocar las herramientas correspondientes del MCP, NO simular comandos en bash. Si una operación específica no está cubierta por el MCP, Claude Code lo notificará y propondrá la alternativa (manual via consola o Firebase CLI).

---

## 3. Hitos de ejecución

El proyecto se ejecuta en **6 hitos secuenciales**. Cada hito tiene precondiciones, tareas concretas, criterios de aceptación y validación.

### Hito 0 — Preparación del entorno

**Precondiciones:** ninguna.

**Tareas:**

1. Verificar herramientas instaladas:
   - JDK 17+
   - Android Studio Ladybug o superior
   - Firebase CLI (`firebase --version`)
   - Node.js 20+ (para Firebase CLI)
2. Verificar acceso al MCP de Firebase listando proyectos existentes.
3. Crear directorio raíz del proyecto: `~/proyectos/finanzas-rd/`.

**Criterio de aceptación:** Claude Code puede invocar `firebase projects:list` con éxito y el MCP responde con la lista de proyectos del usuario.

---

### Hito 1 — Provisionar Firebase

**Precondiciones:** Hito 0 completo.

**Tareas (vía MCP de Firebase):**

1. **Crear proyecto Firebase** con ID `finanzas-rd-prod` (o el que indique el usuario).
2. **Habilitar Firestore** en modo nativo, región `nam5` (multi-región Estados Unidos, la más cercana a República Dominicana con buena latencia).
3. **Habilitar Authentication** con el provider de Google.
4. **Crear app Android** dentro del proyecto con package `com.jeanmarco.finanzas`. Descargar `google-services.json` y guardarlo en `~/proyectos/finanzas-rd/app/`.
5. **Deployar reglas de seguridad** (archivo `firestore.rules`, contenido en sección 5 de este documento).
6. **Deployar índices compuestos** (archivo `firestore.indexes.json`, contenido en sección 5).
7. **Sembrar catálogo público de bancos** en colección `/catalogoBancos`. Datos en sección 6.
8. **Sembrar catálogo público de categorías** en colección `/catalogoCategorias`. Datos en sección 6.
9. **Sembrar reglas de categorización globales** en colección `/reglasCategorizacionGlobales`. Datos en sección 6.

**Criterio de aceptación:**
- El proyecto Firebase existe y responde a queries.
- Las reglas de seguridad están activas (verificable con el simulador de reglas del MCP).
- Las 3 colecciones públicas tienen los documentos sembrados.
- `google-services.json` está en el directorio correcto.

---

### Hito 2 — Bootstrap del proyecto Android

**Precondiciones:** Hito 1 completo.

**Tareas:**

1. Crear proyecto Android con plantilla "Empty Activity (Compose)":
   - Package: `com.jeanmarco.finanzas`
   - Min SDK: 26
   - Target SDK: 35
   - Kotlin DSL para Gradle
2. Configurar `libs.versions.toml` con todas las dependencias listadas en sección 7.
3. Aplicar plugin de `google-services` al `build.gradle.kts` del módulo `app`.
4. Crear la estructura de carpetas Clean Architecture (sección 8).
5. Configurar Hilt:
   - Anotar `Application` con `@HiltAndroidApp`
   - Crear módulos base: `FirebaseModule`, `RepositoryModule`, `ParserModule`, `UseCaseModule`
6. Configurar tema Material 3 con colores institucionales (azul como primary).
7. Crear `NavGraph` con las 8 pantallas vacías (stubs):
   - `OnboardingScreen`, `DashboardScreen`, `UploadScreen`, `HistorialScreen`, `CuentaDetalleScreen`, `TarjetasScreen`, `CategoriasScreen`, `ExportarScreen`.
8. Configurar ProGuard/R8 con reglas para Firebase y Compose.
9. Primera compilación: `./gradlew assembleDebug` debe pasar.

**Criterio de aceptación:** APK debug se compila sin errores, se instala en emulador, muestra la pantalla de Onboarding vacía.

---

### Hito 3 — Capa de dominio + autenticación

**Precondiciones:** Hito 2 completo.

**Tareas:**

1. **Implementar todos los modelos de dominio** según especificación en `02_MODELADO_DE_DATOS.md`:
   - `Transaccion`, `Cuenta`, `Tarjeta`, `EstadoTarjeta`, `MovimientoTarjeta`
   - `Carga`, `ReglaCategoria`, `Banco`, `Categoria`
   - Enums: `TipoTransaccion`, `Moneda`, `TipoCuenta`, `TipoDocumento`, `EstadoCarga`
2. **Implementar interfaces de repositorio** en `domain/repositories/`.
3. **Implementar Google Sign-In** con Credential Manager:
   - Configurar OAuth 2.0 en Google Cloud Console (usar `web_client_id`)
   - Crear `AuthRepository` con `signIn()`, `signOut()`, `currentUser`
   - Flujo: si `currentUser == null` → `OnboardingScreen`, sino → `DashboardScreen`
4. **Implementar `UsuarioRepository`** con creación automática del documento `/usuarios/{uid}` en primer login.
5. **Implementar `BancoRepository`** que lee del catálogo público con caché local de 24h.
6. **Crear DTOs y mappers** para Firestore.

**Criterio de aceptación:**
- Usuario puede iniciar sesión con Google.
- Se crea automáticamente su documento raíz en `/usuarios/{uid}`.
- La app lista los bancos del catálogo en una pantalla de prueba.
- Logout funciona y devuelve a Onboarding.

---

### Hito 4 — Núcleo de parsers (extensible)

**Precondiciones:** Hito 3 completo.

**Tareas:**

1. **Implementar el contrato `BankParser`** completo según `02_MODELADO_DE_DATOS.md` sección 2.
2. **Implementar `ParserRegistry`** con:
   - Inyección de todos los parsers via Hilt `Set<BankParser>`
   - Método `detectar(archivo)` que devuelve lista ordenada por confianza
   - Lógica de umbrales (alta/media/baja)
3. **Implementar `BanReservasPdfParser`**:
   - Detección por texto "BANRESERVAS", URL "banreservas.com.do", e IBAN `DO57BRRD`
   - Parser de tabla con columnas Fecha, Referencia, Concepto, Cheques, Depósitos, Balance
   - Extracción de transacciones derivadas DGII 0.15% con `transaccionPadreId`
4. **Implementar `PopularCsvParser`**:
   - Detección por header "Banco Popular Dominicano" en línea 7
   - Skip de 10 líneas de metadata
   - Mapeo de "Descripción Corta" a `TipoTransaccion` (DEBITO/CREDITO)
5. **Implementar `QikPdfParser`**:
   - Detección por "qik.com.do" en footer y "Qik Banco Digital Dominicano" en header
   - Parser narrativo con regex para extraer datos de tarjeta del header
   - Parser de tabla Fecha, Entrada, Descripción, Monto
   - Signo del monto define tipo (negativo = pago/crédito, positivo = compra/débito)
   - **Auto-extracción de tasa de interés, fecha de corte, fecha de pago, límite**
6. **Implementar `GenericoTarjetaXlsParser`** para Cibao y similares:
   - Sin detección por contenido (confianza siempre = 0.3)
   - Detección por estructura: hoja "CreditCardDetail", columnas USD/DOP paralelas
   - Devuelve `RequiereConfirmacion` si confianza media
7. **Implementar `NormalizadorTransacciones`** que aplica el formato de salida estandarizado.
8. **Tests unitarios de cada parser** con archivos fixture en `app/src/test/resources/fixtures/`.

**Criterio de aceptación:**
- Cada parser pasa sus tests unitarios contra los archivos reales.
- El registry detecta correctamente el banco para los 4 archivos de muestra.
- Para el XLS genérico, devuelve `RequiereConfirmacion` con la lista correcta de candidatos.

---

### Hito 5 — Pantalla de Upload + persistencia

**Precondiciones:** Hito 4 completo.

**Tareas:**

1. **Pantalla `UploadScreen`** con:
   - Botón de selección de archivo (PDF, CSV, XLS, XLSX)
   - Estados: Idle, Detectando, ConfirmacionRequerida, Procesando, Exito, Error
2. **`ProcesarArchivoUseCase`**:
   - Recibe `Uri` del archivo
   - Lee bytes en memoria (máximo 10 MB, validar)
   - Invoca `ParserRegistry.detectar()`
   - Según confianza:
     - Alta → ejecuta parser directamente
     - Media → emite `ConfirmacionRequerida` con top 3
     - Baja → emite `ConfirmacionRequerida` con catálogo completo
   - Una vez el usuario confirma (o automático), invoca `parser.parsear()`
   - Genera hash determinístico por transacción
   - Persiste en Firestore vía `WriteBatch` de máximo 500 documentos por batch
   - Crea documento en `/cargas/{cargaId}` con resumen inmutable
3. **Manejo de duplicados**: Firestore `set()` con merge en transacciones con mismo hash es idempotente.
4. **Pantalla `HistorialScreen`**: lista de `/cargas` con drill-down a transacciones de esa carga.

**Criterio de aceptación:**
- Subir el PDF de BanReservas, el CSV de Popular y el PDF de Qik produce los documentos esperados en Firestore.
- Re-subir el mismo archivo NO duplica transacciones.
- Subir el XLS genérico abre el diálogo de confirmación.
- El historial muestra las 3 cargas.

---

### Hito 6 — Dashboard, gráficos, categorización, exportación

**Precondiciones:** Hito 5 completo.

**Tareas:**

1. **Categorización automática**:
   - Al insertar transacciones, ejecutar `CategorizarTransaccionUseCase`:
     - Buscar match en `/usuarios/{uid}/reglasCategorias` (personales)
     - Si no, buscar en `/reglasCategorizacionGlobales`
     - Si no, marcar como "Sin categorizar"
2. **Recategorización manual**:
   - En detalle de transacción, permitir cambiar categoría
   - Al cambiar, crear o actualizar regla personal con patrón = `descripcionNormalizada`
3. **Conversión de moneda DOP/USD**:
   - `ConvertirMonedaUseCase` consulta `/tasasCambio/{fecha}` (cache compartido)
   - Si no existe, llama a API BCRD, cachea y devuelve
   - Fallback a TasaReal.com si BCRD falla
4. **Resúmenes**:
   - `ObtenerResumenPeriodoUseCase` con parámetros: rango fechas, bancos, monedas, categorías
   - Agrupa por día/semana/mes según selección
   - Devuelve `Resumen` con ingresos, gastos, neto, por categoría, por banco
5. **Dashboard**:
   - Cards de balance por moneda
   - Gráfico de barras 30 días (vico)
   - Gráfico de torta por categoría
   - Lista de últimas transacciones
6. **Detalle de cuenta**: transacciones filtrables + resumen del período
7. **Pantalla de tarjetas**: estado, días para corte/pago, intereses aproximados
8. **`ExportarXlsxUseCase`**:
   - Genera XLSX con `fastexcel`
   - Múltiples hojas: Transacciones, Resumen, Tarjetas
   - Lo guarda en `cacheDir` y comparte vía `FileProvider` con `ACTION_SEND`

**Criterio de aceptación:** ciclo completo funcionando: cargar archivo → ver dashboard con datos → recategorizar → exportar a XLSX.

---

## 4. Configuración del MCP de Firebase

### Llamadas que Claude Code debe realizar al MCP

Durante Hito 1, Claude Code ejecuta:

```text
1. firebase_create_project(projectId="finanzas-rd-prod", displayName="Finanzas RD")
2. firebase_enable_firestore(projectId="finanzas-rd-prod", location="nam5", mode="native")
3. firebase_enable_auth(projectId="finanzas-rd-prod", providers=["google"])
4. firebase_create_android_app(projectId="finanzas-rd-prod", packageName="com.jeanmarco.finanzas")
5. firebase_download_config(projectId="finanzas-rd-prod", appId="<id>", outputPath="~/proyectos/finanzas-rd/app/google-services.json")
6. firebase_deploy_firestore_rules(projectId="finanzas-rd-prod", rulesPath="./firestore.rules")
7. firebase_deploy_firestore_indexes(projectId="finanzas-rd-prod", indexesPath="./firestore.indexes.json")
8. firebase_firestore_seed_collection(projectId="finanzas-rd-prod", collection="catalogoBancos", documents=[...])
9. firebase_firestore_seed_collection(projectId="finanzas-rd-prod", collection="catalogoCategorias", documents=[...])
10. firebase_firestore_seed_collection(projectId="finanzas-rd-prod", collection="reglasCategorizacionGlobales", documents=[...])
```

> **Nota**: los nombres exactos de las herramientas del MCP pueden variar según la implementación. Claude Code debe inspeccionar las herramientas disponibles (`mcp__firebase__*`) y mapear estas operaciones a las llamadas equivalentes.

### Si una operación no está soportada por el MCP

Claude Code debe:
1. Reportarlo claramente al usuario.
2. Proponer una alternativa con Firebase CLI o consola web.
3. Generar los archivos necesarios (ej: scripts de seeding en `scripts/seed-firestore.ts`) para que el usuario los ejecute manualmente.

---

## 5. Archivos de configuración a crear

### 5.1 `firestore.rules`

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isAuth() {
      return request.auth != null;
    }
    function isOwner(uid) {
      return isAuth() && request.auth.uid == uid;
    }
    function isValidString(field, maxLen) {
      return field is string && field.size() > 0 && field.size() <= maxLen;
    }
    function isPositiveNumber(field) {
      return field is number && field >= 0;
    }

    match /catalogoBancos/{codigo} {
      allow read: if isAuth();
      allow write: if false;
    }

    match /catalogoCategorias/{categoriaId} {
      allow read: if isAuth();
      allow write: if false;
    }

    match /reglasCategorizacionGlobales/{reglaId} {
      allow read: if isAuth();
      allow write: if false;
    }

    match /tasasCambio/{fecha} {
      allow read: if isAuth();
      allow write: if isAuth();
    }

    match /usuarios/{uid} {
      allow read, write: if isOwner(uid);

      match /cuentas/{cuentaId} {
        allow read: if isOwner(uid);
        allow create: if isOwner(uid)
                      && isValidString(request.resource.data.bancoCodigo, 50)
                      && isValidString(request.resource.data.numero, 50);
        allow update, delete: if isOwner(uid);
      }

      match /tarjetas/{tarjetaId} {
        allow read: if isOwner(uid);
        allow create: if isOwner(uid)
                      && isValidString(request.resource.data.bancoCodigo, 50)
                      && isValidString(request.resource.data.ultimos4, 4)
                      && isPositiveNumber(request.resource.data.limiteCredito);
        allow update, delete: if isOwner(uid);
      }

      match /transacciones/{txId} {
        allow read: if isOwner(uid);
        allow create: if isOwner(uid)
                      && request.resource.data.monto is number
                      && request.resource.data.fecha is timestamp;
        allow update: if isOwner(uid)
                      && request.resource.data.diff(resource.data)
                         .affectedKeys()
                         .hasOnly(['categoriaId', 'categoriaAutomatica', 'notaUsuario']);
        allow delete: if isOwner(uid);
      }

      match /movimientosTarjeta/{movId} {
        allow read: if isOwner(uid);
        allow create, update, delete: if isOwner(uid);
      }

      match /estadosTarjeta/{periodoId} {
        allow read: if isOwner(uid);
        allow create, update, delete: if isOwner(uid);
      }

      match /reglasCategorias/{reglaId} {
        allow read, write: if isOwner(uid);
      }

      match /cargas/{cargaId} {
        allow read: if isOwner(uid);
        allow create: if isOwner(uid);
        allow update, delete: if false;
      }
    }
  }
}
```

### 5.2 `firestore.indexes.json`

```json
{
  "indexes": [
    {
      "collectionGroup": "transacciones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "cuentaId", "order": "ASCENDING" },
        { "fieldPath": "fecha", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transacciones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "bancoCodigo", "order": "ASCENDING" },
        { "fieldPath": "fecha", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transacciones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "categoriaId", "order": "ASCENDING" },
        { "fieldPath": "fecha", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transacciones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "tipo", "order": "ASCENDING" },
        { "fieldPath": "moneda", "order": "ASCENDING" },
        { "fieldPath": "fecha", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "movimientosTarjeta",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "tarjetaId", "order": "ASCENDING" },
        { "fieldPath": "fecha", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "reglasCategorias",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "activa", "order": "ASCENDING" },
        { "fieldPath": "confianza", "order": "DESCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

---

## 6. Datos de seeding para colecciones públicas

### 6.1 `/catalogoBancos`

```json
[
  {
    "id": "BANRESERVAS",
    "codigo": "BANRESERVAS",
    "nombre": "BanReservas",
    "nombreCompleto": "Banco de Reservas de la República Dominicana",
    "colorPrimario": "#005DA8",
    "pais": "DO",
    "monedasSoportadas": ["DOP", "USD"],
    "tiposCuenta": ["CORRIENTE", "AHORRO"],
    "tieneParser": true,
    "activo": true
  },
  {
    "id": "POPULAR",
    "codigo": "POPULAR",
    "nombre": "Banco Popular",
    "nombreCompleto": "Banco Popular Dominicano",
    "colorPrimario": "#005CAA",
    "pais": "DO",
    "monedasSoportadas": ["DOP", "USD"],
    "tiposCuenta": ["CORRIENTE", "AHORRO", "CREDITO"],
    "tieneParser": true,
    "activo": true
  },
  {
    "id": "QIK",
    "codigo": "QIK",
    "nombre": "Qik",
    "nombreCompleto": "Qik Banco Digital Dominicano",
    "colorPrimario": "#0099E5",
    "pais": "DO",
    "monedasSoportadas": ["DOP"],
    "tiposCuenta": ["AHORRO", "CREDITO"],
    "tieneParser": true,
    "activo": true
  },
  {
    "id": "CIBAO",
    "codigo": "CIBAO",
    "nombre": "Asociación Cibao",
    "nombreCompleto": "Asociación Cibao de Ahorros y Préstamos",
    "colorPrimario": "#E30613",
    "pais": "DO",
    "monedasSoportadas": ["DOP", "USD"],
    "tiposCuenta": ["AHORRO", "CREDITO"],
    "tieneParser": true,
    "activo": true
  },
  {
    "id": "BHD",
    "codigo": "BHD",
    "nombre": "BHD",
    "nombreCompleto": "Banco Múltiple BHD",
    "colorPrimario": "#003F7F",
    "pais": "DO",
    "monedasSoportadas": ["DOP", "USD"],
    "tiposCuenta": ["CORRIENTE", "AHORRO", "CREDITO"],
    "tieneParser": false,
    "activo": true
  }
]
```

### 6.2 `/catalogoCategorias`

```json
[
  { "id": "alimentacion", "nombre": "Alimentación", "icono": "ti-shopping-cart", "color": "#E24B4A", "tipo": "GASTO" },
  { "id": "transporte", "nombre": "Transporte", "icono": "ti-car", "color": "#378ADD", "tipo": "GASTO" },
  { "id": "salud", "nombre": "Salud", "icono": "ti-heart", "color": "#D4537E", "tipo": "GASTO" },
  { "id": "entretenimiento", "nombre": "Entretenimiento", "icono": "ti-movie", "color": "#7F77DD", "tipo": "GASTO" },
  { "id": "suscripciones", "nombre": "Suscripciones", "icono": "ti-repeat", "color": "#EF9F27", "tipo": "GASTO" },
  { "id": "servicios", "nombre": "Servicios", "icono": "ti-bolt", "color": "#BA7517", "tipo": "GASTO" },
  { "id": "compras", "nombre": "Compras", "icono": "ti-bag", "color": "#D85A30", "tipo": "GASTO" },
  { "id": "atm", "nombre": "Retiro ATM", "icono": "ti-cash", "color": "#888780", "tipo": "GASTO" },
  { "id": "transferencia_enviada", "nombre": "Transferencia enviada", "icono": "ti-arrow-up-right", "color": "#5F5E5A", "tipo": "GASTO" },
  { "id": "impuestos", "nombre": "Impuestos", "icono": "ti-receipt", "color": "#444441", "tipo": "GASTO" },
  { "id": "intereses_comisiones", "nombre": "Intereses y comisiones", "icono": "ti-percentage", "color": "#993C1D", "tipo": "GASTO" },
  { "id": "salario", "nombre": "Salario", "icono": "ti-briefcase", "color": "#3B6D11", "tipo": "INGRESO" },
  { "id": "transferencia_recibida", "nombre": "Transferencia recibida", "icono": "ti-arrow-down-left", "color": "#1D9E75", "tipo": "INGRESO" },
  { "id": "deposito", "nombre": "Depósito", "icono": "ti-coin", "color": "#639922", "tipo": "INGRESO" },
  { "id": "cashback", "nombre": "Cashback", "icono": "ti-gift", "color": "#97C459", "tipo": "INGRESO" },
  { "id": "pago_tarjeta", "nombre": "Pago a tarjeta", "icono": "ti-credit-card", "color": "#0F6E56", "tipo": "TRANSFERENCIA_INTERNA" },
  { "id": "sin_categorizar", "nombre": "Sin categorizar", "icono": "ti-question-mark", "color": "#B4B2A9", "tipo": "INDEFINIDO" }
]
```

### 6.3 `/reglasCategorizacionGlobales`

```json
[
  { "patron": "UBER", "tipoMatch": "CONTIENE", "categoriaId": "transporte", "prioridad": 100 },
  { "patron": "DIDI", "tipoMatch": "CONTIENE", "categoriaId": "transporte", "prioridad": 100 },
  { "patron": "OPRET METRO", "tipoMatch": "CONTIENE", "categoriaId": "transporte", "prioridad": 100 },
  { "patron": "PEDIDOSYA", "tipoMatch": "CONTIENE", "categoriaId": "alimentacion", "prioridad": 100 },
  { "patron": "HELADOS BON", "tipoMatch": "CONTIENE", "categoriaId": "alimentacion", "prioridad": 100 },
  { "patron": "CHUCK E CHEESE", "tipoMatch": "CONTIENE", "categoriaId": "entretenimiento", "prioridad": 100 },
  { "patron": "NETFLIX", "tipoMatch": "CONTIENE", "categoriaId": "suscripciones", "prioridad": 100 },
  { "patron": "SPOTIFY", "tipoMatch": "CONTIENE", "categoriaId": "suscripciones", "prioridad": 100 },
  { "patron": "GOOGLE", "tipoMatch": "CONTIENE", "categoriaId": "suscripciones", "prioridad": 90 },
  { "patron": "CRUNCHYROLL", "tipoMatch": "CONTIENE", "categoriaId": "suscripciones", "prioridad": 100 },
  { "patron": "CONSUMO POS", "tipoMatch": "CONTIENE", "categoriaId": "compras", "prioridad": 50 },
  { "patron": "RETIRO ATM", "tipoMatch": "CONTIENE", "categoriaId": "atm", "prioridad": 100 },
  { "patron": "RETIRO SAB", "tipoMatch": "CONTIENE", "categoriaId": "atm", "prioridad": 100 },
  { "patron": "COBRO IMP", "tipoMatch": "CONTIENE", "categoriaId": "impuestos", "prioridad": 100 },
  { "patron": "DGII", "tipoMatch": "CONTIENE", "categoriaId": "impuestos", "prioridad": 100 },
  { "patron": "INTERES", "tipoMatch": "CONTIENE", "categoriaId": "intereses_comisiones", "prioridad": 100 },
  { "patron": "COMISION", "tipoMatch": "CONTIENE", "categoriaId": "intereses_comisiones", "prioridad": 100 },
  { "patron": "NOMINAS ACH", "tipoMatch": "CONTIENE", "categoriaId": "salario", "prioridad": 100 },
  { "patron": "TRANSFERENCIA", "tipoMatch": "CONTIENE", "categoriaId": "transferencia_recibida", "prioridad": 50 },
  { "patron": "DEPOSITO", "tipoMatch": "CONTIENE", "categoriaId": "deposito", "prioridad": 100 },
  { "patron": "CASHBACK", "tipoMatch": "CONTIENE", "categoriaId": "cashback", "prioridad": 100 },
  { "patron": "REBATE", "tipoMatch": "CONTIENE", "categoriaId": "cashback", "prioridad": 100 },
  { "patron": "PAGO A TARJETA", "tipoMatch": "CONTIENE", "categoriaId": "pago_tarjeta", "prioridad": 100 }
]
```

Cada documento agregar campos: `activa: true`, `creadoPor: "SISTEMA"`, `version: 1`.

---

## 7. Dependencias Android (`libs.versions.toml`)

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.20"
ksp = "2.0.20-1.0.25"
hilt = "2.52"
firebaseBom = "33.5.1"
compose = "1.7.5"
composeBom = "2024.11.00"
nav = "2.8.4"
coroutines = "1.9.0"
credentials = "1.3.0"
googleid = "1.1.1"
pdfbox = "2.0.27.0"
poi = "5.3.0"
fastexcel = "0.18.4"
opencsv = "5.9"
vico = "2.0.0-beta.1"
coil = "2.7.0"
datastore = "1.1.1"

[libraries]
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore-ktx" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics-ktx" }
firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics-ktx" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "nav" }

coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }

credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }

pdfbox-android = { group = "com.tom-roush", name = "pdfbox-android", version.ref = "pdfbox" }
poi = { group = "org.apache.poi", name = "poi", version.ref = "poi" }
poi-ooxml = { group = "org.apache.poi", name = "poi-ooxml", version.ref = "poi" }
fastexcel-writer = { group = "org.dhatim", name = "fastexcel", version.ref = "fastexcel" }
fastexcel-reader = { group = "org.dhatim", name = "fastexcel-reader", version.ref = "fastexcel" }
opencsv = { group = "com.opencsv", name = "opencsv", version.ref = "opencsv" }

vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }

coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
```

---

## 8. Estructura de carpetas

```
app/src/main/java/com/jeanmarco/finanzas/
├── core/
│   ├── extensions/         (KString.normalizar(), Timestamp.diasDesde(), etc.)
│   ├── result/             (Result<T>, ErrorApp sealed)
│   ├── crypto/             (HashGenerator con SHA-256)
│   └── di/
│       ├── FirebaseModule.kt
│       ├── RepositoryModule.kt
│       ├── ParserModule.kt
│       └── UseCaseModule.kt
│
├── data/
│   ├── firestore/
│   │   ├── dto/
│   │   ├── mappers/
│   │   └── repositories/
│   ├── parsers/
│   │   ├── core/
│   │   │   ├── BankParser.kt
│   │   │   ├── ParserRegistry.kt
│   │   │   ├── ConfianzaDeteccion.kt
│   │   │   └── ResultadoParseo.kt
│   │   ├── banreservas/
│   │   ├── popular/
│   │   ├── qik/
│   │   ├── generico_tarjeta/
│   │   └── utils/
│   │       ├── PdfTextExtractor.kt
│   │       ├── CsvReader.kt
│   │       └── XlsReader.kt
│   ├── remote/bcrd/
│   └── local/datastore/
│
├── domain/
│   ├── models/
│   ├── repositories/
│   └── usecases/
│       ├── auth/
│       ├── carga/
│       ├── transaccion/
│       ├── tarjeta/
│       ├── resumen/
│       ├── categorizacion/
│       └── exportacion/
│
└── presentation/
    ├── theme/
    ├── navigation/
    ├── components/
    └── screens/
        ├── onboarding/
        ├── dashboard/
        ├── upload/
        ├── historial/
        ├── cuenta_detalle/
        ├── tarjetas/
        ├── categorias/
        └── exportar/

app/src/test/
└── resources/fixtures/
    ├── banreservas_v1.pdf
    ├── popular_v1.csv
    ├── qik_v1.pdf
    └── generico_tarjeta_v1.xls
```

---

## 9. Convenciones de código

- **Naming**: PascalCase para clases, camelCase para funciones/variables, SCREAMING_SNAKE_CASE para constantes.
- **Idioma**: español para nombres de dominio (`Transaccion`, `Cuenta`, `Banco`), inglés para términos técnicos universales (`Repository`, `UseCase`, `Mapper`).
- **Suspending functions**: cualquier I/O o cálculo pesado va en `suspend fun`. Nunca bloquear el main thread.
- **Errores**: usar `Result<T, ErrorApp>` (sealed class) en capa de dominio. Las excepciones se atrapan en `data/` y se convierten en `ErrorApp`.
- **Inmutabilidad**: `data class` con `val`, evitar `var`.
- **BigDecimal para dinero**: nunca `Double` ni `Float`. Convertir a/desde Firestore con custom serializer.
- **Tests**: nombrar como `nombreFuncion_estadoInicial_resultadoEsperado()`. JUnit 5.

---

## 10. Checklist final para Claude Code

Antes de marcar el proyecto como completado, verificar:

- [ ] Firebase proyecto creado, Firestore en `nam5`, Auth con Google.
- [ ] Reglas e índices deployados.
- [ ] 3 colecciones públicas sembradas (catálogos + reglas).
- [ ] APK debug compila y se instala.
- [ ] Google Sign-In funciona end-to-end.
- [ ] Los 4 parsers pasan sus tests con fixtures reales.
- [ ] Subir cada uno de los 4 archivos de muestra produce las transacciones correctas.
- [ ] Re-subir el mismo archivo NO duplica.
- [ ] Dashboard muestra cards de balance, gráfico 30d, top categorías.
- [ ] Recategorización manual crea regla personal y aplica retroactivamente.
- [ ] Exportar XLSX produce archivo descargable con múltiples hojas.
- [ ] Historial de cargas muestra todas con drill-down.
- [ ] Tasas BCRD se cachean en `/tasasCambio` y se reutilizan en el día.

Cuando todos los checks pasen, el MVP está listo para uso real.
