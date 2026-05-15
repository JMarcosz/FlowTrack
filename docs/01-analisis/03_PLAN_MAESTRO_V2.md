# Plan maestro v2 — Finanzas RD

> **Documento maestro consolidado.** Reemplaza al `01_PLAN_DE_ACCION_CLAUDE_CODE.md` y al `02_MODELADO_DE_DATOS.md` originales. Refleja todas las decisiones acordadas entre el plan de análisis y el design system de Claude Design. Es la **fuente única de verdad** para Claude Code.

---

## Decisiones acordadas (registro de cambios)

Esta es la lista completa de decisiones que normalizan el plan v1 contra el diseño:

### Modelado y datos

| # | Decisión | Resolución |
|---|----------|------------|
| 1 | Categorías | **17 del plan** (granular). 8 visibles en UI; el resto se categorizan internamente y se agregan a "Otros" si no caben en UI |
| 2 | Tasa de interés de tarjetas | **Auto-extraer del PDF** + override manual disponible siempre |
| 3 | Auto-extracción de datos de tarjeta | **Auto cuando el PDF lo permite**, manual cuando no |
| 4 | Asociación Cibao | **Incluida en MVP** con `GenericoTarjetaXlsParser` |
| 5 | Detección con confianza | **Pantalla de selección** cuando confianza media/baja |
| 6 | Multi-moneda | **DOP por defecto, USD opcional** cuando el banco lo permita (BanReservas, Popular, Cibao) |
| 7 | Transacciones derivadas DGII | **Agrupadas visualmente** con su transacción padre (expandible) |
| 8 | Estados de tarjeta | **Solo "Activo"** en MVP |

### UX y features nuevas (del diseño)

| # | Feature | Decisión |
|---|---------|----------|
| 9 | Comparativa "% vs mes anterior" en Dashboard | **MVP** |
| 10 | Eliminar transacción | **MVP** con diálogo de dos opciones (solo esta / toda la carga) |
| 11 | Badge "Próximamente" para BHD | **MVP** |
| 12 | Vista de duplicados detectados | **MVP** |
| 13 | Conversor USD/DOP en vivo | **MVP** |
| 14 | Vista previa de reporte XLSX | **MVP** |
| 15 | Pantalla "Bancos y cuentas" (CRUD) | **MVP** |
| 16 | Pantalla de Perfil | **MVP** |
| 17 | Notificaciones programables | **MVP** |
| 18 | Ajustes generales | **MVP** con set típico (idioma, formato fecha/moneda, backup, borrar datos) |
| 19 | Reglas sugeridas automáticas | **MVP** (clustering de patrones) |
| 20 | Resúmenes por día/semana/mes in-app | **MVP** (pantallas dedicadas, además del XLSX) |
| 21 | Date range picker personalizado | **MVP** (completo) |

---

## 1. Visión de producto

App Android nativa en Kotlin que permite a un usuario consolidar, visualizar, analizar y exportar las transacciones de sus cuentas bancarias y tarjetas de crédito en República Dominicana. Identidad visual: limpia, plana, azul institucional, tipografía Inter, basada en el design system de Claude Design.

### Bancos soportados en MVP

- **BanReservas** — PDF tabular (cuenta corriente)
- **Banco Popular Dominicano** — CSV web (cuenta corriente/ahorro)
- **Qik Banco Digital** — PDF narrativo (tarjeta de crédito)
- **Asociación Cibao** — XLS legacy (tarjeta de crédito)
- **BHD** — badge "Próximamente" sin parser activo

### Restricciones técnicas

| Decisión | Valor |
|----------|-------|
| Lenguaje | Kotlin 2.0.20 |
| UI | Jetpack Compose, Material 3 |
| Arquitectura | Clean Architecture + MVVM |
| DI | Hilt 2.52 |
| Backend | Firebase Firestore |
| Auth | Google Sign-In via Credential Manager |
| Procesamiento | 100% en el cliente |
| Multi-usuario | Sí, aislados por `uid` |
| Multi-moneda | DOP por defecto, USD opcional |
| Notificaciones | WorkManager (locales programadas, no FCM) |
| Mínimo SDK | API 26 (Android 8) |
| Target SDK | API 35 |

---

## 2. Inventario completo de pantallas

**20 pantallas en total** (17 del diseño + 3 derivadas de las decisiones acordadas):

### Del diseño original

1. Login / Splash
2. Dashboard
3. Transacciones (lista)
4. Detalle de transacción
5. Resumen (con tabs Banco/Categoría)
6. Resumen-Bank (drill-down)
7. Tarjetas de crédito (carrusel)
8. Detalle de tarjeta
9. Importar estado
10. Revisión de importación
11. Historial de importaciones
12. Reglas de categorización
13. Configuración
14. Notificaciones
15. Tasas de cambio
16. Exportar a Excel
17. Vista previa del reporte

### Nuevas (agregadas por decisiones)

18. **Selección de banco por confianza** (se muestra entre "Importar" y "Revisión" cuando la confianza es media o baja)
19. **Detalle de duplicados** (se muestra al tocar "Ver detalles" de "Posibles duplicados: N")
20. **Bancos y cuentas** (CRUD de cuentas; navegable desde Configuración)
21. **Perfil** (datos Google; navegable desde Configuración)
22. **Resumen diario** in-app
23. **Resumen semanal** in-app
24. **Resumen mensual** in-app
25. **Ajustes generales** (idioma, formato, backup, borrar datos)
26. **Date range picker personalizado** (modal/sheet, no es pantalla independiente)

---

## 3. Modelado de datos en cascada

### 3.1 Capa A — Contrato genérico (sin cambios respecto a v1)

```kotlin
interface BankParser {
    val codigoBanco: String
    val tipoDocumento: TipoDocumento
    val version: Int
    val formatosArchivo: Set<String>
    suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion
    suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo
}
```

Ver `02_MODELADO_DE_DATOS.md` original para tipos auxiliares completos. Sin cambios.

### 3.2 Capa B — Parsers por banco

5 parsers implementados:

- `BanReservasPdfParser` (cuenta corriente, DOP)
- `PopularCsvParser` (cuenta corriente/ahorro, DOP, USD opcional)
- `QikPdfParser` (tarjeta crédito, DOP, auto-extracción completa)
- `CibaoXlsParser` — antes `GenericoTarjetaXlsParser`; ahora se llama directamente Cibao porque ya confirmamos el banco (tarjeta crédito, DOP/USD paralelos)
- BHD: ningún parser, solo entrada en catálogo con `tieneParser: false`

### 3.3 Capa C — Modelo de dominio (cambios respecto a v1)

#### Cambio en `Transaccion`

```kotlin
data class Transaccion(
    // ... campos del v1 ...
    val transaccionPadreId: String? = null,  // existente, ahora SIEMPRE se usa para DGII
    val esDerivada: Boolean = false,         // existente
    val derivadasIds: List<String> = emptyList()  // NUEVO: lista inversa, solo en la padre
)
```

La transacción padre conoce sus derivadas para el agrupamiento visual sin queries adicionales.

#### Cambio en `Tarjeta`

```kotlin
data class Tarjeta(
    // ... campos del v1 ...
    val tasaInteresAnual: Double,           // existente
    val tasaInteresOrigen: OrigenTasa,      // NUEVO: AUTO_EXTRAIDA | MANUAL
    val estado: EstadoTarjeta = ACTIVO      // NUEVO: solo ACTIVO en MVP
)

enum class OrigenTasa { AUTO_EXTRAIDA, MANUAL }
enum class EstadoTarjeta { ACTIVO }  // ampliable post-MVP
```

#### Cambio en `Cuenta`

```kotlin
data class Cuenta(
    // ... campos del v1 ...
    val moneda: Moneda,            // DOP por defecto; USD si el banco lo soporta
    val mostrarEnDashboard: Boolean = true  // NUEVO: opcional ocultar
)
```

#### Nuevos modelos

```kotlin
data class ConfiguracionUsuario(
    val uidUsuario: String,
    val idioma: String = "es-DO",              // es-DO | en-US
    val formatoFecha: String = "dd/MM/yyyy",
    val formatoMoneda: String = "RD$ 0.00",
    val monedaPredeterminada: Moneda = Moneda.DOP,
    val ultimoBackup: Instant? = null
)

data class NotificacionConfig(
    val uidUsuario: String,
    val activa: Boolean = true,
    val pago7dias: Boolean = true,
    val pago3dias: Boolean = true,
    val pago1dia: Boolean = true,
    val pagoMismoDia: Boolean = true,
    val resumenMensual: Boolean = true,
    val alertasGastosAltos: Boolean = false,
    val umbralGastoAlto: BigDecimal = BigDecimal("5000")
)

data class ReglaSugerida(
    val id: String,
    val uidUsuario: String,
    val patronDetectado: String,            // descripción normalizada del cluster
    val categoriaSugerida: String,
    val muestras: List<String>,             // IDs de transacciones que forman el cluster
    val confianzaCluster: Float,
    val creadaEn: Instant,
    val aceptada: Boolean? = null,          // null = pendiente, true = aceptada, false = rechazada
    val resueltaEn: Instant? = null
)
```

#### Categorías visibles en UI vs catálogo completo

Las 17 categorías del catálogo se mantienen. Para la UI:

```kotlin
val CATEGORIAS_VISIBLES_DASHBOARD = listOf(
    "compras", "servicios", "transporte", "alimentacion",
    "salud", "pagos", "ingresos", "otros"
)
// Las demás 9 (atm, impuestos, intereses_comisiones, salario,
// transferencia_recibida, transferencia_enviada, deposito,
// cashback, pago_tarjeta, suscripciones, sin_categorizar)
// se mapean a "otros" en visualizaciones del Dashboard
// pero conservan su categoría real en transacciones, exports y reportes.
```

---

## 4. Estructura completa de Firestore

```
/catalogoBancos/{codigoBanco}
/catalogoCategorias/{categoriaId}
/reglasCategorizacionGlobales/{reglaId}
/tasasCambio/{fechaISO}
/usuarios/{uid}
    ├── /configuracion/preferencias         ← documento único
    ├── /configuracion/notificaciones       ← documento único
    ├── /cuentas/{cuentaId}
    ├── /tarjetas/{tarjetaId}
    ├── /transacciones/{txId}
    ├── /movimientosTarjeta/{movId}
    ├── /estadosTarjeta/{estadoId}
    ├── /reglasCategorias/{reglaId}        ← reglas personales aprendidas
    ├── /reglasSugeridas/{reglaId}         ← reglas pendientes de aceptar
    └── /cargas/{cargaId}
```

---

## 5. Reglas de seguridad ampliadas

```javascript
// Adiciones a las reglas v1:

match /usuarios/{uid} {
    // ... reglas v1 ...

    match /configuracion/{docId} {
        allow read, write: if isOwner(uid)
                            && docId in ['preferencias', 'notificaciones'];
    }

    match /reglasSugeridas/{reglaId} {
        allow read: if isOwner(uid);
        allow create: if isOwner(uid);
        allow update: if isOwner(uid)
                       && request.resource.data.diff(resource.data)
                          .affectedKeys()
                          .hasOnly(['aceptada', 'resueltaEn']);
        allow delete: if isOwner(uid);
    }
}
```

---

## 6. Plan de sprints reajustado (9 sprints)

Sprints de 2 semanas. Plan extendido de **18 semanas** por el alcance ampliado.

### Sprint 1 — Cimientos
- Setup proyecto, Gradle, Compose, Hilt
- Firebase project, Firestore, Auth, reglas iniciales
- Google Sign-In con Credential Manager
- **Design tokens del diseño** aplicados como tema Material 3
- Componentes base: `Pill`, `Switch`, `StatCard`, `ActionRow`, `CategoryIcon`, `MerchantLogo`, `BankLogo`, `DonutChart`, `BottomNav`
- Navegación con NavGraph completo

**Entregable**: app instalable con login, dashboard vacío usando el design system.

### Sprint 2 — Modelo + parser de BanReservas
- Modelos de dominio completos (con cambios v2)
- Repositorios + mappers
- Hash determinístico, idempotencia
- `BankParser` interface + `ParserRegistry` con confianza
- **Parser de BanReservas** (más complejo, sirve de plantilla)
- Detección de DGII como derivada con `transaccionPadreId`
- Pantalla de Upload + Selección de banco por confianza

**Entregable**: subir BanReservas y verlo en Firestore con DGII agrupado.

### Sprint 3 — Resto de parsers + flujo de importación completo
- Parser de **Popular** (CSV)
- Parser de **Qik** (PDF) con auto-extracción de tarjeta
- Parser de **Cibao** (XLS) con detección de banco por estructura
- Pantalla de Revisión con duplicados detectados
- Pantalla de Detalle de duplicados
- Historial de importaciones inmutable

**Entregable**: los 4 parsers consumen archivos reales, duplicados se detectan y muestran.

### Sprint 4 — Dashboard + Transacciones
- Dashboard con StatCards, comparativa mensual, donut categorías, lista por banco
- Pantalla de Transacciones con filtros (Todas/Ingresos/Gastos) y búsqueda
- Agrupamiento por día
- Detalle de transacción con expansión de derivadas DGII
- Bottom sheet de cambio de categoría
- Eliminar transacción con diálogo de dos opciones
- Aprendizaje de reglas personales al recategorizar

**Entregable**: vista y edición completa de transacciones.

### Sprint 5 — Tarjetas + Resúmenes
- Pantalla de Tarjetas (carrusel swipeable, dots)
- Detalle de tarjeta con historial de estados
- Pantalla de Resumen (tabs Banco/Categoría)
- Pantalla de Resumen-Bank (drill-down)
- **Resúmenes por día/semana/mes in-app** (3 pantallas)
- **Date range picker personalizado**
- Conversor DOP/USD desde tasas cacheadas
- API BCRD + fallback TasaReal

**Entregable**: experiencia completa de consumo y análisis.

### Sprint 6 — Reglas + categorización + exportación
- Catálogo precargado completo (17 categorías, ~25 reglas globales)
- Motor de categorización en cascada (personal → global → "sin categorizar")
- Pantalla de Reglas (tabs Mis reglas / Sugeridas)
- **Algoritmo de reglas sugeridas** (clustering de descripciones normalizadas)
- Pantalla de Exportar a Excel con selección de secciones
- **Vista previa del reporte**
- Generación XLSX con fastexcel (múltiples hojas)
- Compartir vía FileProvider

**Entregable**: ciclo cargar → categorizar → resumir → exportar funcionando.

### Sprint 7 — Configuración + notificaciones + ajustes
- Pantalla de Configuración (raíz)
- **Pantalla de Perfil**
- **Pantalla de Bancos y cuentas** (CRUD)
- Pantalla de Notificaciones con toggles
- **Sistema de notificaciones programables** con WorkManager:
  - Cálculo de próximas fechas de pago por tarjeta
  - Schedule de notifications 7d/3d/1d/mismo día
  - Notificación de resumen mensual
  - Alertas de gastos altos (verificación al insertar transacción)
  - Permission Android 13+ `POST_NOTIFICATIONS`
- Pantalla de Tasas de cambio con conversor y historial
- **Pantalla de Ajustes generales** (idioma, formato fecha/moneda, backup, borrar datos)
- Backup: exportar JSON completo a Drive del usuario
- Borrar datos: confirmación + delete recursivo

**Entregable**: configuración y notificaciones completas.

### Sprint 8 — Pulido visual + interacciones
- Validación pixel-perfect contra mockup
- Animaciones: fade-in entre pantallas, slide-up de sheets, dots animados
- Empty states para todas las listas
- Loading states y skeletons
- Error states con retry
- Hover/press states en pressables
- Accesibilidad: labels, semantics, contraste, tamaños mínimos de tap
- Modo offline: caché Firestore + indicadores de sincronización

**Entregable**: experiencia pulida lista para usuarios reales.

### Sprint 9 — Estabilización + lanzamiento
- Tests unitarios de parsers (fixtures de regresión)
- Tests de UseCases críticos (categorización, conversión, agrupamiento DGII)
- Tests de UI de flujos principales
- Crashlytics + Analytics
- Optimización APK (R8, recursos, baseline profiles)
- Documentación de usuario
- Beta interna
- Release a Play Store (track interno)

**Entregable**: versión 1.0 publicada.

---

## 7. Algoritmos clave que requieren implementación específica

### 7.1 Detección con confianza y ramificación de UI

```kotlin
when (val resultado = parserRegistry.detectar(archivo)) {
    is ResultadoDeteccion.AltaConfianza -> {
        // Auto-procesar, ir directo a Revisión
        navegarA(ImportReview(parserId = resultado.parser.codigoBanco))
    }
    is ResultadoDeteccion.MediaConfianza -> {
        // Mostrar pantalla 18 con top 3 candidatos
        navegarA(SeleccionBanco(candidatos = resultado.candidatos, archivo))
    }
    is ResultadoDeteccion.BajaConfianza, is ResultadoDeteccion.NoDetectado -> {
        // Mostrar pantalla 18 con catálogo completo
        navegarA(SeleccionBanco(catalogoCompleto = true, archivo))
    }
}
```

### 7.2 Cálculo de comparativa mensual ("+18.6% vs mes anterior")

```kotlin
class CalcularComparativaMensualUseCase {
    suspend fun ejecutar(uid: String): Comparativa {
        val ahora = LocalDate.now(ZoneId.of("America/Santo_Domingo"))
        val inicioMesActual = ahora.withDayOfMonth(1).atStartOfDay()
        val inicioMesAnterior = ahora.minusMonths(1).withDayOfMonth(1).atStartOfDay()
        val finMesAnterior = inicioMesActual.minusNanos(1)

        val gastoActual = repo.totalGastos(uid, inicioMesActual, ahora.atTime(23, 59, 59))
        val gastoAnterior = repo.totalGastos(uid, inicioMesAnterior, finMesAnterior)

        if (gastoAnterior == BigDecimal.ZERO) return Comparativa(porcentaje = null, ...)

        val delta = (gastoActual - gastoAnterior) / gastoAnterior * BigDecimal(100)
        return Comparativa(porcentaje = delta, esIncremento = delta > BigDecimal.ZERO)
    }
}
```

### 7.3 Algoritmo de reglas sugeridas (clustering)

```kotlin
class GenerarReglasSugeridasUseCase {
    suspend fun ejecutar(uid: String): List<ReglaSugerida> {
        // 1. Obtener todas las transacciones sin categoría o con "sin_categorizar"
        val sinCategorizar = repo.transaccionesSinCategorizar(uid)

        // 2. Agrupar por descripcionNormalizada con tolerancia de similitud
        val clusters = sinCategorizar.groupBy { tx ->
            // Tomar primeras 2-3 palabras significativas
            tx.descripcionNormalizada
                .split(" ")
                .filter { it.length > 3 }
                .take(3)
                .joinToString(" ")
        }.filter { it.value.size >= 3 }  // mínimo 3 transacciones para sugerir

        // 3. Para cada cluster, sugerir categoría basada en:
        //    - Reglas globales que parcialmente matchearían
        //    - Patrones conocidos (UBER → transporte, etc.)
        return clusters.map { (patron, muestras) ->
            val categoriaSugerida = inferirCategoria(patron) ?: "otros"
            ReglaSugerida(
                id = generarId(),
                uidUsuario = uid,
                patronDetectado = patron,
                categoriaSugerida = categoriaSugerida,
                muestras = muestras.map { it.id },
                confianzaCluster = calcularConfianza(muestras),
                creadaEn = Instant.now()
            )
        }
    }
}
```

Se ejecuta como worker periódico cada 7 días o tras insertar > 50 transacciones nuevas.

### 7.4 Programación de notificaciones de pago

```kotlin
class ProgramarNotificacionesPagoUseCase {
    suspend fun ejecutar(uid: String) {
        val config = configRepo.notificaciones(uid)
        if (!config.activa) {
            workManager.cancelAllWorkByTag("notif_pago_$uid")
            return
        }

        val tarjetas = tarjetaRepo.activas(uid)
        tarjetas.forEach { tarjeta ->
            val proximoPago = calcularProximoPago(tarjeta.diaPago)

            listOf(
                7 to config.pago7dias,
                3 to config.pago3dias,
                1 to config.pago1dia,
                0 to config.pagoMismoDia
            ).filter { it.second }.forEach { (dias, _) ->
                val fechaTrigger = proximoPago.minusDays(dias.toLong())
                if (fechaTrigger.isAfter(LocalDate.now())) {
                    scheduleOneTimeWork(
                        tag = "notif_pago_${uid}_${tarjeta.id}_$dias",
                        triggerAt = fechaTrigger,
                        data = workDataOf(
                            "tarjetaId" to tarjeta.id,
                            "diasRestantes" to dias
                        )
                    )
                }
            }
        }
    }
}
```

### 7.5 Agrupamiento visual de derivadas DGII

En la UI de lista de transacciones:

```kotlin
@Composable
fun TransaccionItem(tx: Transaccion, derivadas: List<Transaccion>) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TransaccionRow(
            tx = tx,
            trailing = if (derivadas.isNotEmpty()) {
                { Badge(text = "+${derivadas.size} impuesto") }
            } else null,
            onClick = { if (derivadas.isNotEmpty()) expanded = !expanded
                       else navegarA(TransaccionDetalle(tx)) }
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 56.dp)) {
                derivadas.forEach { derivada ->
                    TransaccionRow(tx = derivada, compact = true)
                }
            }
        }
    }
}
```

Query para obtener derivadas:

```kotlin
suspend fun obtenerDerivadas(uid: String, padreId: String): List<Transaccion> =
    firestore.collection("usuarios").document(uid)
        .collection("transacciones")
        .whereEqualTo("transaccionPadreId", padreId)
        .get().await()
        .toObjects(TransaccionDto::class.java)
        .map { it.toDomain() }
```

Requiere índice adicional (agregado a `firestore.indexes.json`):

```json
{
  "collectionGroup": "transacciones",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "transaccionPadreId", "order": "ASCENDING" }
  ]
}
```

---

## 8. Datos de seeding actualizados

### 8.1 `/catalogoBancos` (sin cambios en estructura, BHD con `tieneParser: false`)

Tal como en v1 + Cibao con `tieneParser: true`.

### 8.2 `/catalogoCategorias` — 17 categorías

Las 17 del documento original. La UI del Dashboard solo renderiza las 8 "visibles" definidas en `CATEGORIAS_VISIBLES_DASHBOARD`; el resto se enrollan en "otros" para gráficas pero conservan identidad en exports y reportes.

### 8.3 `/reglasCategorizacionGlobales`

Las ~25 reglas precargadas (UBER, DIDI, NETFLIX, etc.) del v1. Sin cambios.

---

## 9. Tareas específicas para Claude Code en cada sprint

### En cada sprint, Claude Code debe:

1. **Crear branch** desde `main`: `sprint-N-titulo`
2. **Implementar archivos** según la estructura de carpetas Clean Architecture
3. **Aplicar tokens del design system** (sección 10) en cada Composable nuevo
4. **Escribir tests** para parsers y use cases críticos
5. **Validar compilación** con `./gradlew assembleDebug`
6. **Reportar checklist** al final del sprint:
   - ¿Compila? ✅/❌
   - ¿Tests pasan? ✅/❌
   - ¿Pantallas implementadas coinciden con mockup? ✅/❌
   - ¿Bloqueadores? ✅/❌

### Cuando una operación del MCP de Firebase no esté disponible

Claude Code reporta y propone alternativas: Firebase CLI, scripts manuales, o consola web. No inventa comandos.

---

## 10. Design system extraído (referencia)

Ver `04_DESIGN_SYSTEM.md` para tokens completos (colores, tipografía, spacing, sombras, radii, componentes).

Resumen rápido:
- **Color primario**: `#2F6FED`
- **Tipografía**: Inter (400, 500, 600, 700, 800)
- **Radii**: 8/12/16/20/999
- **Background**: `#F4F6FA`
- **Card**: `#FFFFFF` con borde `#EEF1F5`
- **Income**: `#16A34A` con fill `#E7F7EC`
- **Expense**: `#DC2626` con fill `#FDECEC`

---

## 11. Checklist final de aceptación v1.0

- [ ] Firebase proyecto operativo con reglas, índices y seedings
- [ ] APK release firmado bajo 50MB
- [ ] 4 parsers funcionando contra fixtures reales (BanReservas, Popular, Qik, Cibao)
- [ ] Google Sign-In sin errores
- [ ] 25 pantallas implementadas pixel-perfect según design system
- [ ] Detección con confianza ramifica correctamente a las 3 rutas (alta/media/baja)
- [ ] Duplicados se detectan y muestran en pantalla dedicada
- [ ] Derivadas DGII agrupadas visualmente bajo su padre
- [ ] Comparativa mensual calculada correctamente
- [ ] Categorización en cascada con aprendizaje de reglas personales
- [ ] Reglas sugeridas se generan tras 50+ transacciones o 7+ días
- [ ] Resúmenes por día/semana/mes funcionales in-app
- [ ] Date range picker personalizado operativo
- [ ] Exportar XLSX con vista previa, múltiples secciones
- [ ] Conversor DOP/USD con tasa BCRD cacheada
- [ ] Notificaciones programadas se disparan correctamente
- [ ] Ajustes generales con backup/borrar/idioma/formato
- [ ] Tests unitarios pasando
- [ ] Crashlytics y Analytics activos

Cuando todos los checks pasen, el MVP está listo para Play Store.
