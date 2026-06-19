# CODEX.md

Contexto operativo para Codex en este repositorio.

> Actualizado: 2026-06-10
> Este archivo resume el estado de trabajo. No reemplaza el Plan Maestro V2 ni el design system.

## 1. Orden de autoridad

Usar las fuentes en este orden:

1. `AGENTS.md`
2. `docs/01-analisis/03_PLAN_MAESTRO_V2.md`
3. `docs/02-desing-system/04_DESIGN_SYSTEM.md`
4. `docs/HANDOFF_CODEX.md`
5. `CLAUDE.md`
6. `docs/AUDITORIA_SPRINTS.md`

Si hay contradiccion, respetar ese orden. No asumir que un issue sigue vigente sin revisar el codigo actual.

## 2. Producto y restricciones

FlowTrack es una app Android nativa en Kotlin y Jetpack Compose para importar, analizar y exportar transacciones bancarias de Republica Dominicana.

- Arquitectura: Clean Architecture + MVVM.
- DI: Hilt.
- Backend base: Firebase Auth + Firestore.
- Procesamiento de archivos: local.
- Firebase Storage sigue fuera de alcance.
- Cloud Functions se usan solo para FCM y eventos de notificacion aprobados por el usuario.
- Parsers fijos: PdfBox-Android, OpenCSV y Apache POI HSSF.
- Exportacion XLSX: fastexcel.
- Dinero: `BigDecimal`; nunca `Double` o `Float` en dominio.
- Dominio en espanol; terminos tecnicos universales en ingles.
- UI exclusivamente Compose y conforme al design system.
- No exponer, subir ni commitear los fixtures bancarios reales.

## 3. Estado verificado en esta sesion

- Branch activo: `sprint-3-parsers-flujo-importacion`.
- El repo ya no es el template inicial descrito en `CLAUDE.md`.
- Los filtros de Dashboard y Transacciones se conservan al cambiar de pantalla mediante `SavedStateHandle` y navegacion con `saveState/restoreState`.
- Transacciones carga paginas de 30 documentos con cursor Firestore al acercarse al final del scroll.
- El Dashboard mantiene su estado mientras vive su ViewModel y ejecuta en paralelo sus consultas independientes.
- El menu de las pantallas principales abre un `ModalNavigationDrawer` global con `Metas de ahorro`, `Presupuestos`, `Bancos y cuentas` y `Tasas de cambio`.
- `Configuracion` quedo reducida a ajustes, perfil, exportacion, borrado de datos y cierre de sesion.
- `ProcesarArchivoUseCase` quedo en `com.example.flowtrack.domain.usecase`.
- Se cerró el refactor de core/data/domain: `ClasificacionFinanciera` centraliza los totales, `TasaCambio` vive en `domain/model`, y los repositorios afectados usan DTOs y mappers explícitos.
- La capa de notificaciones quedo rearmada con:
  - horarios exactos,
  - fallback en WorkManager,
  - notificacion sintetica de prueba,
  - FCM por dispositivo,
  - backend NestJS para eventos remotos.
- El scaffold local en `functions/` ya fue retirado; Cloud Run es la unica implementacion de backend activa.
- Build aun no verificado por esta sesion por bloqueo del wrapper de Gradle al intentar descargar la distribucion.

## 4. Trabajo local que se debe preservar

- No incluir `.idea/deploymentTargetSelector.xml` en commits.
- Los fixtures reales de `docs/03-fixtures/` siguen siendo locales y sensibles.
- La persistencia de filtros solicitada es de sesion/navegacion, no de Firestore ni DataStore.

## 5. Estado actualizado de issues

Tomar `docs/HANDOFF_CODEX.md` como indice actualizado y verificar siempre el codigo antes de editar.

- #1: completado.
- #2: completado.
- #3: completado.
- #4: pendiente de validacion e implementacion de derivadas DGII.
- #5: pendiente.
- #6: pendiente.
- #7: pendiente.
- #8: pendiente.
- #9: pendiente.
- #10: pendiente.
- #11: pendiente.

## 6. Toolchain local

En Windows:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Notas:

- `app/google-services.json` es local, esta ignorado y es necesario para builds.
- PDFBox-Android no funciona en tests JVM puros.
- Para instrumentados usar `connectedDebugAndroidTest`.
- Tras cambiar modelos o DTOs, ejecutar la suite JVM completa.

## 7. Procedimiento de trabajo

Antes de implementar:

1. Leer `AGENTS.md`, este archivo y el handoff.
2. Revisar branch, ultimo commit y `git status`.
3. Inspeccionar el codigo actual del issue.
4. Confirmar con el usuario el issue o sprint que se trabajara si hace falta.

Durante la implementacion:

1. Conservar cambios locales ajenos.
2. Mantener el alcance en el issue confirmado.
3. Usar el version catalog para dependencias.
4. No imprimir ni copiar datos de fixtures reales.

Al cerrar:

1. Ejecutar build y tests relevantes cuando el entorno lo permita.
2. Reportar compilacion, tests, conformidad visual y bloqueadores.
3. Hacer commits separados y claros solo para el alcance acordado.
