# Tracking de Migración: Modo Oscuro (Capa de Presentación)

## Contexto
Durante la implementación del Modo Oscuro se detectó que la capa de presentación no reacciona al cambio de tema. Esto se debe a que la interfaz utiliza variables estáticas de color (`BgScreen`, `Ink`, `Primary`, etc.) importadas directamente desde `com.example.flowtrack.ui.theme.*` en lugar de abstraerlos mediante `MaterialTheme.colorScheme.*`.

Para lograr que la aplicación reaccione al cambio de modo claro/oscuro, es necesario reemplazar estas importaciones estáticas por llamadas semánticas dinámicas en todos los componentes y pantallas detectadas.

## Estrategia de Reemplazo (Mapeo)
| Color Estático Actual | Componente Material equivalente |
| :--- | :--- |
| `BgScreen` | `MaterialTheme.colorScheme.background` |
| `BgCard` | `MaterialTheme.colorScheme.surface` |
| `Ink`, `Ink2` | `MaterialTheme.colorScheme.onSurface` / `onBackground` |
| `Muted`, `Muted2` | `MaterialTheme.colorScheme.onSurfaceVariant` |
| `Primary`, `Primary600` | `MaterialTheme.colorScheme.primary` |
| `Primary50`, `Primary100` | `MaterialTheme.colorScheme.primaryContainer` |
| `Line`, `Line2` | `MaterialTheme.colorScheme.surfaceVariant` |
| `Success` | `MaterialTheme.colorScheme.secondary` |
| `Expense` | `MaterialTheme.colorScheme.error` |

*Nota: Los colores de los bancos (`BancoPopular`, etc.) o de las categorías pueden permanecer estáticos si no deben cambiar de matiz en modo oscuro.*

---

## Tareas Pendientes (Issues)

### Componentes Globales
- [ ] `app/src/main/java/com/example/flowtrack/presentation/components/BankRegistry.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/components/FinanzasSwitch.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/components/FlowTrackDrawer.kt`

### Pantallas Principales
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/dashboard/DashboardScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/historial/HistorialScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/presupuestos/PresupuestosScreen.kt`

### Configuración y Ajustes
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/configuracion/ConfiguracionScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/bancos/BancosYCuentasScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/perfil/PerfilScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/reglas/ReglasScreen.kt`

### Herramientas y Utilidades
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/conversor/ConversorScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/duplicados/DuplicadosScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/exportar/ExportarScreen.kt`
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/notificaciones/NotificacionesScreen.kt`

### Casos Especiales
- [ ] `app/src/main/java/com/example/flowtrack/presentation/screens/login/LoginScreen.kt` *(Requiere revisión: Su diseño originalmente fuerza un fondo oscuro, por lo que podría mantener variables estáticas específicas si no debe mutar en modo claro).*

---
**Instrucciones de Ejecución:**
A medida que vayamos modificando los archivos, iremos marcando los checkboxes de este documento y verificando en el proyecto para asegurar una transición estable y gradual sin afectar compilaciones.