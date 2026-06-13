# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Lee este archivo primero.** Es tu punto de entrada al proyecto. Define el contexto, las reglas no negociables, dónde encontrar todo, y qué hacer (y qué no hacer) durante la implementación.

---

## 1. Qué estás construyendo

App Android nativa en Kotlin (Jetpack Compose) para consolidar, visualizar, analizar y exportar transacciones bancarias y de tarjetas de crédito de bancos de República Dominicana. El usuario sube estados de cuenta (PDF, CSV, XLS), la app los parsea localmente, los persiste en Firebase Firestore, y los presenta en dashboards.

**Bancos en MVP:** BanReservas, Banco Popular, Qik, Asociación Cibao y BHD.

**Estado actual del repo:** módulo `:app` con arquitectura Clean Architecture + MVVM ya implementada. Incluye 20+ pantallas en Compose, parsers para BanReservas, Popular, Qik y Cibao, integración con Firebase Auth (Google) y Firestore, y sistema de notificaciones con WorkManager. Se encuentra en una fase avanzada de desarrollo (Sprints 7-8).

---

## 2. Fuente de verdad

**El plan maestro v2 es la única fuente de verdad:**

```
docs/01-analisis/03_PLAN_MAESTRO_V2.md
```

Léelo completo antes de hacer cualquier otra cosa. Reemplaza y consolida los documentos `01_*.md` y `02_*.md` (que se conservan solo como histórico). Si hay contradicciones entre v2 y los históricos, gana v2.

**El design system es la referencia visual:**

```
docs/02-desing-system/04_DESIGN_SYSTEM.md
```

> Nota: la carpeta tiene un typo (`desing` en vez de `design`). Mantenlo así hasta que el usuario decida renombrar — si lo renombra, actualizar esta ruta.

Contiene tokens de color, tipografía, espaciado, componentes Compose ya especificados. Aplícalos sin excepciones — cada Composable nuevo debe pasar el checklist final de ese documento.

**Los fixtures son los archivos reales que el sistema debe procesar:**

```
docs/03-fixtures/
├── banreservas.pdf
├── popular.csv
├── qik.pdf
├── cibao.xls
└── bhd.pdf
```

Úsalos para tests de regresión de los parsers desde Sprint 2.

> **⚠️ Datos sensibles — solo locales.** `docs/03-fixtures/` está en `.gitignore` (línea 16 del root) porque contienen estados de cuenta reales del usuario. **No los commitees nunca**, ni los subas a servicios externos, ni los pegues en mensajes. Si en una sesión futura no los encontrás, no es un error del repo — pedile al usuario que los copie localmente a esa ruta. Para CI, si se necesita, crear fixtures sintéticos aparte (mismo formato, datos falsos) en una carpeta separada.

---

## 3. Reglas no negociables

1. **No inventes**. Si el plan maestro no especifica algo, preguntá antes de asumir. Es un proyecto del usuario, no tuyo.
2. **No saltes hitos**. El plan tiene 9 sprints. Cada uno tiene precondiciones, tareas, y criterio de aceptación. Cumplilos en orden.
3. **Reporta al final de cada sprint**. Checklist obligatorio: ¿compila?, ¿tests pasan?, ¿pantallas coinciden con el design system?, ¿bloqueadores?
4. **Branch por sprint**. Trabajá en `sprint-N-titulo` desde `main`. PR al cerrar el sprint para que el usuario revise antes de mergear.
5. **Si una herramienta del MCP de Firebase no existe, reportalo**. Nunca inventes nombres de comandos. Proponé alternativa (Firebase CLI, consola web, script manual).
6. **Tests con fixtures reales**. Los 4 archivos en `docs/03-fixtures/` son sagrados y **sensibles** (datos bancarios reales, ignorados por git). Cada parser debe pasar tests de regresión contra ellos. Nunca commitearlos, exportarlos, ni incluir su contenido en logs, PRs o mensajes.
7. **Sin Firebase Storage, sin Cloud Functions**. Solo Firestore + Auth. No agregues servicios sin autorización.
8. **Sin cambiar tecnologías sin permiso**. Stack fijo: Kotlin 2.0.x, Compose, Hilt, fastexcel, PdfBox-Android, OpenCSV, Apache POI HSSF. No sustituyas librerías.
9. **Idioma del código**: español para nombres de dominio (`Transaccion`, `Cuenta`, `Banco`), inglés para términos técnicos universales (`Repository`, `UseCase`, `Mapper`). Comentarios en español.
10. **Dinero con `BigDecimal`**. Nunca `Double` ni `Float`. Conversión a/desde Firestore documentada en el plan.

---

## 4. Plan de sprints (resumen)

| Sprint | Foco | Entregable |
|--------|------|------------|
| 1 | Cimientos + design tokens + nav | App con Google Sign-In y dashboard vacío |
| 2 | Modelo + Parser BanReservas | Subir PDF de BanReservas → Firestore |
| 3 | Parsers Popular, Qik, Cibao, BHD + flujo importación | 5 bancos reales con duplicados y selección de banco |
| 4 | Dashboard + Transacciones + Detalle | CRUD completo de transacciones con DGII agrupado |
| 5 | Tarjetas + Resúmenes + Date picker | Análisis completo + conversor DOP/USD |
| 6 | Reglas + Categorización + Export XLSX | Ciclo full cargar→categorizar→exportar |
| 7 | Config + Notificaciones + Ajustes | Sistema de notificaciones WorkManager funcional |
| 8 | Pulido visual + accesibilidad | Pixel-perfect contra design system |
| 9 | Estabilización + release | v1.0 en Play Store track interno |

Detalles completos en sección 6 del plan maestro v2.

---

## 5. Cómo arrancar (paso a paso)

### Sesión 1 — Hito 0 (preparación)

1. Verificar herramientas: `java -version` (17+), `firebase --version`, `node -v` (20+)
2. Verificar acceso al MCP de Firebase listando proyectos existentes
3. Reportar al usuario qué herramientas del MCP están disponibles para confirmar mapeo de llamadas

### Sesión 2 — Hito 1 (Firebase)

Ejecutar las 10 llamadas del MCP listadas en sección 4 del plan v1 (válidas para v2): crear proyecto, habilitar Firestore + Auth, crear app Android, descargar `google-services.json`, deployar reglas e índices, sembrar 3 colecciones públicas.

### Sesión 3 — Sprint 1 (bootstrap Android)

El módulo `:app` ya existe como template. Sprint 1 consiste en: aplicar el design system completo como tema Material 3, montar navegación, agregar Hilt/Compose-Nav/Firebase BoM, integrar Google Sign-In, primer dashboard vacío.

### Sesiones siguientes

Una por sprint. Empezar siempre revisando el último commit del usuario, el estado del branch, y los criterios del sprint actual.

---

## 6. Cuándo pedir ayuda al usuario

Pedí input cuando:
- Una decisión técnica no está cubierta por el plan
- El MCP de Firebase no soporta una operación necesaria
- Un parser falla contra un fixture y el formato parece haber cambiado
- Un design token o componente del design system no cubre un caso de UI
- Encontrás contradicciones entre el plan v2 y los documentos históricos

No pidas ayuda para: decisiones triviales que el plan ya cubre, confirmar cosas obvias (ej: "¿uso Kotlin?"), reformatear código (Kotlin official style).

---

## 7. Estructura del repo (estado actual)

```
finanzas/                              ← rootProject.name = "FlowTrack"
├── CLAUDE.md                          ← este archivo
├── build.gradle.kts                   ← top-level (plugins via version catalog)
├── settings.gradle.kts
├── gradle/libs.versions.toml          ← catálogo de versiones; bumps van aquí
├── gradle.properties
├── gradlew / gradlew.bat
├── local.properties                   ← path al SDK Android, NO commitear cambios
├── app/                               ← módulo único :app (template Compose por ahora)
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/java/com/example/flowtrack/   ← namespace y applicationId
│       │   ├── MainActivity.kt        ← entry point ComponentActivity + setContent
│       │   └── ui/theme/              ← Theme/Color/Type — reemplazar en Sprint 1
│       ├── main/res/                  ← strings, themes, launcher, backup rules
│       ├── test/                      ← JUnit 4 (JVM)
│       └── androidTest/               ← instrumented + Compose UI tests
└── docs/
    ├── 01-analisis/
    │   ├── 01_PLAN_DE_ACCION_CLAUDE_CODE.md   (histórico)
    │   ├── 02_MODELADO_DE_DATOS.md            (técnico)
    │   └── 03_PLAN_MAESTRO_V2.md              ⭐
    ├── 02-desing-system/                       ← typo en el nombre, ver §2
    │   └── 04_DESIGN_SYSTEM.md
    └── 03-fixtures/                             ← gitignored, datos reales sensibles
        ├── banreservas.pdf
        ├── popular.csv
        ├── qik.pdf
        └── cibao.xls
```

---

## 8. Toolchain y comandos

Versiones congeladas en `gradle/libs.versions.toml` — agregar/subir dependencias allí y referenciarlas vía `libs.*` en `app/build.gradle.kts`, nunca hardcodear coordenadas.

- **AGP** 8.13.2, **Kotlin** 2.0.21 (el plan v2 menciona 2.0.20; usar el que está en el catálogo, que es lo que de hecho compila)
- **compileSdk** 36, **targetSdk** 36, **minSdk** 34 — el `minSdk` alto permite usar APIs modernas sin shims de compatibilidad
- **JVM target** 11 (Java + Kotlin)
- **Compose** habilitado por `buildFeatures { compose = true }` + plugin `kotlin-compose`. Las dependencias Compose vienen por BOM (`composeBom = "2024.09.00"`) — no fijar versiones individuales.
- **R8/ProGuard** apagado en release (`isMinifyEnabled = false`); habilitar y poblar `app/proguard-rules.pro` antes del Sprint 9.

Gradle wrapper presente. En Windows usar `gradlew.bat`:

- Build debug APK: `.\gradlew.bat assembleDebug`
- Build completo: `.\gradlew.bat build`
- Instalar en device/emulador: `.\gradlew.bat installDebug`
- Clean: `.\gradlew.bat clean`
- Tests JVM (`app/src/test`): `.\gradlew.bat testDebugUnitTest`
- Tests instrumentados (`app/src/androidTest`, requiere device/emulador): `.\gradlew.bat connectedDebugAndroidTest`
- Un test específico: `.\gradlew.bat testDebugUnitTest --tests "com.example.flowtrack.ExampleUnitTest.addition_isCorrect"`
- Lint: `.\gradlew.bat lint` (reporte en `app/build/reports/lint-results-debug.html`)

UI 100% Jetpack Compose con Material3 — no hay XML layouts ni Fragments. `enableEdgeToEdge()` se llama en `MainActivity.onCreate`, así que las pantallas nuevas deben respetar el `innerPadding` del `Scaffold` en vez de asumir que los insets del sistema están manejados en otro lado.

---

## 9. Comunicación con el usuario

- El usuario habla español; respondé en español.
- Sé conciso. Explica decisiones cuando importen, no para llenar espacio.
- Mostrá progreso real, no resumen genérico. Reportá archivos creados, lo que falta, errores reales.
- Cuando completes un hito, hacé un commit y describí en lenguaje claro qué cambió.

---

## Primera acción al iniciar sesión

1. Leé este archivo y confirmá que lo leíste.
2. Listá `docs/` para confirmar que la documentación está completa (recordá: `02-desing-system` con typo, fixtures sin sufijo `_v1`).
3. Indicá qué sprint te toca según el estado del repo:
   - Si `app/` solo tiene el template (`MainActivity` con `Greeting`): Sprint 1 (bootstrap real).
   - Si hay parsers pero no dashboard: probablemente Sprint 4.
   - En cualquier otro caso: revisar último commit y branch activo para determinar sprint.
4. Confirmá con el usuario el sprint a trabajar antes de empezar.
