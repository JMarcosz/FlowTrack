# Análisis Arquitectónico y Plan de Refactorización

## 1. Hallazgos en la Auditoría (Fases 1 y 2)

He completado la auditoría inicial de las capas `domain` y `data`. Se identificaron violaciones a los principios de Clean Architecture que comprometen la mantenibilidad y escalabilidad del proyecto, enfocándonos principalmente en la capa de persistencia y la inversión de dependencias.

### 🔴 A. Violación de la Inversión de Dependencias (Domain Layer)
* **El problema:** No existen interfaces de repositorios en la capa de dominio.
* **Impacto:** Los Casos de Uso y ViewModels están inyectando directamente las implementaciones concretas de la capa de datos.
* **Solución (Acordada):** Crear interfaces con el prefijo `I` en `domain/repository/` (ej. `ITransaccionRepository`). Las implementaciones concretas en la capa de datos mantendrán su nombre actual (ej. `TransaccionRepository`) e implementarán estas interfaces. 

*(Nota: Por decisión arquitectónica del proyecto, se permite el uso de dependencias del framework de Android, como `Context` y `Uri`, en la capa de dominio, dado que el enfoque es 100% Android y se cuenta con un entorno de testing compatible. Esto evita sobreingeniería).*

### 🟡 B. Ausencia del Patrón Data Source en la Capa de Datos
* **El problema:** Los repositorios concretos inyectan directamente `FirebaseFirestore` y `OfflineStore`.
* **Impacto:** El repositorio actúa como orquestador y como origen de datos simultáneamente.
* **Solución (Acordada):** Refactorización **Incremental**. Se implementará el patrón de `RemoteDataSource` y `LocalDataSource` exclusivamente para las entidades críticas (Transaccion, Cuenta, Tarjeta) en la primera etapa, dejando el resto para futuras iteraciones.

### 🔴 C. Fugas de Implementación en la Capa de Presentación
* **El problema:** ViewModels importan directamente repositorios concretos y detalles de SQLite (como `TransaccionesCursor`).
* **Impacto:** La UI está fuertemente acoplada a las bases de datos.
* **Solución (Acordada):** 
  1. **Comunicación UI-Dominio Pragmática:** Los ViewModels inyectarán interfaces (`ITransaccionRepository`) para lecturas y CRUD simple, usando Casos de Uso solo para lógica compleja.
  2. **Paginación:** Migrar a **Paging 3** de AndroidX. La UI consumirá `Flow<PagingData<T>>`, eliminando por completo el conocimiento de cursores locales o referencias de Firebase.

## 2. Plan de Acción Recomendado (Roadmap de Refactorización)

Para corregir estas deficiencias sin romper el flujo de la aplicación, el enfoque será iterativo:

### Fase 1: Inversión de Dependencias y Paging 3 (Core & Domain)
1. ✅ Añadir dependencias de Paging 3 al `build.gradle.kts`.
2. ✅ Crear el paquete `domain/repository`.
3. ✅ Extraer las interfaces con prefijo `I` (ej. `ITransaccionRepository`) de los repositorios Core actuales (Cuenta, Tarjeta, Transaccion).
4. ✅ Crear un módulo en Dagger Hilt (`RepositoryBindingsModule`) para hacer el `@Binds` de cada `IRepository` hacia su implementación concreta.
5. ⏳ **Pendiente:** Migrar `ITransaccionRepository` a firmas reactivas (`Flow<PagingData<T>>`) y actualizar los ViewModels/UseCases.

### Fase 2: Paginación y Desacoplamiento en Capa de Datos
1. Modificar `TransaccionRepository` para que implemente `ITransaccionRepository`.
2. Implementar un `PagingSource` local basado en el `OfflineStore` para alimentar el flujo de Paging 3.
3. Actualizar `TransaccionesViewModel` para consumir el `PagingData` y usar `collectAsLazyPagingItems()` en Compose, eliminando `TransaccionesCursor`.

### Fase 3: Patrón Data Source Incremental
1. Para las entidades Core (Cuenta, Tarjeta, Transaccion), crear `ILocalDataSource` y `IRemoteDataSource` (y sus implementaciones).
2. Refactorizar los repositorios Core para que orquesten los Data Sources en lugar de inyectar Firebase/OfflineStore directamente.

---
*Este análisis sienta las bases para una arquitectura mantenible y preparada para futuras migraciones de backend, respetando un enfoque pragmático para Android.*