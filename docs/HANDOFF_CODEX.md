# Handoff para agente Codex — Issues pendientes

> Documento de traspaso. Asume que ya leíste `CLAUDE.md` (stack, arquitectura, comandos, reglas).
> Aquí solo va lo que **no** está en CLAUDE.md: estado real verificado, commits recientes,
> gotchas de setup local y guía de implementación por issue.
> Branch activo: `sprint-3-parsers-flujo-importacion`. Fecha: 2026-06-09.

---

## 0. ⚠️ Regla de oro: la auditoría está DESACTUALIZADA

`docs/AUDITORIA_SPRINTS.md` (fechada 2026-05-15) describe un estado del repo **anterior a varios refactors**.
Ya se comprobó dos veces que sus "issues" no reflejan la realidad:

- **Issue #1** ("ExitoTarjeta devuelve Error — Qik/Cibao no persisten") era **obsoleto**: la persistencia ya funcionaba; el problema real eran bugs de exactitud del parser.
- **Issue #2** ("9 pantallas ausentes"): 5 de las 7 nombradas **ya existían y estaban ruteadas**.

**Antes de implementar cualquier issue de abajo: verificá el estado actual del código** (grep/lectura),
no confíes en la descripción de la auditoría. Cada issue abajo trae el estado **ya verificado** al 2026-06-09.

---

## 1. Commits recientes verificados

- `66cc244` — issue #1: exactitud de parsers Qik/Cibao, persistencia de tarjetas,
  bimoneda, hash y pruebas de regresión.
- `7cf0233` — issue #2: Ajustes Generales, resúmenes por periodo, notificaciones
  locales y configuración Hilt de WorkManager.

Antes de estos commits se verificaron `testDebugUnitTest`, `assembleDebug` y el
test instrumentado de Qik en un Pixel 6 Pro.

`.idea/deploymentTargetSelector.xml` es ruido del IDE, no lo commitees.

---

## 2. Gotchas de setup local (no están en CLAUDE.md)

- **JDK para Gradle:** no hay `java` en PATH. Usar el JBR de Android Studio:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` antes de `.\gradlew.bat ...`
- **`app/google-services.json`** está gitignored y es **obligatorio para compilar** (el plugin
  `processDebugGoogleServices` corre en todo build, incluso tests JVM). Si falta, pedirlo al usuario.
  SHA-1 debug ya registrado: `7B:D1:FD:74:C5:F1:CC:36:DE:C1:DF:9E:CF:28:40:C5:52:53:56:17`.
- **Fixtures reales** en `docs/03-fixtures/` (gitignored, datos bancarios reales — **nunca commitear ni
  pegar su contenido**). Nombres reales NO normalizados: `Qik.pdf`, `Asociacion Cibao.xls`,
  `Banreservas.pdf`, `Banco Popular Dominicano 026.csv`. Los tests los localizan por patrón vía
  `FixtureLoader` (test/.../parsers/core/FixtureLoader.kt) — busca por substring, no nombre exacto.
- **PDFBox no corre en JVM puro** (PDFBox-Android lanza `ExceptionInInitializerError`). Los parsers PDF
  (Qik, BanReservas) **solo se testean por test instrumentado** (`androidTest`, requiere device). Hay un
  device disponible. Correr instrumentados con:
  `.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=<FQN>"`
  (`--tests` NO funciona en connectedDebugAndroidTest).
- **Fixtures sintéticos CI** (datos falsos, commiteables): `app/src/test/resources/fixtures/` (popular_v1.csv)
  y `app/src/androidTest/assets/fixtures/qik_v1.pdf`. Generar más con Python (reportlab/pypdf instalados).
- Tras tocar modelos/DTOs corré la suite completa: `.\gradlew.bat testDebugUnitTest` (≈205 tests).

---

## 3. Issues pendientes — estado verificado y cómo implementarlos

### Issue #3 — Color `Income` incorrecto  →  COMPLETADO
- El verde legacy incorrecto ya no existe en producción.
- `Income = #16A34A` y `Expense = #DC2626` están protegidos por prueba JVM.
- Todos los gastos/débitos visibles usan `Expense`; los ingresos usan `Income`.
- Los estados positivos no financieros usan `Success`/`Success50`, documentados
  en el design system aunque compartan el mismo valor visual que `Income`.
- Un test Compose instrumentado renderiza muestras sintéticas y valida sus
  colores en dispositivo, sin Firebase ni datos bancarios reales.

### Issue #4 — Derivadas DGII no agrupadas bajo su transacción padre
- **Estado:** no reverificado a fondo. El modelo soporta el vínculo: `Transaccion.transaccionPadreId`,
  `esDerivada`, `derivadasIds`. El parser BanReservas ya detecta DGII como derivada y `ProcesarArchivoUseCase`
  hace una 2ª pasada para vincular `transaccionPadreId`.
- **Qué falta (UI):** en `TransaccionesScreen`, mostrar las derivadas como acordeón bajo la padre con
  `Badge("+N impuesto")` (plan §7.5). Hoy solo se muestra una bandera textual.
- **Cómo:** el `TransaccionesViewModel` debe exponer las derivadas (`derivadasIds`) junto a la padre;
  la fila padre expande/colapsa sus hijas con `AnimatedVisibility`. Verificar primero qué ya hace la pantalla.

### Issue #5 — `tnum` (cifras tabulares) ausente en montos
- **Estado:** ya existe `core/extensions/Format.kt` con `formatMoney`, `formatearMoneda`, `formatearFecha`
  (estas dos últimas las agregó el issue #2 y respetan la config del usuario).
- **Qué falta:** aplicar `fontFeatureSettings = "tnum"` en los `Text` de montos. Crear un `TextStyle`
  `TabularNumber` (o modifier) y aplicarlo en Dashboard, Transacciones, Resumen, Tarjetas, ResumenPeriodo.
- **Cómo:** `style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontFeatureSettings = "tnum"))`.
  Es migración transversal de pantallas; bajo riesgo.

### Issue #6 — Fuente Inter no integrada
- **Estado:** todo usa `FontFamily.Default` (TODO en `ui/theme/Type.kt`).
- **Cómo:** `androidx.compose.ui:ui-text-google-fonts` + `GoogleFont("Inter")` con `FontFamily`, y asignar
  en `Type.kt`. Agregar la dependencia en `gradle/libs.versions.toml` (no hardcodear coordenadas).

### Issue #7 — Tests de parsers (PARCIAL)
- **Verificado:** Cibao y Qik ya tienen cobertura (issue #1: Cibao JVM con fixture real; Qik instrumentado).
- **Qué falta:** solo **Popular** (`PopularCsvParser`). Ya existe `PopularCsvParserTest` y el fixture
  sintético `popular_v1.csv`; revisar si las aserciones son reales o débiles y reforzarlas. CSV corre en JVM.

### Issue #8 — Exportar XLSX + Vista previa  (absorbe el bloque "C" del issue #2)
- **Verificado:** `ExportacionUseCase` tiene `exportarCsv` y `exportarPdf` — **no hay XLSX**. No existe
  `ExportarScreen` ni `VistaPreviaReporteScreen`. `FileProvider` ya está configurado (Manifest + file_provider_paths.xml).
- **Qué hacer:**
  1. Agregar `exportarXlsx(...)` a `ExportacionUseCase` usando **fastexcel** (ya en el stack del plan;
     agregar dep al version catalog si falta). Múltiples hojas (transacciones, resumen por categoría/banco).
  2. `ExportarScreen` (selección de rango/cuentas, botón generar) + `VistaPreviaReporteScreen` (preview) + rutas.
  3. Compartir vía el FileProvider existente (ver `ConfiguracionViewModel.compartirUri`).

### Issue #9 — Componentes del Design System (CASI RESUELTO)
- **Verificado:** `FinanzasSwitch.kt` ✅ existe (en uso), `BankLogo.kt` ✅ existe, `BankRegistry.kt` ✅.
- **Qué falta:** solo **`MerchantLogo`** (DS §5.5). Requiere un `MerchantRegistry` (descripcionNormalizada → abbr+color).
  `TransaccionItem` usa hoy un icono genérico. Crear `MerchantLogo` + registry y usarlo en las filas.

### Issue #10 — Colores hardcodeados en pantallas
- **Estado:** no reverificado de forma exhaustiva. Buscar `Color(0xFF...)` inline en `RevisionScreen`,
  `HistorialScreen`, `DashboardScreen` y reemplazar por tokens de `ui/theme/Color.kt`.
  Caso conocido: Qik usaba `Color(0xFFE6A800)` en vez del token `BancoQik`. **Verificar con grep antes.**

### Issue #11 — Integridad de persistencia de tarjeta (hallazgos del firebase-persistence-tester)
Derivado del issue #1. Tres hallazgos, con archivo:línea:
- **ALTO-1 — merge pisa `alias`:** `ImportacionRepository.persistirCargaTarjeta` usa `SetOptions.merge()`
  con un `TarjetaDto` que siempre lleva `alias` autogenerado → re-importar pisa el alias editado por el usuario.
  **Fix:** leer el doc existente y preservar `alias` (mismo patrón que `construirDtoCuentaConBalanceProtegido`
  para `Cuenta` en ese repo), o `SetOptions.mergeFields(...)` excluyendo `alias`.
- **MEDIO-2 — `EstadoTarjetaSnap` sobreescribible:** se escribe con `set()` sin merge; re-importar el mismo
  corte puede degradarlo. **Fix:** escribir solo si no existe, o versionar.
- **MEDIO-3 — dinero como `Double` en DTOs:** viola "dinero = BigDecimal". **Fix:** migrar campos monetarios
  de los DTOs a `String` (`toPlainString()`/`BigDecimal(string)`) en mappers. Es invasivo (toca round-trips);
  hacerlo con cuidado y correr `MapperRoundTripTest`.

---

## 4. Pendientes diferidos del issue #2 (no son de la lista original, pero quedaron anotados)

- **Alertas de gasto alto (trigger):** el toggle `alertasGastosAltos` se persiste en `NotificacionConfig`,
  pero su disparo es event-driven (al importar transacciones) y **no está cableado**. Implementar: tras una
  importación, si un gasto supera `umbralGastoAlto`, notificar (canal `NotificationHelper.CANAL_ALERTAS`).
- **Idioma / i18n:** diferido. Requiere externalizar strings a `values/strings.xml` (es) + `values-en/` y
  cambio de locale en runtime. El campo `ConfiguracionUsuario.idioma` ya existe pero no se aplica.
- **Backup:** diferido. CLAUDE.md prohíbe Storage/servicios extra; opción recomendada cuando se retome:
  export local JSON vía Storage Access Framework (sin servicios nuevos). Campo `ConfiguracionUsuario.ultimoBackup` ya existe.

---

## 5. Notas de implementación útiles (descubiertas en #1/#2)

- **WorkManager + Hilt ya quedó configurado** (issue #2): `FlowTrackApplication` es `Configuration.Provider`
  con `HiltWorkerFactory` y el Manifest deshabilita el inicializador default. Nuevos `@HiltWorker` funcionan.
  Patrón de notificación: `NotificationHelper` (canales `recordatorios_pago`/`resumenes`/`alertas`) +
  `NotificacionScheduler.aplicar(context, config)`.
- **Bimoneda Cibao:** `MovimientoTarjeta.montoUsd: BigDecimal?` guarda el monto en USD paralelo al `monto` (DOP).
  El hash de movimiento (`HashGenerator.hashMovimientoTarjeta`) incluye `montoUsd` solo cuando es ≠ 0
  (no cambia IDs de movimientos sin USD).
- **Formato según preferencias:** usar `formatearFecha(date, config.formatoFecha)` y
  `formatearMoneda(monto, config.monedaPredeterminada, config.formatoMoneda)` de `Format.kt` en pantallas nuevas.
