# FlowTrack - Gemini CLI Foundational Mandates

> **ESTE ARCHIVO ES LA FUENTE DE VERDAD PARA EL AGENTE.** Define las reglas no negociables, la arquitectura y los estándares de ingeniería de FlowTrack.

## 1. Contexto del Proyecto
FlowTrack es una aplicación Android nativa diseñada para consolidar, visualizar y analizar transacciones bancarias de República Dominicana.
- **Funcionalidad Principal**: Importación local de estados de cuenta (PDF, CSV, XLS), persistencia en Firestore y visualización en dashboards interactivos.
- **Arquitectura**: Clean Architecture (ligera) + MVVM. Módulo único `:app`.

## 2. Fuentes de Verdad
1. **Plan Maestro V2**: `docs/01-analisis/03_PLAN_MAESTRO_V2.md` (Verdad funcional y técnica).
2. **Design System**: `docs/02-desing-system/04_DESIGN_SYSTEM.md` (Verdad visual - nota el typo en la carpeta `desing`).
3. **Reglas Operativas**: `AGENTS.md` (Instrucciones heredadas para agentes).

## 3. Estándares de Ingeniería

### Lenguaje y Nomenclatura
- **Dominio (Español)**: `Transaccion`, `Cuenta`, `Banco`, `Presupuesto`, `Meta`.
- **Términos Técnicos (Inglés)**: `Repository`, `UseCase`, `ViewModel`, `Mapper`, `DTO`.
- **Comentarios**: Siempre en español.

### Tipado y Datos
- **Dinero**: Usar **SIEMPRE** `BigDecimal`. Prohibido el uso de `Double` o `Float` para montos financieros.
- **Fechas**: Usar `Instant` para timestamps, con zona horaria `America/Santo_Domingo`.
- **Parsing**: El procesamiento de archivos debe ser estrictamente **LOCAL** y en hilos de fondo. Los archivos nunca deben subirse a la nube.

### Interfaz de Usuario (UI)
- **Jetpack Compose**: Uso exclusivo de Compose con Material 3.
- **Fidelidad**: Adherencia estricta a los tokens y componentes definidos en el Design System.
- **Accesibilidad**: Cifras tabulares (`fontFeatureSettings = "tnum"`) para todos los montos.

## 4. Seguridad y Privacidad (Crítico)
- **Fixtures Sensibles**: Los archivos en `docs/03-fixtures/` contienen datos bancarios reales.
    - **NUNCA** los commitees (están en `.gitignore`).
    - **NUNCA** pegues su contenido en el chat.
    - **NUNCA** los subas a servicios externos.
- **Credenciales**: Protege `app/google-services.json` y cualquier `.env`.

## 5. Flujo de Trabajo y Toolchain

### Comandos de Verificación (Windows)
> **Importante**: Antes de ejecutar Gradle, configura el JBR de Android Studio:
> `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

```powershell
.\gradlew.bat assembleDebug         # Compilar APK debug (requiere google-services.json)
.\gradlew.bat testDebugUnitTest     # Tests unitarios JVM
.\gradlew.bat connectedDebugAndroidTest # Tests instrumentados (requiere device/emulador)
.\gradlew.bat lint                  # Análisis estático
```

### Reglas de Ejecución
1. **Branch por Sprint**: Trabaja en ramas `sprint-N-titulo`.
2. **Surgical Edits**: Cambios enfocados, sin refactorizaciones innecesarias.
3. **google-services.json**: Este archivo es obligatorio para compilar. Si falta, solicítalo al usuario.
4. **Validación**: Cada cambio debe ser verificado con tests unitarios y, si afecta a parsers de PDF, tests instrumentados.
5. **No Inventar**: Si el Plan Maestro no especifica algo, pregunta antes de asumir.

## 6. Stack Tecnológico Fijo
- **Kotlin**: 2.0.21
- **UI**: Jetpack Compose + Navigation Compose
- **DI**: Hilt (Dagger)
- **Backend**: Firebase Auth + Firestore (Sin Cloud Functions ni Storage)
- **Parsers**: PdfBox-Android, OpenCSV, Apache POI HSSF
- **Exportación**: fastexcel
- **Min SDK**: 34 | **Target SDK**: 36
