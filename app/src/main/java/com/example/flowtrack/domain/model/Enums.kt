package com.example.flowtrack.domain.model

enum class TipoTransaccion { DEBITO, CREDITO }
enum class Moneda { DOP, USD }
enum class TipoCuenta { CORRIENTE, AHORRO, CREDITO, INVERSION }
enum class TipoDocumento { CUENTA_CORRIENTE, CUENTA_AHORRO, TARJETA_CREDITO, TARJETA_DEBITO, PRESTAMO, INVERSION }
enum class EstadoCarga { EXITOSO, PARCIAL, FALLIDO, ELIMINADO }
enum class TipoMatch { EXACTO, CONTIENE, EMPIEZA_CON, REGEX }
enum class OrigenTasa { AUTO_EXTRAIDA, MANUAL }
enum class EstadoTarjeta { ACTIVO, INACTIVO }
enum class OrigenTransaccion { IMPORTACION_ARCHIVO, INGESTA_GMAIL, MANUAL }
enum class EstadoTransaccion { APROBADA, RECHAZADA, DUPLICADA, PENDIENTE }

enum class TipoMovimientoTarjeta {
    COMPRA, PAGO, INTERES, COMISION, CASHBACK, AJUSTE, AVANCE_EFECTIVO, DEVOLUCION
}

/** Tipo de producto financiero que produce movimientos. */
enum class ProductoTipo { CUENTA, TARJETA }

/** Formato de archivo de entrada soportado por los parsers. */
enum class FileFormat { PDF, CSV, XLS, XLSX }
