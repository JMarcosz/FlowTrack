# Inventario de Código Muerto - Fase 2

Este documento lista las clases, funciones, variables y recursos inactivos detectados en los archivos clave del proyecto, analizados a partir de los resultados de Lint y búsquedas de referencias en el código base.

## 1. Archivos Core y Di
* **`ParserModule.kt`**: Lint y los análisis estáticos podrían marcar los métodos `@Binds` como no utilizados, pero esto es un falso positivo. Son utilizados por Dagger Hilt en tiempo de compilación. **(Sin código inactivo confirmado)**.
* **`FirestoreFlowExt.kt`**: La función de extensión `Query.asMappedListFlow` no tiene referencias en el resto del proyecto y parece estar inactiva.

## 2. Capa de Datos (Mappers y Repositories)
* **`Mappers.kt`**: Varias funciones de extensión para conversión de DTOs no se están utilizando fuera del archivo `Mappers.kt` o en el resto del proyecto:
  * `DocumentSnapshot.toConfiguracionUsuarioDto()`
  * `DocumentSnapshot.toReglaCategoriaDto()`
  * `DocumentSnapshot.toReglaSugeridaDto()`
  * `DocumentSnapshot.toNotificacionConfigDto()`
  * `CuentaDetectada.toDomainConBanco()`
* **`OfflineStore.kt`**: Un gran número de funciones `replace*` que manejan transacciones atómicas de reemplazo total no están siendo llamadas (el sistema usa principalmente `upsert*` o sincronización granular):
  * `replaceTransacciones`, `replaceCuentas`, `replaceTarjetas`, `replaceEstadosTarjeta`, `replaceMovimientosTarjeta`, `replaceCargas`, `replaceConfiguracion`, `replaceNotificacionConfig`, `replaceMetas`, `replacePresupuestos`, `replaceReglasCategoria`, `replaceReglasSugeridas`, `replaceCategoriasPersonales`, `replaceTasasCambio`.
* **Repositories** (`CuentaRepository`, `HistorialRepository`, `ImportacionRepository`, `TarjetaRepository`, `TransaccionRepository`): Las verificaciones parciales no arrojaron métodos críticos inactivos. Se requeriría un análisis más profundo para asegurar que no hay métodos de consulta (`get*`) no utilizados.

## 3. Dominio
* **`ExportacionUseCase.kt`**: Las sobrecargas de conveniencia de las funciones `exportarCsv` y `exportarPdf` que reciben `(context, uid, inicio, fin)` en lugar del objeto `FiltroExportacion` no parecen tener usos activos en el código actual.

## 4. UI y Presentación (Design System y Componentes)
* **`Color.kt` y `Spacing.kt`**: Varias definiciones en el archivo `Color.kt` (como paletas secundarias `Primary50`, `Cat*` si no se iteran dinámicamente) y las constantes de radio en `Spacing.kt` (`Radii`) podrían no estar siendo consumidas. Además, en los recursos XML (`colors.xml`), Lint marca como sin uso a `purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, `black`, `white`, y `launch_icon_bg`.
* **`ActionRow.kt` y `CategoryIcon.kt`**: No se detectó concluyentemente código inactivo.
* **`LoginViewModel.kt`**: No se detectó código inactivo concluyente.

## 5. Notificaciones
* **`NotificacionAlarmContract.kt`**: **El archivo no existe** en el proyecto.