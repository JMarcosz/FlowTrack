# CODEX.md

Contexto operativo para Codex en este repositorio.

> Actualizado: 2026-06-09.
> Este archivo resume el estado de trabajo. No reemplaza el Plan Maestro V2 ni
> el design system.

## 1. Orden de autoridad

Usar las fuentes en este orden:

1. `AGENTS.md`: reglas obligatorias para Codex.
2. `docs/01-analisis/03_PLAN_MAESTRO_V2.md`: fuente de verdad funcional y técnica.
3. `docs/02-desing-system/04_DESIGN_SYSTEM.md`: fuente de verdad visual.
4. `docs/HANDOFF_CODEX.md`: estado reciente de issues y trabajo local.
5. `CLAUDE.md`: contexto general; su descripción del repo como template está
   desactualizada.
6. `docs/AUDITORIA_SPRINTS.md`: referencia histórica. Está desactualizada y
   cada hallazgo debe verificarse contra el código actual antes de actuar.

Si hay contradicción, respetar el orden anterior. No asumir que un issue sigue
vigente sin revisar la implementación actual.

## 2. Producto y restricciones

FlowTrack es una app Android nativa en Kotlin y Jetpack Compose para importar,
analizar y exportar transacciones bancarias de República Dominicana.

- Arquitectura: Clean Architecture + MVVM.
- DI: Hilt.
- Backend permitido: Firebase Auth + Firestore.
- Procesamiento de archivos: local.
- Sin Firebase Storage ni Cloud Functions.
- Stack de parsers fijo: PdfBox-Android, OpenCSV y Apache POI HSSF.
- Exportación XLSX: fastexcel.
- Dinero: `BigDecimal`; nunca `Double` o `Float` en dominio.
- Dominio en español; términos técnicos universales en inglés.
- UI exclusivamente Compose y conforme al design system.
- No exponer, subir ni commitear los fixtures bancarios reales.

## 3. Estado verificado en esta sesión

- Branch activo: `sprint-3-parsers-flujo-importacion`.
- Commits recientes verificados:
  - `66cc244` — parsers y persistencia de tarjetas (issue #1).
  - `7cf0233` — ajustes, resúmenes y notificaciones (issue #2).
- El repositorio contiene dominio, parsers, navegación y pantallas de varios
  sprints; no es el template inicial descrito en `CLAUDE.md`.
- El trabajo heredado de los issues #1 y #2 ya está separado en commits.
- La carpeta `docs/03-fixtures/` existe con cuatro fixtures reales sensibles.
- La carpeta del design system conserva intencionalmente el typo
  `docs/02-desing-system/`.
- Se ejecutaron `testDebugUnitTest`, `assembleDebug` y el test instrumentado de
  Qik antes de crear los commits anteriores.
- Google Sign-In usa el flujo explícito `GetSignInWithGoogleOption` y
  AndroidX Credentials 1.6.0. En esta PC el acceso fue validado en un Pixel 6
  Pro después de registrar la SHA-1 del keystore debug en Firebase.
- Los filtros de Dashboard y Transacciones se conservan al cambiar de pestaña
  mediante `SavedStateHandle` y navegación con `saveState/restoreState`.
- Transacciones carga páginas de 30 documentos con cursor Firestore al
  acercarse al final del scroll. Cada transacción es un ítem lazy individual.
- El Dashboard mantiene su estado mientras vive su `ViewModel` y ejecuta en
  paralelo sus cuatro consultas independientes de resumen.
- Validación del cambio: `assembleDebug`, `testDebugUnitTest`, instalación en
  Pixel 6 Pro, persistencia de filtros entre pestañas y carga de registros más
  antiguos por scroll. Sin crashes en Logcat.
- El botón de menú de las pantallas principales ya abre un `ModalNavigationDrawer`
  global con `Metas de ahorro`, `Presupuestos`, `Bancos y cuentas` y `Tasas de cambio`.
- La pantalla `Configuración` quedó reducida a ajustes, perfil, exportación,
  borrado de datos y cierre de sesión; `Ajustes`/`Ajustes Generales`/`Avanzado`
  fueron retiradas de la navegación.
- `ConfiguracionViewModel` absorbió el borrado de datos desde
  `LimpiezaRepository`; ya no existe una pantalla separada para esa acción.

## 4. Trabajo local que se debe preservar

No incluir `.idea/deploymentTargetSelector.xml` en commits. Los fixtures reales
de `docs/03-fixtures/` continúan siendo locales y sensibles.

La persistencia de filtros solicitada es de sesión/navegación. No se escribe
cada cambio en Firestore ni DataStore, para evitar I/O innecesario. Si se
requiere conservar filtros después de cerrar completamente la app, esa es una
decisión de producto separada.

## 5. Estado actualizado de issues

Tomar `docs/HANDOFF_CODEX.md` como índice actualizado y verificar siempre el
código antes de editar.

- #1: completado en `66cc244`.
- #2: completado en `7cf0233`.
- #3: completado; `Income`, `Expense` y `Success` tienen semántica separada,
  pruebas de regresión y validación Compose sintética.
- #4: verificar e implementar agrupación visual de derivadas DGII.
- #5: aplicar cifras tabulares (`fontFeatureSettings = "tnum"`) a montos.
- #6: integrar Inter conforme al design system y al catálogo de versiones.
- #7: revisar y reforzar pruebas del parser Popular.
- #8: implementar exportación XLSX y vista previa.
- #9: falta `MerchantLogo`; `FinanzasSwitch` y `BankLogo` ya existen.
- #10: auditar colores inline y reemplazarlos por tokens.
- #11: corregir integridad de persistencia de tarjetas:
  preservar alias, evitar degradar snapshots y migrar dinero de DTOs a una
  representación exacta.

Pendientes diferidos documentados: alertas de gasto alto, i18n y backup local
mediante Storage Access Framework.

## 6. Toolchain local

En Windows:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Notas:

- `app/google-services.json` es local, está ignorado y es necesario para builds.
- PDFBox-Android no funciona en tests JVM puros. Qik y BanReservas requieren
  tests instrumentados.
- Para instrumentados usar:

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=<FQN>"
```

- Tras cambiar modelos o DTOs, ejecutar la suite JVM completa.
- Los fixtures reales tienen nombres locales no normalizados. Usar
  `FixtureLoader`; no registrar nombres, contenido ni datos en logs públicos.

## 7. Procedimiento de trabajo

Antes de implementar:

1. Leer `AGENTS.md`, este archivo y el handoff.
2. Revisar branch, último commit y `git status`.
3. Inspeccionar el código actual del issue; no confiar solo en la auditoría.
4. Confirmar con el usuario el issue o sprint que se trabajará.

Durante la implementación:

1. Conservar cambios locales ajenos.
2. Mantener el alcance en el issue confirmado.
3. Usar el version catalog para dependencias.
4. Añadir pruebas proporcionales al riesgo.
5. No imprimir ni copiar datos de fixtures reales.

Al cerrar:

1. Ejecutar build, tests relevantes y lint cuando aplique.
2. Reportar compilación, tests, conformidad visual y bloqueadores.
3. Hacer commits separados y claros solo para el alcance acordado.
4. No avanzar al siguiente sprint sin revisión del usuario.

## 8. Próximo punto de decisión

El siguiente trabajo debe acordarse con el usuario. El próximo issue pendiente
en el handoff es el #4.
