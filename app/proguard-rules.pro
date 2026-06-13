# Add project specific ProGuard rules here.

# ── Stack traces legibles ─────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Apache POI — solo subsistemas usados por CibaoXlsParser ─────────────────
# CibaoXlsParser usa: WorkbookFactory.create(InputStream), HSSFWorkbook,
# Sheet, Row, CellType, DateUtil. No usa charts, macros, shapes, encryption,
# HSLF (PowerPoint), HWPF (Word), HSMF (Outlook) ni formula functions.
# Mantener solo los paquetes necesarios para abrir y leer celdas XLS/XLSX.
-keep class org.apache.poi.ss.usermodel.** { *; }
-keep class org.apache.poi.hssf.usermodel.** { *; }
-keep class org.apache.poi.hssf.record.** { *; }
-keep class org.apache.poi.xssf.usermodel.** { *; }
-keep class org.apache.poi.openxml4j.** { *; }
-keep class org.apache.poi.poifs.filesystem.** { *; }
-keep class org.apache.poi.util.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn schemasMicrosoftComOfficeExcel.**
-dontwarn schemasMicrosoftComOfficeSpreadsheetml.**

# ── PdfBox-Android (usa reflection para fuentes y codecs) ────────────────────
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ── Google Sign-In — Credential Manager API ──────────────────────────────────
# androidx.credentials y google-identity usan reflection para parsear el token.
# Sin estas reglas R8 elimina GoogleIdTokenCredential → NoClassDefFoundError
# en runtime que no es capturado por catch(Exception) → app queda en Loading.
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ── Firebase (usa reflection para deserializar documentos Firestore) ──────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Firestore DTOs — R8 renombra data classes sin anotaciones @Keep ──────────
# Firestore usa reflection para deserializar documentos en estos DTOs.
# Sin esta regla los campos se renombran y toDomain() produce valores vacíos/null.
-keep class com.example.flowtrack.data.firestore.dto.** { *; }

# ── Hilt (generación de código en tiempo de compilación, safe) ────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**

# ── OpenCSV ───────────────────────────────────────────────────────────────────
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# ── Java AWT (referenciado por POI/graphbuilder pero no existe en Android) ────
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**