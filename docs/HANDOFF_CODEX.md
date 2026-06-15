# Estado Actual del Proyecto y Deuda TÃ©cnica (Handoff)

> **Documento maestro de tracking y traspaso.**
> Este archivo consolida el estado real verificado del proyecto, tareas completadas recientemente y la deuda tÃ©cnica (incluyendo migraciones pendientes de arquitectura y modo oscuro). Reemplaza a las auditorÃ­as antiguas, las cuales han sido eliminadas para evitar redundancias.
> 
> **Fecha de actualizaciÃ³n:** 2026-06-15.

---

## 1. Hitos Completados Recientemente

### UnificaciÃ³n de Filtros (Completado)
Se ha unificado con Ã©xito la interfaz de filtrado y el selector de periodos para las pantallas principales:
- **Componentes ExtraÃ­dos:** `PeriodoDropdown` y `FiltrosSheet` extraÃ­dos a `presentation/components/Filters.kt` sin alterar estilos.
- **Tipado Fuerte:** Creados `PeriodoState` y `FiltrosAvanzadosState` en `presentation/model/Filtros.kt`.
- **ViewModels Refactorizados:** `DashboardViewModel`, `ResumenViewModel` y `TransaccionesViewModel` migrados para usar los nuevos estados.
- **Dominio:** `ObtenerResumenUseCase` actualizado para aceptar `FiltrosAvanzadosState` y aplicar filtros (banco, monto, categorÃ­as) directamente al balance neto y agregaciones (donas/barras).
- **Pruebas:** 15 pruebas unitarias (`DashboardViewModelTest`, `ResumenViewModelTest`, `ObtenerResumenUseCaseFiltrosTest`) creadas y pasando verde.
- Se repararon pruebas existentes (`ObtenerBalancesPorCuentaUseCaseTest`, `AnalizarTransaccionesUseCaseTest`) que arrojaban falsos positivos por mock de parÃ¡metros `anyOrNull()`.

### BHD y Parsers
- **BHD:** Activo como parser de cuentas PDF, registrado en Hilt y seed. El fixture real vive en `docs/03-fixtures/bhd.pdf` y su regresiÃ³n se ejecuta como prueba instrumentada.
- **Cibao / Qik:** Bugs de exactitud (metadata columnar en Cibao, regex de lÃ­mite en Qik) corregidos en commits recientes (`66cc244`).

### Core y Preferencias
- CentralizaciÃ³n de `ClasificacionFinanciera` y `TasaCambio` en dominio.
- ConfiguraciÃ³n de Hilt para `WorkManager`.
- Ajustes Generales, notificaciones locales y resÃºmenes por periodo (`7cf0233`).

---

## 2. Gotchas de Setup Local

- **JDK para Gradle:** usar el JBR de Android Studio (`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`).
- **`app/google-services.json`:** gitignored pero **obligatorio**. (SHA-1 debug: `7B:D1:FD:74:C5:F1:CC:36:DE:C1:DF:9E:CF:28:40:C5:52:53:56:17`).
- **Fixtures reales:** en `docs/03-fixtures/` (NUNCA COMMITEAR).
- **PDFBox:** solo se testea por test instrumentado (`androidTest`).
- **Tests Instrumentados:** `.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=<FQN>"`.
- Tras tocar modelos/DTOs correr suite completa: `.\gradlew.bat testDebugUnitTest` (â‰ˆ208 tests actualmente, todos en verde).

---

## 3. Backlog Consolidado: Issues Pendientes e ImplementaciÃ³n

### Issue #1 â€” MigraciÃ³n a Paging 3 y Arquitectura de Datos
- **Contexto:** Se extrajeron interfaces (`ITransaccionRepository`) pero la capa de datos aÃºn orquesta directamente Firebase/OfflineStore y la UI acopla detalles locales (`TransaccionesCursor`).
- **Pendiente:** 
  1. Migrar `ITransaccionRepository` a firmas reactivas `Flow<PagingData<T>>`.
  2. Implementar `PagingSource` local basado en el `OfflineStore`.
  3. Actualizar `TransaccionesViewModel` para consumir `PagingData` mediante `collectAsLazyPagingItems()` en Compose y eliminar `TransaccionesCursor`.
  4. Implementar incrementalmente `RemoteDataSource` y `LocalDataSource`.

### Issue #2 â€” MigraciÃ³n a Modo Oscuro
- **Contexto:** Se detectÃ³ que la interfaz utiliza variables estÃ¡ticas de color (`BgScreen`, `Ink`, `Primary`, etc.) desde `com.example.flowtrack.ui.theme.*` impidiendo reaccionar al cambio de tema.
- **Pendiente:** Reemplazar colores estÃ¡ticos por variables dinÃ¡micas semÃ¡nticas (`MaterialTheme.colorScheme.background`, `.surface`, `.onSurface`, etc.) en:
  - Globales: `BankRegistry.kt`, `FlowTrackDrawer.kt`.
  - Pantallas: `DashboardScreen.kt`, `HistorialScreen.kt`, `PresupuestosScreen.kt`, `ConfiguracionScreen.kt`, `BancosYCuentasScreen.kt`, `PerfilScreen.kt`, `ReglasScreen.kt`, `ConversorScreen.kt`, `DuplicadosScreen.kt`, `ExportarScreen.kt`, `NotificacionesScreen.kt`, `LoginScreen.kt`.

### Issue #3 â€” Limpieza de CÃ³digo Muerto
- **Contexto:** Diversas utilidades, DTOs y mappers no estÃ¡n en uso.
- **Pendiente:** Eliminar o refactorizar:
  - `Mappers.kt`: `toConfiguracionUsuarioDto`, `toReglaCategoriaDto`, `toReglaSugeridaDto`, `toNotificacionConfigDto`, `CuentaDetectada.toDomainConBanco`.
  - `OfflineStore.kt`: MÃ©todos inactivos `replaceTransacciones`, `replaceCuentas`, `replaceTarjetas`, etc.
  - `ExportacionUseCase.kt`: Sobrecargas de conveniencia sin uso para `exportarCsv` y `exportarPdf` con firma `(context, uid, inicio, fin)`.
  - Recursos y XML: Limpiar paletas `purple_200`, `teal_200`, `launch_icon_bg` de `colors.xml`.

### Issue #4 â€” Derivadas DGII no agrupadas bajo su transacciÃ³n padre
- **Estado:** El modelo soporta el vÃ­nculo `transaccionPadreId`, y los parsers lo aplican. 
- **Pendiente (UI):** En `TransaccionesScreen`, mostrar las derivadas como acordeÃ³n bajo la padre (ej. `Badge("+N impuesto")`). Expandir con `AnimatedVisibility`.

### Issue #5 â€” `tnum` (cifras tabulares) ausente en montos
- **Estado:** Ya existe la funciÃ³n de formateo, falta aplicar el estilo tipogrÃ¡fico.
- **Pendiente:** Crear `TextStyle` `TabularNumber` (`fontFeatureSettings = "tnum"`) y aplicarlo en todos los `Text` de montos.

### Issue #6 â€” Fuente Inter no integrada
- **Pendiente:** Adoptar Google Fonts (`ui-text-google-fonts`) para inyectar "Inter" en `Type.kt` reemplazando `FontFamily.Default`. Dependencia debe ir en `libs.versions.toml`.

### Issue #7 â€” Exportar XLSX + Vista previa
- **Estado:** `ExportacionUseCase` solo tiene CSV y PDF. 
- **Pendiente:** 
  1. Agregar exportaciÃ³n a XLSX usando `fastexcel`.
  2. Construir `ExportarScreen` y `VistaPreviaReporteScreen`.
  3. Integrar con `FileProvider`.

### Issue #8 â€” MerchantLogo (Design System)
- **Estado:** `TransaccionItem` usa un Ã­cono genÃ©rico.
- **Pendiente:** Crear un `MerchantRegistry` (descripcionNormalizada â†’ abbr+color) e implementar el componente `MerchantLogo`.

### Issue #9 â€” Integridad de Persistencia de Tarjeta
Derivado de hallazgos del tester, requieren mitigaciÃ³n en repositorios:
- **ALTO:** `ImportacionRepository.persistirCargaTarjeta` pisa el campo `alias` al usar `SetOptions.merge()`. **Fix:** excluir `alias` del merge o leer el existente y preservarlo.
- **MEDIO:** Escribir en `EstadoTarjetaSnap` degrada datos si se reimporta. **Fix:** Solo escribir si no existe o versionar.
- **MEDIO:** Dinero persistido como `Double` en DTOs. **Fix:** Migrar DTOs monetarios a `String` (`toPlainString()`). Riesgo alto, requiere asegurar compatibilidad en mappers y tests de RoundTrip.

### Issue #10 â€” Consolidar literales de categorÃ­as en `MotorCategorizacion`
- **Contexto:** `CategoriaCatalogo` ya centraliza el catÃ¡logo de categorÃ­as, pero `MotorCategorizacion` todavÃ­a contiene literales de texto duplicados para inferencia automÃ¡tica.
- **Pendiente:** Reemplazar los retornos y comparaciones de categorÃ­as en `MotorCategorizacion` por constantes de `CategoriaCatalogo`, sin modificar la lÃ³gica de precedencia ni el comportamiento de inferencia.
- **Objetivo:** Evitar divergencia entre el catÃ¡logo de UI, exportaciÃ³n y la heurÃ­stica de categorizaciÃ³n automÃ¡tica.
- **Riesgo:** Bajo-medio. El cambio es mecÃ¡nico, pero puede romper pruebas si algÃºn literal no queda mapeado exactamente a su constante equivalente.

### Issue #11 â€” Quitar fallback mock de `TasaCambioRepository`
- **Contexto:** `TasaCambioRepository` aÃºn crea una tasa `BCRD (Mock)` local cuando no existe la tasa del dÃ­a en Firestore. Esa semilla de respaldo quedÃ³ fuera de `APP-401`.
- **Pendiente:** Reemplazar el fallback mock por un comportamiento explÃ­cito de error o por una estrategia de fuente real definida por el plan maestro, sin inventar datos locales.
- **Objetivo:** Evitar que el sistema presente tasas sintÃ©ticas como si fueran reales cuando no hay sincronizaciÃ³n remota.
- **Riesgo:** Medio-alto. Puede afectar conversor, resumen y cualquier flujo que hoy dependa implÃ­citamente de la tasa mock como Ãºltimo recurso.


### Issue #12 â€” ImportaciÃ³n parcial en ACAP/Cibao y Qik
- **Contexto:** En algunos estados de cuenta importados de Asociación Cibao (ACAP) y Qik, el resumen se persiste y se muestra correctamente, pero las transacciones no aparecen en la UI ni en el flujo reactivo esperado.
- **Pendiente:** Validar el flujo de importación completo para esos parsers: detección de formato, parseo, mapeo a dominio, persistencia en Firestore/OfflineStore y exposición reactiva en la pantalla de transacciones.
- **Objetivo:** Identificar por qué el resumen queda disponible mientras las transacciones se pierden o no se renderizan, y corregir la ruta exacta sin tocar agregación.
- **Riesgo:** Alto. Puede estar en parsers, persistencia o filtros/reactividad; requiere aislar el origen real antes de modificar comportamiento.

### Issue #13 â€” Back navigation distinto para Sidebar y Configuración
- **Contexto:** El retorno con `Back` debe comportarse distinto según el origen de la pantalla. Si una pantalla se abrió desde el sidebar, el retorno debe reabrir el sidebar y volver a la pantalla principal previa. Si la pantalla se abrió desde Configuración, el retorno debe volver a Configuración con el sidebar cerrado.
- **Pendiente:** Separar la lógica de navegación de retorno para distinguir origen `Sidebar` vs `Configuración`, sin romper el flujo actual de las pantallas principales.
- **Objetivo:** Evitar que pantallas abiertas desde Configuración hereden el comportamiento de reabrir drawer, y mantener el contrato actual del sidebar solo para accesos desde el menú lateral.
- **Riesgo:** Medio-alto. Toca navegación global y puede afectar backstack si no se acota correctamente el origen de la pantalla.

### Issue #14 â€” Botón de retroceso en Convertor de divisas
- **Contexto:** La pantalla de Convertor de divisas no muestra botón visible de retroceso, aunque debe seguir la misma lógica de retorno que las demás pantallas secundarias: volver a la pantalla anterior y reabrir el sidebar cuando corresponda.
- **Pendiente:** Agregar el botón de retroceso en la barra superior del Convertor y enlazarlo con la misma lógica de navegación usada en las otras pantallas del drawer.
- **Objetivo:** Unificar la experiencia de regreso en Convertor con Metas, Presupuestos, Bancos y cuentas, Historial, Exportar y Sugerencias.
- **Riesgo:** Bajo-medio. El ajuste es de UI/navegación, pero debe respetar el origen de apertura para no romper el contrato definido en el Issue #13.

### Issue #15 â€” Botón para volver a Inicio tras importar
- **Contexto:** Después de completar una importación, el usuario necesita una acción explícita para regresar a la pantalla de Inicio sin depender del backstack o de cerrar pantallas manualmente.
- **Pendiente:** Agregar un botón de navegación a Inicio en el flujo de importación exitosa, integrado en la UI existente sin alterar la lógica de importación.
- **Objetivo:** Reducir fricción post-importación y llevar al usuario a la pantalla principal para revisar el resultado.
- **Riesgo:** Medio. Toca la pantalla/flujo posterior a importación y debe respetar el backstack actual y el origen de navegación.

### Issue #16 â€” Filtro Ingresos/Gastos no aplica a movimientos de tarjeta
- **Contexto:** En la pantalla de transacciones, el filtro por tipo funciona para `Transaccion` pero deja visibles los `MovimientoTarjeta` aunque no correspondan al tipo seleccionado.
- **Pendiente:** Hacer que el filtro `Ingresos / Gastos / Todas` afecte también a los movimientos de tarjeta mostrados en la misma pantalla.
- **Objetivo:** Mantener consistencia visual y funcional entre transacciones bancarias y movimientos de tarjeta bajo el mismo filtro.
- **Riesgo:** Medio. Hay que mapear correctamente tipos de movimiento de tarjeta a ingresos o gastos financieros sin romper la lista principal.

### Issue #17 â€” Persistencia del estado del filtro al cambiar de pantalla
- **Contexto:** Al salir y volver a una pantalla, el estado del filtro de transacciones no siempre se conserva como espera el usuario.
- **Pendiente:** Garantizar persistencia de estado del filtro al cambiar de pantalla y restaurar el estado activo cuando se vuelve a `Transacciones`.
- **Objetivo:** Evitar que el usuario pierda el contexto de filtros aplicados al navegar entre pantallas principales.
- **Riesgo:** Medio. Toca navegación y restauración de estado; hay que evitar regresiones en el backstack.

### Issue #18 â€” Normalización de categoría legacy `Compra`
- **Contexto:** Algunas transacciones y movimientos llegan con la categoría legacy `"Compra"` y el sistema los trata como si fueran `Sin categorizar` en filtros, listas y gráficos.
- **Pendiente:** Normalizar ese alias a la categoría canónica `compras` en la capa de lectura, filtrado y agregación sin alterar el dato persistido original.
- **Objetivo:** Alinear UI, filtros y gráficos con la categoría real y evitar falsos `Sin categorizar`.
- **Riesgo:** Medio. Toca la fuente de verdad de categorías y puede afectar pantallas que consumen ids crudos.

### Issue #19 â€” Detalle editable para movimientos de tarjeta
- **Contexto:** La lista de movimientos de tarjeta no permite abrir una vista de detalle ni cambiar su categoría, a diferencia de las transacciones.
- **Pendiente:** Agregar detalle navegable para `MovimientoTarjeta` con acción de cambio de categoría usando la misma lógica de edición existente.
- **Objetivo:** Paridad funcional entre transacciones bancarias y movimientos de tarjeta en la pantalla de transacciones.
- **Riesgo:** Medio. Hay que añadir soporte de edición en repositorio y ViewModel sin romper el flujo actual de transacciones.

### Issue #20 â€” Refresco instantáneo del detalle al recategorizar
- **Contexto:** Al cambiar la categoría desde el detalle, la pantalla no refleja el nuevo valor hasta salir y volver.
- **Pendiente:** Hacer que el detalle derive su estado de la lista reactiva para que el cambio se vea inmediatamente en `Transaccion` y `MovimientoTarjeta`.
- **Objetivo:** Confirmación visual instantánea después de recategorizar, sin navegación adicional.
- **Riesgo:** Medio. Requiere evitar objetos congelados en memoria y mantener el detalle sincronizado con el estado fuente.

### Issue #21 â€” Sustituir logos de bancos por assets locales
- **Contexto:** Los bancos deben mostrar los logos disponibles en `app/src/main/res/logos/` en lugar de variantes genéricas o inconsistentes.
- **Estado:** Completado. El mapeo visual ahora usa assets locales compilables en `res/drawable`.
- **Objetivo:** Unificar la identidad visual de bancos y cuentas con los recursos reales del proyecto.
- **Riesgo:** Bajo-medio. Toca recursos y componentes de badges/logos, pero no la lógica financiera.

### Issue #22 â€” Barra de estado invisible en modo oscuro
- **Contexto:** En tema oscuro la barra de estado no se distingue correctamente, जबकि en tema claro sí se ve.
- **Estado:** Completado. La Activity aplica el modo de barras según el tema efectivo y mantiene transparencia.
- **Objetivo:** Mantener visibilidad consistente de estado y navegación del sistema en ambos temas.
- **Riesgo:** Medio. Toca configuración visual global y puede requerir revisar `enableEdgeToEdge` y colores de sistema.

### Issue #23 â€” Tema por defecto según sistema y persistencia de elección
- **Contexto:** El usuario puede cambiar el modo visual manualmente, pero la app no debe ignorar la preferencia que el sistema ya tiene asignada por defecto.
- **Estado:** Completado. El arranque usa el tema del sistema como valor inicial y persiste la preferencia guardada del usuario.
- **Objetivo:** Respetar el modo del sistema por defecto y seguir permitiendo el cambio manual actual.
- **Riesgo:** Medio. Toca preferencias, estado persistido y la lógica de arranque del tema.

### Issue #24 — Logos de bancos en Inicio/Resumen y contraste para PNG en dark mode
- **Contexto:** Las tarjetas de bancos en Inicio y Resumen todavía muestran monogramas, y varios logos PNG se pierden sobre fondos oscuros si no tienen una base clara.
- **Estado:** Completado. Inicio y Resumen ahora usan los logos locales de banco, y los logos PNG se renderizan sobre fondo claro en ambos temas con contraste adicional en dark mode.
- **Objetivo:** Unificar la identidad visual de bancos en toda la app y evitar pérdida de legibilidad en logos con transparencia o colores profundos.
- **Riesgo:** Bajo-medio. Toca componentes visuales reutilizados y el tratamiento de assets locales.

### Issue #25 — Logos de bancos en la pantalla de importación
- **Contexto:** La pantalla de importación de estados seguía mostrando monogramas en la selección de banco.
- **Estado:** Completado. La selección de banco ahora usa los mismos logos locales y fondo claro que el resto de la app.
- **Objetivo:** Mantener consistencia visual entre importación, inicio y resumen.
- **Riesgo:** Bajo. Es un cambio de UI reutilizando el componente de logo ya corregido.

---

*Cualquier desarrollo futuro debe tomar como punto de partida exclusivo este documento y el `PLAN_MAESTRO_V2.md`.*
