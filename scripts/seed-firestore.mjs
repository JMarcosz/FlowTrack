/**
 * Siembra las 3 colecciones públicas en Firestore.
 *
 * Prerequisitos:
 *   1. En Firebase Console → Configuración del proyecto → Cuentas de servicio
 *      → Generar nueva clave privada → guardar como scripts/serviceAccount.json
 *   2. npm install firebase-admin  (desde la raíz del proyecto)
 *   3. node scripts/seed-firestore.mjs
 *
 * Seguridad: serviceAccount.json está en .gitignore — nunca lo commitees.
 */

import { readFileSync } from 'fs';
import { initializeApp, cert } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

// ── Inicialización ─────────────────────────────────────────────────────────

const serviceAccount = JSON.parse(
  readFileSync(new URL('./serviceAccount.json', import.meta.url))
);

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

// ── Datos de seeding ──────────────────────────────────────────────────────

const bancos = [
  {
    id: 'BANRESERVAS',
    codigo: 'BANRESERVAS',
    nombre: 'BanReservas',
    nombreCompleto: 'Banco de Reservas de la República Dominicana',
    colorPrimario: '#005DA8',
    pais: 'DO',
    monedasSoportadas: ['DOP', 'USD'],
    tiposCuenta: ['CORRIENTE', 'AHORRO'],
    tieneParser: true,
    activo: true,
  },
  {
    id: 'POPULAR',
    codigo: 'POPULAR',
    nombre: 'Banco Popular',
    nombreCompleto: 'Banco Popular Dominicano',
    colorPrimario: '#005CAA',
    pais: 'DO',
    monedasSoportadas: ['DOP', 'USD'],
    tiposCuenta: ['CORRIENTE', 'AHORRO', 'CREDITO'],
    tieneParser: true,
    activo: true,
  },
  {
    id: 'QIK',
    codigo: 'QIK',
    nombre: 'Qik',
    nombreCompleto: 'Qik Banco Digital Dominicano',
    colorPrimario: '#0099E5',
    pais: 'DO',
    monedasSoportadas: ['DOP'],
    tiposCuenta: ['AHORRO', 'CREDITO'],
    tieneParser: true,
    activo: true,
  },
  {
    id: 'CIBAO',
    codigo: 'CIBAO',
    nombre: 'Asociación Cibao',
    nombreCompleto: 'Asociación Cibao de Ahorros y Préstamos',
    colorPrimario: '#E30613',
    pais: 'DO',
    monedasSoportadas: ['DOP', 'USD'],
    tiposCuenta: ['AHORRO', 'CREDITO'],
    tieneParser: true,
    activo: true,
  },
  {
    id: 'BHD',
    codigo: 'BHD',
    nombre: 'BHD',
    nombreCompleto: 'Banco Múltiple BHD',
    colorPrimario: '#003F7F',
    pais: 'DO',
    monedasSoportadas: ['DOP', 'USD'],
    tiposCuenta: ['CORRIENTE', 'AHORRO', 'CREDITO'],
    tieneParser: true,
    activo: true,
  },
];

const categorias = [
  { id: 'alimentacion',          nombre: 'Alimentación',             icono: 'ti-shopping-cart',   color: '#E24B4A', tipo: 'GASTO' },
  { id: 'transporte',            nombre: 'Transporte',               icono: 'ti-car',             color: '#378ADD', tipo: 'GASTO' },
  { id: 'salud',                 nombre: 'Salud',                    icono: 'ti-heart',           color: '#D4537E', tipo: 'GASTO' },
  { id: 'entretenimiento',       nombre: 'Entretenimiento',          icono: 'ti-movie',           color: '#7F77DD', tipo: 'GASTO' },
  { id: 'suscripciones',         nombre: 'Suscripciones',            icono: 'ti-repeat',          color: '#EF9F27', tipo: 'GASTO' },
  { id: 'servicios',             nombre: 'Servicios',                icono: 'ti-bolt',            color: '#BA7517', tipo: 'GASTO' },
  { id: 'compras',               nombre: 'Compras',                  icono: 'ti-bag',             color: '#D85A30', tipo: 'GASTO' },
  { id: 'atm',                   nombre: 'Retiro ATM',               icono: 'ti-cash',            color: '#888780', tipo: 'GASTO' },
  { id: 'transferencia_enviada', nombre: 'Transferencia enviada',    icono: 'ti-arrow-up-right',  color: '#5F5E5A', tipo: 'GASTO' },
  { id: 'impuestos',             nombre: 'Impuestos',                icono: 'ti-receipt',         color: '#444441', tipo: 'GASTO' },
  { id: 'intereses_comisiones',  nombre: 'Intereses y comisiones',   icono: 'ti-percentage',      color: '#993C1D', tipo: 'GASTO' },
  { id: 'salario',               nombre: 'Salario',                  icono: 'ti-briefcase',       color: '#3B6D11', tipo: 'INGRESO' },
  { id: 'transferencia_recibida',nombre: 'Transferencia recibida',   icono: 'ti-arrow-down-left', color: '#1D9E75', tipo: 'INGRESO' },
  { id: 'deposito',              nombre: 'Depósito',                 icono: 'ti-coin',            color: '#639922', tipo: 'INGRESO' },
  { id: 'cashback',              nombre: 'Cashback',                 icono: 'ti-gift',            color: '#97C459', tipo: 'INGRESO' },
  { id: 'pago_tarjeta',          nombre: 'Pago a tarjeta',           icono: 'ti-credit-card',     color: '#0F6E56', tipo: 'TRANSFERENCIA_INTERNA' },
  { id: 'sin_categorizar',       nombre: 'Sin categorizar',          icono: 'ti-question-mark',   color: '#B4B2A9', tipo: 'INDEFINIDO' },
];

const reglasGlobales = [
  { patron: 'UBER',           tipoMatch: 'CONTIENE', categoriaId: 'transporte',             prioridad: 100 },
  { patron: 'DIDI',           tipoMatch: 'CONTIENE', categoriaId: 'transporte',             prioridad: 100 },
  { patron: 'OPRET METRO',    tipoMatch: 'CONTIENE', categoriaId: 'transporte',             prioridad: 100 },
  { patron: 'PEDIDOSYA',      tipoMatch: 'CONTIENE', categoriaId: 'alimentacion',           prioridad: 100 },
  { patron: 'HELADOS BON',    tipoMatch: 'CONTIENE', categoriaId: 'alimentacion',           prioridad: 100 },
  { patron: 'CHUCK E CHEESE', tipoMatch: 'CONTIENE', categoriaId: 'entretenimiento',        prioridad: 100 },
  { patron: 'NETFLIX',        tipoMatch: 'CONTIENE', categoriaId: 'suscripciones',          prioridad: 100 },
  { patron: 'SPOTIFY',        tipoMatch: 'CONTIENE', categoriaId: 'suscripciones',          prioridad: 100 },
  { patron: 'GOOGLE',         tipoMatch: 'CONTIENE', categoriaId: 'suscripciones',          prioridad: 90  },
  { patron: 'CRUNCHYROLL',    tipoMatch: 'CONTIENE', categoriaId: 'suscripciones',          prioridad: 100 },
  { patron: 'CONSUMO POS',    tipoMatch: 'CONTIENE', categoriaId: 'compras',                prioridad: 50  },
  { patron: 'RETIRO ATM',     tipoMatch: 'CONTIENE', categoriaId: 'atm',                    prioridad: 100 },
  { patron: 'RETIRO SAB',     tipoMatch: 'CONTIENE', categoriaId: 'atm',                    prioridad: 100 },
  { patron: 'COBRO IMP',      tipoMatch: 'CONTIENE', categoriaId: 'impuestos',              prioridad: 100 },
  { patron: 'DGII',           tipoMatch: 'CONTIENE', categoriaId: 'impuestos',              prioridad: 100 },
  { patron: 'INTERES',        tipoMatch: 'CONTIENE', categoriaId: 'intereses_comisiones',   prioridad: 100 },
  { patron: 'COMISION',       tipoMatch: 'CONTIENE', categoriaId: 'intereses_comisiones',   prioridad: 100 },
  { patron: 'NOMINAS ACH',    tipoMatch: 'CONTIENE', categoriaId: 'salario',                prioridad: 100 },
  { patron: 'TRANSFERENCIA',  tipoMatch: 'CONTIENE', categoriaId: 'transferencia_recibida', prioridad: 50  },
  { patron: 'DEPOSITO',       tipoMatch: 'CONTIENE', categoriaId: 'deposito',               prioridad: 100 },
  { patron: 'CASHBACK',       tipoMatch: 'CONTIENE', categoriaId: 'cashback',               prioridad: 100 },
  { patron: 'REBATE',         tipoMatch: 'CONTIENE', categoriaId: 'cashback',               prioridad: 100 },
  { patron: 'PAGO A TARJETA', tipoMatch: 'CONTIENE', categoriaId: 'pago_tarjeta',           prioridad: 100 },
];

// ── Helpers ────────────────────────────────────────────────────────────────

async function seedCollection(collectionName, docs, idField = 'id') {
  console.log(`\nSembrando ${collectionName} (${docs.length} documentos)...`);
  const batch = db.batch();
  for (const doc of docs) {
    const { [idField]: docId, ...data } = doc;
    const ref = db.collection(collectionName).doc(docId);
    batch.set(ref, {
      ...data,
      activa: true,
      creadoPor: 'SISTEMA',
      version: 1,
    });
  }
  await batch.commit();
  console.log(`  ✓ ${collectionName} sembrado`);
}

// ── Ejecución ──────────────────────────────────────────────────────────────

async function main() {
  console.log('Iniciando seeding de Firestore...');

  await seedCollection('catalogoBancos', bancos);
  await seedCollection('catalogoCategorias', categorias);
  await seedCollection('reglasCategorizacionGlobales',
    reglasGlobales.map((r, i) => ({ id: `regla_${String(i + 1).padStart(3, '0')}`, ...r }))
  );

  console.log('\n✅ Seeding completo. Verifica los datos en Firebase Console.');
  process.exit(0);
}

main().catch((err) => {
  console.error('❌ Error durante el seeding:', err);
  process.exit(1);
});
