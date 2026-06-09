# Auditoría de Sprints — FlowTrack
> Fecha: 2026-05-15 | Auditor: Claude Code | Build: `assembleDebug` ✅

Este documento evalúa cada sprint y módulo contra las rúbricas del Plan Maestro V2 (`docs/01-analisis/03_PLAN_MAESTRO_V2.md`) y el Design System (`docs/02-desing-system/04_DESIGN_SYSTEM.md`). No se realizaron correcciones durante la auditoría.

**Leyenda:** ✅ Cumple | ⚠️ Parcial | ❌ No cumple | 🔧 Fix posible

---

## SPRINT 1 — Cimientos + Design System + Navegación

**Entregable esperado:** App instalable con login, dashboard vacío, design system aplicado, componentes base, navegación completa.

### Módulo: `ui/theme`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Tokens de color DS §1.1 Primary `#2F6FED` | ✅ | `Color.kt` — `Primary`, `Primary600`, `Primary50`, `Primary100` correctos |
| Tokens de color DS §1.2 Neutrales | ✅ | `Ink`, `Muted`, `Line`, `BgScreen`, `BgCard`, `BgDark` todos presentes y correctos |
| Tokens semánticos DS §1.3 (Income/Expense/Warning) | ✅ | `Income`, `Expense`, `Warning` con fills `50` correctos |
| Tokens de categorías DS §1.4 (8 visibles) | ✅ | 8 categorías principales correctas; extras (`CatAtm`, `CatSuscripciones`, etc.) bonus |
| Colores de banco DS §1.5 | ✅ | `BancoBanReservas`, `BancoPopular`, `BancoQik`, `BancoBhd`, `BancoCibao` correctos |
| `lightColorScheme` (no dark) | ✅ | `Theme.kt` usa `lightColorScheme` con `darkTheme = false` como default |
| Escala tipográfica DS §3.2 | ⚠️ | Tamaños y pesos correctos, pero **fuente es `FontFamily.Default`** en vez de Inter. TODO en comentario para Sprint 8 |
| Espaciado DS §4.1 (xxs→xxxxl) | ✅ | `Spacing.kt` — todos los 9 valores correctos según DS |
| Radii DS §4.2 (sm/md/lg/xl/pill) | ✅ | `Radii` como `RoundedCornerShape` directamente; `pill = RoundedCornerShape(50)` |
| `TabularNumber` para montos | ❌ | No existe archivo `Format.kt` con `TabularNumber`. Los montos no usan `fontFeatureSettings = "tnum"` en pantallas |

**Fix `TabularNumber`:** Agregar a `core/extensions/Format.kt` o `ui/theme/Type.kt`:
```kotlin
val TabularNumber = TextStyle(fontFeatureSettings = "tnum, ss01")
// Usar: style = MaterialTheme.typography.bodyLarge.merge(TabularNumber)
```
Aplicar en `DashboardScreen`, `TransaccionesScreen`, `ResumenScreen`, `TarjetasScreen` en todos los `Text` de montos.

---

### Módulo: `presentation/components`

| Componente DS | Estado | Detalle |
|---------------|--------|---------|
| `Pill` | ⚠️ | Existe pero **API distinta al DS** — DS §5.1 especifica `active: Boolean`, `onClick`, `leadingIcon`, `trailingIcon`; implementado como solo `text + color + modifier`. Sin interactividad ni estados activo/inactivo |
| `StatCard` | ⚠️ | Existe pero **API distinta al DS** — DS §5.2 requiere `color` y `background` como parámetros explícitos, implementado con Material3 `surfaceVariant`. Sin soporte `tnum` en valor monetario |
| `FinanzasSwitch` | ❌ | **No existe** el switch custom del DS §5.3 — se usa el `Switch` de Material3 genérico en `ConfiguracionScreen` |
| `CategoryIcon` | ✅ | Existe y mapea correctamente a `CategoryRegistry` |
| `MerchantLogo` | ❌ | **No existe** — el DS §5.5 lo especifica. `TransaccionItem` usa un icono genérico `Icons.Outlined.Category` en lugar de logo del comercio |
| `BankLogo` | ❌ | **No existe** como componente independiente — DS §5.6. `BankRegistry.kt` existe pero no hay composable `BankLogo`. `DashboardScreen` y `HistorialScreen` hacen su propia implementación inline ad-hoc |
| `ActionRow` | ⚠️ | Existe pero **API distinta** — DS §5.7 incluye `danger: Boolean` y `subtitle`. La implementación tiene campos similares pero el `ConfiguracionScreen` usa sus propios `ConfigActionRow`/`ConfigSwitchRow` en lugar del componente centralizado |
| `BottomNav` | ⚠️ | Existe y funcional, pero usa Material3 `NavigationBar` genérico en vez de la implementación custom del DS §5.8. El DS especifica `drawBehind` con línea superior en vez de la elevación automática. Label dice "Movimientos" en vez de "Transacciones"; icono usa `Icons.Outlined.List` (deprecated) en vez de `Icons.Outlined.Receipt` |
| `DonutChart` | ✅ | Existe e implementado en Canvas |
| `ShimmerEffect` | ✅ | Existe con variantes `DashboardStatShimmerCard`, `TransactionShimmerItem`, `CreditCardShimmerItem`, `ShimmerHistoryItem`, `ResumenShimmerItem` |
| `EmptyState` | ✅ | Existe con `icon`, `title`, `description` |
| `ErrorState` | ✅ | Existe con `mensaje`, `onRetry` |

**Fix `FinanzasSwitch`:**
```kotlin
// Crear presentation/components/FinanzasSwitch.kt según DS §5.3
// Reemplazar Switch nativo en ConfiguracionScreen
```

**Fix `BankLogo`:**
```kotlin
// Crear presentation/components/BankLogo.kt usando BankRegistry
// Reemplazar todas las implementaciones inline en HistorialScreen, DashboardScreen, SeleccionBancoScreen
```

**Fix `MerchantLogo`:** Requiere definir un `MerchantRegistry` con pares (descripcionNormalizada → abbr + color). Complejidad media.

**Fix `BottomNav`:** Cambiar `Icons.Outlined.List` → `Icons.Outlined.Receipt` (o `.ReceiptLong`). Evaluar si adoptar implementación custom del DS en Sprint 8.

---

### Módulo: `presentation/navigation`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Todas las rutas del Plan §2 declaradas | ⚠️ | 13 rutas declaradas de las ~20 del plan. Faltan: `Detalle Transacción`, `Detalle Tarjeta`, `Exportar`, `Vista Previa Reporte`, `Notificaciones`, `Perfil`, `Bancos y Cuentas`, `Resúmenes diario/semanal/mensual`, `Ajustes Generales` |
| Transiciones `fadeIn(tween(240))` | ✅ | `enterTransition = { fadeIn(fadeSpec) }` con `tween(240)` |
| BottomNav visible en rutas correctas | ✅ | `bottomNavRoutes` filtra Dashboard, Transacciones, Resumen, Tarjetas, Configuracion |
| `startDestination = Login` | ✅ | Login es la pantalla inicial |
| URI encoding para SeleccionBanco | ✅ | `URLEncoder`/`URLDecoder` aplicados correctamente |

**Fix rutas faltantes:** Agregar a `Screen` sealed class y registrar en `NavGraph`:
```kotlin
object DetalleTransaccion : Screen("detalle_transaccion/{txId}")
object DetalleTarjeta     : Screen("detalle_tarjeta/{tarjetaId}")
object Exportar           : Screen("exportar")
object Notificaciones     : Screen("notificaciones")
object Perfil             : Screen("perfil")
object BancosYCuentas     : Screen("bancos_cuentas")
object Ajustes            : Screen("ajustes")
```

---

### Sprint 1 — Resumen

| Criterio | Estado |
|----------|--------|
| App compila | ✅ |
| Login funcional (Google Sign-In) | ✅ |
| Dashboard vacío visible | ✅ |
| Design tokens aplicados | ⚠️ (fuente Inter pendiente Sprint 8; `tnum` faltante) |
| Componentes base | ⚠️ (Switch custom, BankLogo, MerchantLogo faltantes) |
| Navegación completa | ⚠️ (7 rutas del plan aún sin declarar) |

---

## SPRINT 2 — Modelo de Dominio + Parser BanReservas

**Entregable esperado:** Modelos completos con cambios v2, repositorios, hash determinístico, BankParser interface, ParserRegistry con confianza, parser BanReservas, detección DGII.

### Módulo: `domain/model`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| `Transaccion` con `derivadasIds: List<String>` (cambio v2) | ✅ | Campo presente en `Transaccion.kt` |
| `Transaccion.transaccionPadreId` para DGII | ✅ | Presente |
| `Tarjeta.tasaInteresOrigen: OrigenTasa` (cambio v2) | ✅ | Presente con `enum OrigenTasa { AUTO_EXTRAIDA, MANUAL }` |
| `Tarjeta.estado: EstadoTarjeta` (cambio v2) | ✅ | Presente con `EstadoTarjeta.ACTIVO` |
| `Cuenta.mostrarEnDashboard: Boolean` (cambio v2) | ✅ | Presente |
| `Cuenta.moneda: Moneda` | ✅ | Presente |
| `ConfiguracionUsuario` completo | ✅ | Existe en `Configuracion.kt` — todos los campos del plan |
| `NotificacionConfig` completo | ✅ | Existe — todos los campos del plan |
| `ReglaSugerida` completo | ✅ | Presente en `ReglaCategoria.kt` con `aceptada: Boolean?` |
| `MovimientoTarjeta` | ✅ | Existe en `Tarjeta.kt` |
| `EstadoTarjetaSnap` | ✅ | Existe en `Tarjeta.kt` |
| Enums: `TipoTransaccion`, `Moneda`, `TipoCuenta`, `TipoDocumento`, `EstadoCarga`, `TipoMatch`, `OrigenTasa`, `EstadoTarjeta`, `TipoMovimientoTarjeta` | ✅ | Todos en `Enums.kt` |

---

### Módulo: `data/parsers/core`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Interface `BankParser` con `codigoBanco`, `tipoDocumento`, `version`, `formatosArchivo`, `puedeManejar`, `parsear` | ✅ | `BankParser.kt` — todos los campos del plan §3.1 |
| `ConfianzaDeteccion` con `confianza: Float` y `razon: String` | ✅ | Existe en `TiposParser.kt` |
| `ArchivoEntrada` con `bytes`, `extension`, `mimeType`, `tamanioBytes` | ✅ | Existe |
| `ParserRegistry` con multibinding Hilt `@IntoSet` | ✅ | Correcto — `Set<@JvmSuppressWildcards BankParser>` |
| Umbral alta confianza ≥ 0.8 | ✅ | `UMBRAL_ALTA_CONFIANZA = 0.8f` |
| Umbral media confianza ≥ 0.4 | ✅ | `UMBRAL_MEDIA_CONFIANZA = 0.4f` |
| `ResultadoDeteccion` con 4 estados (Alta/Media/Baja/NoDetectado) | ✅ | Todos presentes |
| `parserPorCodigo(String): BankParser?` | ✅ | Agregado en esta sesión |
| Filtro por extensión antes de evaluar contenido | ✅ | `archivo.extension in it.formatosArchivo` |

---

### Módulo: `data/parsers/banreservas`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Parser implementado para PDF | ✅ | `BanReservasPdfParser.kt` existe |
| Detección de DGII como derivada | ✅ | Detecta "COBRO IMP" + "DGII" |
| `transaccionPadreId` asignado en ProcesarArchivoUseCase | ✅ | Segunda pasada en `procesarConParser()` |
| Test de regresión con fixture real | ⚠️ | `BanReservasPdfParserTest.kt` existe pero **no carga el fixture real** `docs/03-fixtures/banreservas.pdf` — el test parece estar vacío o con datos sintéticos |
| Hash determinístico SHA-256 | ✅ | `HashGenerator.kt` existe |

**Fix test:** El test debe cargar el fixture local para regresión real (solo en local, nunca en CI).

---

### Módulo: `domain/usecases/carga`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| `ProcesarArchivoUseCase.ejecutar()` con ramificación alta/media/baja | ✅ | Implementado correctamente |
| `ejecutarConBancoConfirmado()` usa `parserPorCodigo` | ✅ | Correcto tras fix de esta sesión |
| Validación de tamaño ≤ 10 MB | ✅ | `LIMITE_BYTES = 10 * 1024 * 1024L` |
| Lectura de URI con `OpenableColumns` | ✅ | Usa `contentResolver.query` con `DISPLAY_NAME` |
| Persistencia via `ImportacionRepository` | ✅ | `importacionRepository.persistirCarga(...)` |
| `ResultadoParseo.ExitoTarjeta` manejado | ❌ | Devuelve `Error("Parseo de tarjeta pendiente de implementación completa.")` — Sprint 3 pendiente |
| Ruta `bancoCodigo != null` en `ejecutar()` (líneas 113-120) | ❌ | **Bug activo** — el código tiene un bloque muerto que siempre retorna Error cuando `bancoCodigo` se pasa a `ejecutar()`. Solo `ejecutarConBancoConfirmado()` funciona para banco confirmado. Este bloque es letra muerta pero confuso |

**Fix bug bloque muerto:** Eliminar las líneas 112-120 de `ProcesarArchivoUseCase.kt`:
```kotlin
// Eliminar este bloque — bancoCodigo explícito en ejecutar() nunca se usa
// desde la UI (UploadViewModel solo llama ejecutar(uri, uid) sin bancoCodigo)
// La ruta correcta es ejecutarConBancoConfirmado()
```

---

### Sprint 2 — Resumen

| Criterio | Estado |
|----------|--------|
| Modelos dominio completos (cambios v2) | ✅ |
| BankParser + ParserRegistry | ✅ |
| Parser BanReservas | ✅ |
| DGII como derivada | ✅ |
| Hash determinístico | ✅ |
| Repositorios | ✅ |
| Tests de regresión con fixture | ⚠️ (test existe pero sin fixture real) |
| Bug bloque muerto en ejecutar() | ❌ |

---

## SPRINT 3 — Parsers Popular, Qik, Cibao + Flujo Importación

**Entregable esperado:** 4 parsers funcionales, pantalla Revisión, Duplicados, Historial inmutable.

### Módulo: `data/parsers` — Popular, Qik, Cibao

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| `PopularCsvParser` implementado | ✅ | Existe |
| `QikPdfParser` con auto-extracción de tarjeta | ✅ | Existe |
| `CibaoXlsParser` (antes `GenericoTarjetaXlsParser`) | ✅ | Existe |
| Tests de regresión Popular, Qik, Cibao | ❌ | Solo existe test para BanReservas — **los otros 3 parsers no tienen tests** |
| `ResultadoParseo.ExitoTarjeta` manejado end-to-end | ❌ | `ProcesarArchivoUseCase` devuelve Error para tarjetas — Qik y Cibao son tarjetas |

**Fix tests faltantes:** Crear:
- `app/src/test/.../parsers/popular/PopularCsvParserTest.kt`
- `app/src/test/.../parsers/qik/QikPdfParserTest.kt`
- `app/src/test/.../parsers/cibao/CibaoXlsParserTest.kt`

**Fix `ExitoTarjeta`:** Implementar la rama en `procesarConParser()` para persistir `MovimientoTarjeta` + `EstadoTarjetaSnap` + `Tarjeta` vía sus respectivos repositorios.

---

### Módulo: `presentation/screens/revision`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Pantalla Revisión existe | ✅ | `RevisionScreen.kt` + `RevisionViewModel.kt` |
| Muestra resumen de la carga (banco, archivo, período, totales) | ✅ | `ResumenCargaCard` con banco, nombre archivo, transacciones, débitos, créditos |
| Alerta de duplicados con enlace a Duplicados | ✅ | `DuplicadosAlertCard` navega a `Screen.Duplicados` |
| Advertencias del parser | ✅ | `AdvertenciasCard` |
| Lista de transacciones parseadas | ✅ | `TransaccionRevisionRow` por cada `TransaccionNormalizada` |
| Botón "Confirmar" con count | ✅ | `Confirmar (N)` |
| Navegación a Historial tras confirmar | ✅ | `LaunchedEffect` navega a `Screen.Historial` |
| `containerColor = BgScreen` | ⚠️ | Usa literal `Color(0xFFF4F6FA)` en vez del token `BgScreen` |
| Colores hardcodeados en lugar de tokens | ⚠️ | Múltiples `Color(0xFF...)` inline — debería usar tokens de `Color.kt` |

**Fix colores hardcodeados:**
```kotlin
// Reemplazar en RevisionScreen.kt:
Color(0xFFF4F6FA) → BgScreen
Color(0xFF2F6FED) → Primary
Color(0xFF64748B) → Muted
Color(0xFF94A3B8) → Muted2
Color(0xFF0F172A) → Ink
Color(0xFF16A34A) → Income
Color(0xFFDC2626) → Expense
Color(0xFFF59E0B) → Warning
```

---

### Módulo: `presentation/screens/duplicados`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Pantalla Duplicados existe | ✅ | `DuplicadosScreen.kt` + `DuplicadosViewModel.kt` |
| Muestra lista de duplicados detectados | ⚠️ | Pantalla existe pero requiere verificar si la lógica de detección de duplicados está conectada al flujo de importación |

---

### Módulo: `presentation/screens/historial`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Historial inmutable existe | ✅ | `HistorialScreen.kt` + `HistorialViewModel.kt` |
| Lista de cargas con estado (Exitoso/Parcial/Fallido) | ✅ | `CargaCard` con `EstadoBadge` |
| Detalle expandible por carga | ✅ | Acordeón con insertadas, duplicadas, parser version, confianza |
| Advertencias del parser en expandible | ✅ | Muestra lista de advertencias |
| Eliminar carga con confirmación | ✅ | Dialog de confirmación, llama a `viewModel.eliminar(cargaId)` |
| FAB para nueva importación | ✅ | Navega a `Screen.Upload` |
| Color de banco inline (no usa `BankRegistry`) | ⚠️ | `bancoCcolor()` función privada duplica `BankRegistry`. Además Qik usa `Color(0xFFE6A800)` distinto al DS `Color(0xFFFFD200)` |
| Colores del DS | ⚠️ | Usa literales en lugar de tokens `BgScreen`, `Primary`, etc. |

**Fix color Qik en `HistorialScreen.kt`:**
```kotlin
// Línea ~259: "QIK" -> Color(0xFFE6A800)  ← INCORRECTO
// Debería ser:
"QIK" -> BancoQik  // = Color(0xFFFFD200)
```

---

### Sprint 3 — Resumen

| Criterio | Estado |
|----------|--------|
| 4 parsers implementados | ✅ |
| Flujo revisión funcional | ✅ |
| Historial de importaciones | ✅ |
| Duplicados detectados y mostrados | ⚠️ (pantalla existe, integración pendiente de verificar) |
| `ExitoTarjeta` end-to-end | ❌ |
| Tests de parsers Popular/Qik/Cibao | ❌ |
| Colores hardcodeados en pantallas | ⚠️ |

---

## SPRINT 4 — Dashboard + Transacciones

**Entregable esperado:** Dashboard con StatCards, comparativa mensual, donut, lista cuentas. Transacciones con filtros, búsqueda, agrupamiento diario, detalle, cambio categoría, eliminar, aprendizaje de reglas.

### Módulo: `presentation/screens/dashboard`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| `StatCard` "Gastos actuales" | ✅ | Presente |
| Comparativa `% vs mes anterior` | ✅ | `estado.comparativa.porcentaje` con `+/-` |
| Color comparativa (rojo si incremento, verde si decremento) | ⚠️ | Usa `Color.Red` y `Color(0xFF00C853)` hardcodeados — debería usar `Expense` e `Income` |
| DonutChart con categorías | ✅ | Implementado, top 5 categorías |
| Colores del donut | ⚠️ | Lista hardcodeada `listOf(Color(0xFF2F6FED), Color(0xFFE91E63), ...)` — debería usar tokens `Cat*` del DS §1.4 |
| Lista de cuentas | ✅ | `CuentaItem` en `LazyColumn` |
| `CuentaItem` usa `BankLogo` | ❌ | Usa `Icons.Outlined.AccountBalance` genérico — no diferencia por banco |
| Empty state en cuentas vacías | ✅ | `EmptyState` con `AccountBalanceWallet` |
| FAB → Upload | ✅ | `FloatingActionButton` navega a `Screen.Upload` |
| Background `BgScreen` | ❌ | `DashboardScreen` no establece `containerColor = BgScreen` en `Scaffold` — hereda de tema |
| Padding lateral `16.dp` | ⚠️ | Usa `Spacing.md` (12.dp) en vez de `Spacing.xl` (16.dp) para padding horizontal |
| `CalcularComparativaMensualUseCase` del plan §7.2 | ⚠️ | Existe `CalcularComparativaMensualUseCase.kt` en `domain/usecase` pero se ubica en la carpeta equivocada (`usecase` vs `usecases/carga`) — inconsistencia de paquetes |

**Fix colores hardcodeados en Dashboard:**
```kotlin
// Reemplazar en DashboardScreen.kt:
Color.Red          → Expense
Color(0xFF00C853)  → Income
// Colores del donut: usar CatCompras, CatServicios, CatTransporte, CatAlimentacion, CatOtros
```

---

### Módulo: `presentation/screens/transacciones`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Filtros Todas/Ingresos/Gastos | ✅ | `FilterChip` con `TipoTransaccionFiltro` |
| Búsqueda por texto | ✅ | `OutlinedTextField` con `searchQuery` |
| Agrupamiento por día con sticky headers | ✅ | `stickyHeader` con `formatDateRelative` |
| `TransaccionItem` expandible | ✅ | `AnimatedVisibility` al tocar |
| Detalle expandido: banco, referencia, flag DGII | ✅ | Muestra banco, referencia, bandera emoji si `esDerivada` |
| Derivadas DGII expandibles bajo su padre (DS §7.5) | ❌ | **No implementado** — el plan especifica un accordeon de derivadas con `Badge("+N impuesto")`. Actualmente solo muestra flag textual sin expandir las derivadas hijas |
| Eliminar con diálogo | ✅ | `AlertDialog` con confirmación |
| Diálogo de eliminación con dos opciones (solo esta / toda la carga) | ❌ | El plan §10 especifica dos opciones de eliminación. Actualmente solo elimina la transacción individual |
| Bottom sheet categorías | ✅ | `ModalBottomSheet` con lista de categorías |
| Checkbox "aplicar a todas" en categorización | ✅ | `Checkbox` presente |
| Aprendizaje de reglas al recategorizar | ⚠️ | `viewModel.recategorizar()` llama con `aplicarATodas` pero no está claro si persiste en `ReglaCategoriaRepository` |
| `CategoryIcon` en filas (no icono genérico) | ❌ | Usa `Icons.Outlined.Category` genérico — debería usar el composable `CategoryIcon` con color según categoría |
| Color ingreso `Income`, gasto `Expense` | ⚠️ | Ingreso usa `Color(0xFF00C853)` en vez de `Income = Color(0xFF16A34A)` |
| `tnum` en montos | ❌ | No aplica `fontFeatureSettings = "tnum"` |
| `Divider` deprecado | ⚠️ | Usa `Divider()` en lugar de `HorizontalDivider()` |

**Fix derivadas DGII:**
```kotlin
// Implementar según plan §7.5: mostrar Badge "+N impuesto" en padre
// Al tocar expandir con AnimatedVisibility las transacciones derivadas
// Requiere cargar derivadasIds desde el ViewModel
```

**Fix diálogo eliminar — dos opciones:**
```kotlin
AlertDialog(
    text = { Text("¿Eliminar solo esta transacción o todas las de esta carga?") },
    confirmButton = { TextButton { Text("Solo esta") } },
    // + botón "Toda la carga"
)
```

---

### Sprint 4 — Resumen

| Criterio | Estado |
|----------|--------|
| Dashboard con comparativa mensual | ✅ |
| DonutChart categorías | ✅ |
| Lista cuentas | ✅ |
| Transacciones con filtros y búsqueda | ✅ |
| Agrupamiento por día | ✅ |
| Eliminar transacción | ⚠️ (falta opción "toda la carga") |
| Cambio de categoría con aprendizaje | ⚠️ (UI sí, persistencia incierta) |
| Derivadas DGII agrupadas visualmente | ❌ |
| `CategoryIcon` en filas | ❌ |
| Colores DS en Dashboard | ⚠️ |

---

## SPRINT 5 — Tarjetas + Resúmenes + Date Picker + Conversor

**Entregable esperado:** Tarjetas con carrusel/dots, detalle tarjeta, Resumen tabs Banco/Categoría, date range picker, resúmenes diario/semanal/mensual, conversor DOP/USD con BCRD.

### Módulo: `presentation/screens/tarjetas`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Carrusel swipeable (`HorizontalPager`) | ✅ | Implementado con `foundation.pager` |
| Dots animados | ✅ | Dots con animación de ancho y color |
| Historial de estados de tarjeta | ✅ | `EstadoTarjetaItem` con lista |
| Tarjeta visual oscura con número enmascarado | ✅ | `TarjetaCardView` con fondo `Color(0xFF1E293B)` |
| Tarjeta muestra: banco, límite, corte/pago | ✅ | Presente |
| `TarjetaCardView` usa color de banco del DS | ❌ | Fondo fijo `Color(0xFF1E293B)` — el plan §2 dice "tarjetas oscuras" con `BgDark` (`Color(0xFF0B1220)`). Además el DS §4.3 especifica `shadow(elevation = 12.dp)` para tarjetas |
| Detalle de tarjeta (pantalla separada) | ❌ | No existe `DetalleTarjetaScreen` — el plan §2 especifica pantalla #8 separada |
| Empty state sin tarjetas | ✅ | `EmptyState` con `CreditCardOff` |

**Fix color `TarjetaCardView`:**
```kotlin
containerColor = BgDark  // Color(0xFF0B1220) en vez de Color(0xFF1E293B)
// + Modifier.shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp))
```

**Fix pantalla Detalle Tarjeta:** Crear `DetalleTarjetaScreen` + `DetalleTarjetaViewModel` + ruta `Screen.DetalleTarjeta`.

---

### Módulo: `presentation/screens/resumen`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Tabs Banco/Categoría | ✅ | `TabRow` con Categorías y Bancos |
| Totales gastos e ingresos | ✅ | Ambos en header |
| `LinearProgressIndicator` por ítem | ✅ | Barras de progreso en `ResumenItem` |
| Date range picker | ✅ | `DateRangePicker` de Material3 en `DatePickerDialog` |
| Rangos predefinidos "Este mes" / "Mes pasado" | ✅ | `FilterChip` con `setRangoPredefinido` |
| Resúmenes diario/semanal/mensual in-app (pantallas dedicadas) | ❌ | No existen. El plan §2 especifica pantallas 22, 23, 24 separadas. Solo hay un `ResumenScreen` genérico |
| Color de ingresos `Income` | ⚠️ | Usa `Color(0xFF00C853)` en vez de `Income = Color(0xFF16A34A)` |
| `LinearProgressIndicator` deprecado | ⚠️ | Usa overload con `Float` en vez de lambda — warning en build |
| Drill-down `ResumenBank` (pantalla separada) | ❌ | No existe `ResumenBankScreen` — plan §2 pantalla #6 |

**Fix color ingresos:**
```kotlin
Color(0xFF00C853) → Income  // en ResumenScreen y DashboardScreen
```

**Fix pantallas resumen adicionales:**
```
Crear ResumenDiarioScreen, ResumenSemanalScreen, ResumenMensualScreen
+ rutas correspondientes en NavGraph
```

---

### Módulo: `presentation/screens/conversor`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Conversor DOP/USD | ✅ | Implementado con `ConversorViewModel` |
| Muestra tasa del día (compra/venta) | ✅ | `Card` con tasa de la fuente |
| Fuente de tasa (BCRD/fallback) | ✅ | Muestra `state.tasa!!.fuente` |
| Invertir dirección | ✅ | `SwapVert` icon button |
| Historial de tasas (pantalla dedicada) | ❌ | El plan §2 especifica pantalla #15 "Tasas de cambio" separada con historial. `ConversorScreen` solo muestra la tasa actual |
| API BCRD + fallback TasaReal | ⚠️ | `ConversorViewModel` existe pero no se puede verificar la implementación de la API sin leer el ViewModel |

---

### Sprint 5 — Resumen

| Criterio | Estado |
|----------|--------|
| Tarjetas con carrusel | ✅ |
| Dots animados | ✅ |
| Resumen con tabs | ✅ |
| Date range picker | ✅ |
| Conversor DOP/USD | ✅ |
| Resúmenes diario/semanal/mensual | ❌ |
| Detalle de tarjeta (pantalla) | ❌ |
| Drill-down Resumen-Bank | ❌ |
| Historial de tasas de cambio | ❌ |
| Color `Income` correcto | ⚠️ |

---

## SPRINT 6 — Reglas + Categorización + Exportación

**Entregable esperado:** Catálogo 17 categorías, motor cascada, pantalla Reglas (Mis reglas/Sugeridas), algoritmo clustering, Exportar XLSX, vista previa reporte, compartir FileProvider.

### Módulo: `presentation/screens/categorias`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Pantalla Categorías existe | ✅ | `CategoriasScreen.kt` + `CategoriasViewModel.kt` |
| 17 categorías en catálogo | ⚠️ | `CategoryRegistry.kt` tiene 13 categorías — faltan `suscripciones`, `pago_tarjeta`, `deposito`, `cashback` |
| Pantalla de Reglas (tabs Mis reglas / Sugeridas) | ❌ | No existe `ReglasScreen`. `SugerenciasScreen` existe pero solo para sugerencias, no tiene tab "Mis reglas" |
| Motor de categorización en cascada (personal → global → sin_categorizar) | ⚠️ | Existe `ReglaCategoriaRepository` y `ReglaSugeridaRepository` pero no hay un `CategorizarTransaccionUseCase` centralizado |
| Algoritmo de reglas sugeridas (clustering §7.3) | ✅ | `TfIdfKMeansClusterer.kt` + `ClusteringWorker.kt` + `AnalizarTransaccionesUseCase.kt` |
| Worker cada 7 días o tras 50+ transacciones | ⚠️ | `ClusteringWorker` existe pero no se pudo verificar si está registrado como `PeriodicWorkRequest` |
| Exportar XLSX | ❌ | No existe `ExportarScreen`. `ExportacionUseCase.kt` existe pero exporta CSV, no XLSX (plan especifica fastexcel) |
| Vista previa del reporte | ❌ | No existe `VistasPreviaReporteScreen` |
| Compartir via FileProvider | ❌ | No verificado — depende de `ExportacionUseCase` |

**Fix categorías faltantes en CategoryRegistry:**
```kotlin
// Agregar en CategoryRegistry.kt:
"suscripciones"   to CategoriaUI(CatSuscripciones, "Suscripciones", ...)
"pago_tarjeta"    to CategoriaUI(CatPagos,         "Pago Tarjeta",  ...)
"deposito"        to CategoriaUI(CatIngresos,       "Depósito",      ...)
"cashback"        to CategoriaUI(CatCashback,       "Cashback",      ...)
```

**Fix exportación XLSX:**
```kotlin
// ExportacionUseCase debe usar fastexcel en lugar de CSV
// Requiere implementar ReporteSheet con múltiples hojas
// Dependencia ya en libs.versions.toml: fastexcel
```

---

### Sprint 6 — Resumen

| Criterio | Estado |
|----------|--------|
| Motor clustering sugerencias | ✅ |
| Worker de análisis | ✅ (verificar scheduling) |
| Pantalla reglas (Mis reglas / Sugeridas) | ❌ |
| Motor categorización en cascada | ⚠️ |
| Exportar XLSX con fastexcel | ❌ (solo CSV) |
| Vista previa reporte | ❌ |
| 17 categorías completas | ⚠️ (13/17) |

---

## SPRINT 7 — Configuración + Notificaciones + Ajustes

**Entregable esperado:** Config raíz completa, Perfil, Bancos y Cuentas CRUD, Notificaciones con toggles, WorkManager, Tasas de cambio, Ajustes generales (idioma/formato/backup/borrar).

### Módulo: `presentation/screens/configuracion`

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Pantalla Configuración raíz | ✅ | Existe con Modo Oscuro, Moneda, Exportar CSV, Categorías |
| Modo oscuro toggle | ✅ | Funcional — conectado a `MainActivity` via `ConfiguracionRepository` |
| Moneda base | ✅ | Dialog de selección |
| Exportar CSV | ✅ | Llama a `ExportacionUseCase` |
| Navegación a Categorías | ✅ | Presente |
| Pantalla Perfil | ❌ | No existe. El plan §2 pantalla #21 especifica datos Google, foto de perfil |
| Pantalla Bancos y Cuentas CRUD | ❌ | No existe. Plan §2 pantalla #20 |
| Pantalla Notificaciones con toggles | ❌ | No existe. Plan §7 |
| WorkManager — notificaciones de pago (§7.4) | ❌ | `ClusteringWorker` existe pero no hay `NotificacionPagoWorker` |
| Permission `POST_NOTIFICATIONS` Android 13+ | ❌ | No declarado |
| Pantalla Ajustes Generales (idioma/formato/backup/borrar) | ❌ | No existe. Plan §2 pantalla #25 |
| Backup JSON a Google Drive | ❌ | No implementado |
| Borrar datos con confirmación + delete recursivo | ❌ | No implementado |
| `FinanzasSwitch` custom en toggles | ❌ | Usa `Switch` nativo de Material3 |
| `ActionRow` custom del DS en filas de configuración | ❌ | Usa `ConfigActionRow`/`ConfigSwitchRow` propios |

---

### Sprint 7 — Resumen

| Criterio | Estado |
|----------|--------|
| Configuración raíz | ⚠️ (funcional pero incompleta) |
| Perfil | ❌ |
| Bancos y Cuentas CRUD | ❌ |
| Notificaciones con WorkManager | ❌ |
| Ajustes Generales | ❌ |
| Backup / Borrar datos | ❌ |

---

## SPRINT 8 — Pulido Visual + Accesibilidad

**Entregable esperado:** Pixel-perfect, animaciones completas, empty/loading/error states, press states, accesibilidad, modo offline.

### Módulo: Pulido Visual (global)

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Fuente Inter via Google Fonts | ❌ | Todo usa `FontFamily.Default`. TODO en `Type.kt` |
| Números con `tnum` | ❌ | Ninguna pantalla aplica `fontFeatureSettings = "tnum"` en montos |
| Fade-in 240ms entre pantallas | ✅ | `NavGraph` tiene `fadeIn(tween(240))` global |
| Animación switch 200ms cubic-bezier | ❌ | Switch custom no existe |
| `scale(0.98)` en press states | ❌ | No implementado en ningún componente |
| Empty states en todas las listas | ✅ | `EmptyState` presente en Dashboard, Transacciones, Tarjetas, Historial |
| Loading skeletons | ✅ | `ShimmerEffect` con variantes por pantalla |
| Error states con retry | ✅ | `ErrorState` presente |
| Modo offline (Firestore cache) | ❌ | No configurado `FirebaseFirestoreSettings` con `setCacheSizeBytes` |
| Accesibilidad: `contentDescription` en iconos | ⚠️ | La mayoría usa `null` como `contentDescription` en iconos decorativos (correcto), pero botones de acción tampoco tienen labels accesibles |
| Sombras según DS §4.3 | ❌ | Ninguna pantalla aplica las sombras del DS (`shadow(1.dp)` en cards, `shadow(12.dp)` en tarjetas crédito) |
| Bordes `Line2 = EEF1F5` en cards | ❌ | Cards usan `CardDefaults.cardColors` sin borde explícito. El DS especifica borde `Color(0xFFEEF1F5)` |

---

### Sprint 8 — Resumen

| Criterio | Estado |
|----------|--------|
| Fuente Inter | ❌ |
| tnum en montos | ❌ |
| Press states | ❌ |
| Switch custom con animación | ❌ |
| Sombras DS | ❌ |
| Bordes cards | ❌ |
| Modo offline Firestore | ❌ |
| Empty/Loading/Error states | ✅ |
| Transiciones entre pantallas | ✅ |

---

## SPRINT 9 — Estabilización + Lanzamiento

**Entregable esperado:** Tests unitarios, R8, Crashlytics, Analytics, documentación.

### Módulo: Tests

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| Test BanReservas | ⚠️ | Existe `BanReservasPdfParserTest.kt` pero sin fixture real |
| Tests Popular, Qik, Cibao | ❌ | No existen |
| Tests UseCases (categorización, conversión, agrupamiento DGII) | ❌ | No existen |
| Tests UI de flujos principales | ❌ | Solo `ExampleUnitTest.kt` template |

### Módulo: Release

| Rúbrica | Estado | Detalle |
|---------|--------|---------|
| R8/ProGuard | ❌ | `isMinifyEnabled = false` en build.gradle. Pendiente Sprint 9 según CLAUDE.md |
| Crashlytics | ❌ | Comentado en `build.gradle.kts` — deshabilitado en Sprint 1 |
| Analytics | ❌ | No integrado |
| APK < 50 MB | ⚠️ | No medido |

---

## RESUMEN EJECUTIVO

### Pantallas del Plan vs Implementadas

El plan v2 §2 especifica **20+ pantallas**. Estado actual:

| # | Pantalla del Plan | Estado |
|---|-------------------|--------|
| 1 | Login / Splash | ✅ |
| 2 | Dashboard | ✅ |
| 3 | Transacciones (lista) | ✅ |
| 4 | **Detalle de transacción** | ❌ |
| 5 | Resumen (tabs) | ✅ |
| 6 | **Resumen-Bank drill-down** | ❌ |
| 7 | Tarjetas (carrusel) | ✅ |
| 8 | **Detalle de tarjeta** | ❌ |
| 9 | Importar estado | ✅ |
| 10 | Revisión de importación | ✅ |
| 11 | Historial de importaciones | ✅ |
| 12 | **Reglas de categorización** | ❌ |
| 13 | Configuración (raíz) | ✅ |
| 14 | **Notificaciones** | ❌ |
| 15 | **Tasas de cambio** | ⚠️ (solo conversor, no historial) |
| 16 | **Exportar a Excel** | ❌ |
| 17 | **Vista previa del reporte** | ❌ |
| 18 | Selección de banco (confianza) | ✅ |
| 19 | **Detalle de duplicados** | ⚠️ (pantalla existe, integración incierta) |
| 20 | **Bancos y cuentas CRUD** | ❌ |
| 21 | **Perfil** | ❌ |
| 22-24 | **Resúmenes diario/semanal/mensual** | ❌ |
| 25 | **Ajustes Generales** | ❌ |

**Implementadas: 11/20+ (55%)** — Los sprints 1-5 están mayormente completos en funcionalidad core. Los sprints 6, 7, 8, 9 tienen implementación mínima o nula.

---

### Top 10 Issues Prioritarios

| # | Severidad | Issue | Sprint | Fix |
|---|-----------|-------|--------|-----|
| 1 | 🔴 Alta | `ExitoTarjeta` devuelve Error — Qik y Cibao no persisten | S3 | Implementar rama en `procesarConParser` |
| 2 | 🔴 Alta | 9 pantallas del plan completamente ausentes | S6-S7 | Crear pantallas faltantes (Reglas, Notificaciones, Perfil, Bancos, Ajustes, Exportar, Resúmenes) |
| 3 | 🔴 Alta | Color `Income` incorrecto (`#00C853` vs `#16A34A`) | S4-S5 | Reemplazar `Color(0xFF00C853)` → `Income` en Dashboard, Transacciones, Resumen |
| 4 | 🟡 Media | Derivadas DGII no se muestran agrupadas bajo su padre | S4 | Implementar accordeon según plan §7.5 |
| 5 | 🟡 Media | `tnum` ausente en todos los montos monetarios | S1 | Aplicar `TabularNumber` en pantallas con montos |
| 6 | 🟡 Media | Fuente Inter no integrada | S8 | `ui-text-google-fonts` + `GoogleFont("Inter")` |
| 7 | 🟡 Media | Tests solo para BanReservas — Popular/Qik/Cibao sin cobertura | S3/S9 | Crear 3 tests de regresión |
| 8 | 🟡 Media | Exportación es CSV en vez de XLSX con fastexcel | S6 | Reescribir `ExportacionUseCase` con fastexcel, múltiples hojas |
| 9 | 🟠 Baja | `FinanzasSwitch`, `BankLogo`, `MerchantLogo` no existen | S1/S8 | Crear componentes del DS |
| 10 | 🟠 Baja | Colores hardcodeados en pantallas (Revisión, Historial, Dashboard) | S8 | Reemplazar `Color(0xFF...)` inline por tokens `Color.kt` |

---

### Checklist v1.0 del Plan §11

| Item | Estado |
|------|--------|
| Firebase operativo con reglas, índices, seedings | ⚠️ (no verificable sin ejecutar) |
| APK release firmado bajo 50 MB | ❌ (R8 deshabilitado) |
| 4 parsers funcionando contra fixtures reales | ⚠️ (parsers existen; Qik/Cibao no persistentes por bug ExitoTarjeta) |
| Google Sign-In sin errores | ✅ |
| 25 pantallas pixel-perfect | ❌ (11/20+ implementadas) |
| Detección con confianza ramifica 3 rutas | ✅ |
| Duplicados detectados y mostrados | ⚠️ |
| Derivadas DGII agrupadas visualmente | ❌ |
| Comparativa mensual calculada | ✅ |
| Categorización en cascada con aprendizaje | ⚠️ |
| Reglas sugeridas tras 50+ tx o 7 días | ⚠️ (worker existe; scheduling incierto) |
| Resúmenes día/semana/mes in-app | ❌ |
| Date range picker | ✅ |
| Exportar XLSX con vista previa | ❌ |
| Conversor DOP/USD | ✅ |
| Notificaciones programadas WorkManager | ❌ |
| Ajustes generales backup/borrar | ❌ |
| Tests unitarios | ⚠️ (solo BanReservas parcial) |
| Crashlytics + Analytics | ❌ |
