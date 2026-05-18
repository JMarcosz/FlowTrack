# FlowTrack

Aplicación Android nativa para consolidar, visualizar y analizar transacciones bancarias de República Dominicana. El usuario sube sus estados de cuenta (PDF, CSV, XLS), la app los parsea localmente, los persiste en Firestore y los presenta en dashboards interactivos.

---

## Diagrama del sistema

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            DISPOSITIVO ANDROID                              │
│                                                                             │
│  ┌──────────┐    ┌─────────────────────────────────────────────────────┐   │
│  │  Widget  │    │                    APP FLOWTRACK                    │   │
│  │ (Glance) │    │                                                     │   │
│  │          │    │  ┌─────────────┐   ┌──────────────────────────┐   │   │
│  │ Balance  │    │  │ Presentación │   │       Dominio            │   │   │
│  │ mensual  │    │  │  (Compose)   │   │                          │   │   │
│  │          │    │  │             │   │  UseCases:               │   │   │
│  └────┬─────┘    │  │  Screens:   │   │  · ObtenerResumen        │   │   │
│       │          │  │  Dashboard  │◄──┤  · ObtenerBalanceNeto    │   │   │
│       │          │  │  Transacc.  │   │  · ObtenerPresupuestos   │   │   │
│       │          │  │  Resumen    │   │    ConGasto              │   │   │
│       │          │  │  Tarjetas   │   │  · Exportacion (CSV/PDF) │   │   │
│       │          │  │  Config.    │   │  · Categorización        │   │   │
│       │          │  │  + 14 más   │   │                          │   │   │
│       │          │  └──────┬──────┘   └──────────┬───────────────┘   │   │
│       │          │         │                     │                    │   │
│       │          │  ┌──────▼──────────────────────▼───────────────┐  │   │
│       │          │  │               Datos (Data Layer)             │  │   │
│       │          │  │                                              │  │   │
│       │          │  │  Parsers            Repositories             │  │   │
│       │          │  │  ┌─────────────┐   ┌───────────────────┐   │  │   │
│       │          │  │  │ BanReservas │   │ TransaccionRepo   │   │  │   │
│       │          │  │  │ PDF Parser  │   │ CuentaRepo        │   │  │   │
│       │          │  │  ├─────────────┤   │ TarjetaRepo       │   │  │   │
│       │          │  │  │ Popular     │   │ PresupuestoRepo   │   │  │   │
│       │          │  │  │ CSV Parser  │   │ MetaRepo          │   │  │   │
│       │          │  │  ├─────────────┤   │ TasaCambioRepo    │   │  │   │
│       │          │  │  │ Qik         │   │ + 8 más           │   │  │   │
│       │          │  │  │ PDF Parser  │   └────────┬──────────┘   │  │   │
│       │          │  │  ├─────────────┤            │               │  │   │
│       │          │  │  │ Cibao       │   DataStore│(configuración)│  │   │
│       │          │  │  │ XLS Parser  │            │               │  │   │
│       │          │  │  └─────────────┘            │               │  │   │
│       │          │  └─────────────────────────────┼───────────────┘  │   │
│       │          └───────────────────────────────┬┘                   │   │
│       │                                          │                    │   │
│  ┌────▼─────────────────────────────────────────▼──────────────────┐ │   │
│  │                     WorkManager                                  │ │   │
│  │   BalanceWidgetWorker (6h)   ClusteringWorker (categorización)   │ │   │
│  └──────────────────────────────────────────────────────────────────┘ │   │
└──────────────────────────────────────────────────────┬────────────────┘   │
                                                       │
              ┌────────────────────────────────────────▼────────────────────┐
              │                    FIREBASE (Google Cloud)                   │
              │                                                              │
              │  ┌──────────────────┐        ┌──────────────────────────┐   │
              │  │   Firebase Auth  │        │      Firestore           │   │
              │  │  Google Sign-In  │        │                          │   │
              │  └──────────────────┘        │  usuarios/{uid}/         │   │
              │                              │    transacciones         │   │
              │                              │    cuentas               │   │
              │                              │    tarjetas              │   │
              │                              │    movimientosTarjeta    │   │
              │                              │    presupuestos          │   │
              │                              │    metas                 │   │
              │                              │    configuracion         │   │
              │                              │    reglasCategoria       │   │
              │                              │    historialImportacion  │   │
              │                              │  tasasCambio/ (global)   │   │
              │                              └──────────────────────────┘   │
              └──────────────────────────────────────────────────────────────┘
```

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Navegación | Navigation Compose |
| DI | Hilt (Dagger) |
| Backend | Firebase Firestore + Firebase Auth |
| Estado local | DataStore Preferences |
| Background | WorkManager |
| Widget | Glance AppWidget 1.1.1 |
| Parsers PDF | PdfBox-Android 2.0.27 |
| Parsers XLS | Apache POI HSSF 5.3.0 |
| Parsers CSV | OpenCSV 5.9 |
| Export PDF | Android PdfDocument (nativo) |
| Dinero | `BigDecimal` (nunca `Double`) |
| Min SDK | 34 (Android 14) |
| Target SDK | 36 |

---

## Bancos soportados

| Banco | Formato | Parser |
|-------|---------|--------|
| BanReservas | PDF | `BanReservasPdfParser` |
| Banco Popular | CSV | `PopularCsvParser` |
| Qik (Banesco) | PDF | `QikPdfParser` |
| Asociación Cibao | XLS | `CibaoXlsParser` |
| BHD León | — | Próximamente |

---

## Pantallas

### Autenticación

#### Login
Autenticación con Google Sign-In mediante Credential Manager. Redirige al Dashboard al iniciar sesión exitosamente. No almacena credenciales localmente.

---

### Navegación principal (Bottom Bar)

#### Dashboard
Vista ejecutiva del mes en curso.
- Tarjeta de balance neto (ingresos − gastos) con tendencia
- Gráfico de dona (`DonutChart` en Canvas) con distribución de gastos por categoría
- Top 3 categorías con mayor gasto
- Acceso rápido a importar estado de cuenta
- Selector de período (mes actual / mes anterior / personalizado)

#### Transacciones
Lista completa de movimientos con búsqueda y filtrado avanzado.
- Búsqueda por texto libre en descripción
- `FiltrosSheet` con filtros acumulables: banco, monto mínimo, monto máximo, categorías múltiples, "solo sin categorizar"
- Chip con badge numérico de filtros activos
- Agrupación por fecha
- Swipe-to-delete con confirmación
- Edición de categoría y nota por transacción

#### Resumen
Análisis financiero consolidado.
- `PatrimonioCard`: balance neto patrimonial = suma de cuentas activas − deuda vigente de tarjetas
- Segmented control: vista por Banco / por Categoría
- Barras horizontales de ingresos vs gastos por banco
- Gráfico de dona de gastos por categoría DGII
- Selector de período

#### Tarjetas
Gestión de tarjetas de crédito.
- Listado de tarjetas con saldo al corte y fecha límite de pago
- **Sección "Próximos pagos"**: tarjetas con vencimiento en los próximos 60 días, ordenadas por fecha, con badge de urgencia (🔴 Vence pronto ≤ 2d / 🟡 Esta semana ≤ 6d / 🟢 A tiempo ≥ 7d)
- Movimientos por tarjeta con filtro por tipo (compra, pago, interés, etc.)

#### Configuración
Hub de ajustes y acciones del usuario.
- Perfil de usuario (nombre, email, avatar inicial)
- Acceso a Bancos y cuentas, Categorías, Importaciones
- Tasas de cambio (navega al Conversor)
- Modo oscuro (toggle persistido en DataStore)
- Presupuestos y Metas de ahorro
- Exportar a Excel (CSV) / Exportar a PDF
- Ajustes avanzados, Notificaciones
- Cerrar sesión

---

### Flujo de importación

#### Upload
Selector de archivo (PDF, CSV, XLS/XLSX) con detección automática de banco por nombre de archivo y metadatos.
- `BankParserFactory` elige el parser correcto
- Previsualización de primeras filas parseadas
- Selector de cuenta de destino

#### Revisión
Revisión de transacciones parseadas antes de confirmar la importación.
- Tabla editable: fecha, descripción, monto, tipo
- Eliminación de filas individuales
- Indicador de progreso de importación

#### Duplicados
Detección automática de transacciones ya existentes en Firestore.
- Lista de posibles duplicados con comparación lado a lado
- Acciones: ignorar / marcar como nuevo / eliminar original

#### Historial
Log de todas las importaciones realizadas.
- Fecha, banco, archivo, cantidad de transacciones, estado (EXITOSO / PARCIAL / FALLIDO)
- Opción de eliminar una carga (soft delete de sus transacciones)

---

### Finanzas personales

#### Presupuestos
Control de gasto por categoría con límite mensual o anual.
- Tarjetas con `LinearProgressIndicator` en verde / amarillo / rojo según porcentaje
- Mensaje de "excedido" cuando el gasto supera el límite
- Sheet de creación: selector de categoría (dropdown), monto límite, período (MENSUAL / ANUAL)
- Soft delete (campo `activo = false` en Firestore)

#### Metas de ahorro
Seguimiento de objetivos financieros a largo plazo.
- Tarjetas con anillo de progreso dibujado en Canvas (`StrokeCap.Round`)
- Depósitos parciales acumulativos (cappados al monto objetivo)
- Emoji picker con 12 opciones para personalizar cada meta
- Ordenadas: pendientes primero, completadas al final
- Soft delete

---

### Herramientas

#### Conversor de divisas
Conversión DOP ↔ USD usando la tasa oficial del día.
- Tasa obtenida de Firestore (caché diario); fallback a valor mock si no hay datos
- Muestra tasa de compra y venta por separado (fuente: BCRD)
- Botón de inversión DOP→USD / USD→DOP
- **Gráfico histórico 30 días** (`TasaHistoricoChart`): línea Canvas con puntos, etiquetas de fecha inicial/final, min/max de la tasa de venta

#### Categorías
CRUD de categorías personalizadas.
- Lista de categorías con color e ícono
- Edición y eliminación inline

#### Reglas de categorización
Motor de reglas automáticas basado en descripción de transacción.
- Tipos de match: EXACTO, CONTIENE, EMPIEZA_CON, REGEX
- Prioridad configurable
- Sugerencias automáticas generadas por `ClusteringWorker` (WorkManager)

#### Sugerencias
Reglas de categorización sugeridas por el sistema basadas en patrones de texto detectados en las transacciones importadas.

#### Perfil
Edición de nombre de usuario y visualización de email de la cuenta Google vinculada.

#### Notificaciones
Gestión de alertas programadas (WorkManager).
- Recordatorios de fecha límite de pago de tarjetas
- Alertas de presupuesto al superar umbrales

#### Ajustes avanzados
Herramientas de mantenimiento de datos.
- Limpiar datos de una cuenta o banco específico
- Recategorización masiva

---

### Widget de pantalla de inicio

#### Balance Widget (Glance)
Widget 4×2 para la pantalla de inicio de Android.
- Muestra ingresos, gastos y balance neto del mes en curso
- Se actualiza automáticamente cada 6 horas mediante `BalanceWidgetWorker` (WorkManager)
- Abre la app al tocarlo
- Tema Material You (colores dinámicos del sistema)

---

## Modelo de datos (Firestore)

```
usuarios/{uid}/
├── transacciones/{id}
│     fecha, descripcion, monto (Double), tipo (DEBITO|CREDITO),
│     bancoCodigo, categoriaId, esDerivada, cargaId, notaUsuario
│
├── cuentas/{id}
│     bancoCodigo, tipoCuenta, alias, balanceActual, moneda, activa
│
├── tarjetas/{id}
│     bancoCodigo, alias, limiteCredito, moneda, estado
│
├── movimientosTarjeta/{id}
│     tarjetaId, fecha, descripcion, monto, tipo (COMPRA|PAGO|…)
│
├── presupuestos/{id}
│     categoriaId, montoLimite, periodo (MENSUAL|ANUAL), activo
│
├── metas/{id}
│     nombre, emoji, montoObjetivo, montoActual, fechaLimite, activa
│
├── configuracion/{uid}
│     temaOscuro, monedaPrincipal, notificacionesActivas
│
├── reglasCategoria/{id}
│     patron, tipoMatch, categoriaId, prioridad
│
└── historialImportacion/{id}
      archivo, banco, totalTransacciones, estado, creadoEn

tasasCambio/{fecha-iso}/       ← colección global (fuera de usuarios)
      compra, venta, fuente, fecha
```

---

## Arquitectura

```
MVVM + Repository + UseCase (Clean Architecture ligera)

┌──────────────────────────────────────────────────────────┐
│  Presentation (Compose Screens + ViewModels)             │
│  · StateFlow<UiState> → collectAsState()                 │
│  · Hilt ViewModels inyectados con hiltViewModel()        │
└───────────────────────┬──────────────────────────────────┘
                        │ llama
┌───────────────────────▼──────────────────────────────────┐
│  Domain (UseCases + Models)                              │
│  · Lógica de negocio pura, sin dependencia de Android    │
│  · BigDecimal para todos los montos                      │
│  · Instant para timestamps (zona America/Santo_Domingo)  │
└───────────────────────┬──────────────────────────────────┘
                        │ llama
┌───────────────────────▼──────────────────────────────────┐
│  Data (Repositories + Parsers + DTOs)                    │
│  · Firestore ↔ DTO ↔ Domain mapping en cada repository  │
│  · Caché offline Firestore: 100 MB persistente           │
│  · Parsers: lectura local del archivo, sin red           │
└──────────────────────────────────────────────────────────┘
```

---

## Exportación

| Formato | Contenido | Compartir |
|---------|-----------|-----------|
| CSV | Todas las transacciones del período con encabezados | ShareSheet (FileProvider) |
| PDF | Portada con rango de fechas + tabla resumen (ingresos / gastos / balance / cantidad) + transacciones agrupadas por día | ShareSheet (FileProvider) |

---

## Estructura del repositorio

```
app/src/main/java/com/example/flowtrack/
├── core/
│   ├── di/              Firebase, Hilt modules
│   ├── extensions/      Formatters de moneda y fecha
│   ├── result/          AppResult<T> (Success | Error)
│   └── workers/         ClusteringWorker
├── data/
│   ├── firestore/
│   │   ├── dto/         DTOs Firestore ↔ domain
│   │   └── repositories/
│   ├── parsers/
│   │   ├── banreservas/ PDF
│   │   ├── popular/     CSV
│   │   ├── qik/         PDF
│   │   ├── cibao/       XLS
│   │   └── core/        StatementParser interface, Factory, Registry
│   └── store/           AppDataStore (DataStore Preferences)
├── domain/
│   ├── model/           Transaccion, Cuenta, Tarjeta, Meta, Presupuesto…
│   └── usecase/         ObtenerResumen, Exportacion, BalanceNeto, Presupuestos…
├── presentation/
│   ├── components/      DonutChart, BottomNav, BankBadge…
│   ├── navigation/      NavGraph, Screen sealed class
│   └── screens/         (19 pantallas)
├── ui/theme/            Design system: colores, tipografía, espaciado, radii
└── widget/              BalanceWidget, BalanceWidgetWorker, BalanceWidgetReceiver
```

---

## Seguridad y privacidad

- Los estados de cuenta nunca salen del dispositivo sin acción explícita del usuario
- El parseo ocurre **localmente** en el hilo de fondo — el archivo nunca se sube a Firebase
- Solo se persisten en Firestore los datos ya parseados (transacciones estructuradas)
- FileProvider con `android:exported="false"` para compartir archivos sin exponer rutas internas
- Reglas de seguridad Firestore: cada usuario solo puede leer/escribir en `usuarios/{su-uid}/**`
