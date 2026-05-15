package com.example.flowtrack.domain.model

enum class TipoTransaccion { DEBITO, CREDITO }
enum class Moneda { DOP, USD }
enum class TipoCuenta { CORRIENTE, AHORRO, CREDITO, INVERSION }
enum class TipoDocumento { CUENTA_CORRIENTE, CUENTA_AHORRO, TARJETA_CREDITO, TARJETA_DEBITO, PRESTAMO, INVERSION }
enum class EstadoCarga { EXITOSO, PARCIAL, FALLIDO }
enum class TipoMatch { IGUAL, CONTIENE, EMPIEZA_CON, REGEX }
enum class OrigenTasa { AUTO_EXTRAIDA, MANUAL }
enum class EstadoTarjeta { ACTIVO }

enum class TipoMovimientoTarjeta {
    COMPRA, PAGO, INTERES, COMISION, CASHBACK, AJUSTE, AVANCE_EFECTIVO
}
