import { getApp, getApps, initializeApp } from "firebase-admin/app";
import { getAppCheck } from "firebase-admin/app-check";
import { getAuth } from "firebase-admin/auth";
import { Firestore, getFirestore } from "firebase-admin/firestore";

function ensureApp() {
  return getApps().length > 0 ? getApp() : initializeApp();
}

export function firebaseApp() {
  return ensureApp();
}

export function firestore(): Firestore {
  return getFirestore(ensureApp());
}

export function appCheck() {
  return getAppCheck(ensureApp());
}

export function firebaseAuth() {
  return getAuth(ensureApp());
}
