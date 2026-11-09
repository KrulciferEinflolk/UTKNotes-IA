# Plan de Implementación: Integración de Gemini API (Firebase AI Logic) [COMPLETADO]

## 1. Contexto y Objetivos
- [x] **Resolución del Conflicto OAuth**: Actualizado el `applicationId` a `utk.notes.gemini` en `app/build.gradle.kts` para permitir el registro limpio en el nuevo proyecto de Google Cloud `gen-lang-client-0399724194`.
- [x] **Aprovisionamiento de Firebase AI Logic**: Invocado el RPC `ProvisionFirebase` con `ai_logic`, registrando el certificado de depuración y generando el nuevo `google-services.json` con hash de certificado verificado.
- [x] **Configuración de App Check**: Implementado el helper `configureAppCheck` y conectado en `MainActivity.onCreate()` antes de Compose y ViewModels, pasando el `intent` con `FIREBASE_APPCHECK_DEBUG_TOKEN`.
- [x] **Integración del Modelo Gemini**: Habilitado `gemini-3.5-flash-lite` mediante `Firebase.ai`, permitiendo que el usuario pueda utilizar tanto la IA local offline como la potencia y ultra-velocidad de Gemini en la nube.

---

## 2. Pasos de Ejecución Completados

### Paso 1: Actualización de `applicationId`
- [x] En `app/build.gradle.kts`:
  Cambiado `applicationId = "utk.notes.ia"` por `applicationId = "utk.notes.gemini"`.

### Paso 2: Aprovisionamiento de Firebase AI Logic (`ProvisionFirebase`)
- [x] Invocado `fax.ProvisionFirebase` con:
  - `platform`: `"android"`
  - `package_name`: `"utk.notes.gemini"`
  - `project_id`: `"gen-lang-client-0399724194"`
  - `sha1_certificate`: `"0C:CF:C0:5E:AC:EC:49:FA:CC:1F:2F:2D:8B:98:B2:55:A0:69:7E:3C"`
  - `sha256_certificate`: `"F4:FE:5E:05:59:B8:D1:EA:6D:97:97:D9:D9:48:D7:FE:D2:DC:2B:9A:C4:49:CC:68:98:52:BB:D6:74:F1:18:A8"`
  - `requested_products`: `["ai_logic"]`
- [x] Verificado que `0ccfc05eacec49facc1f2f2d8b98b255a0697e3c` está registrado en `app/google-services.json`.

### Paso 3: Dependencias de Gradle
- [x] En `app/build.gradle.kts`, dependencias activas:
  ```kotlin
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  ```

### Paso 4: Implementación de App Check y Cableado en `MainActivity`
- [x] Creado `com.example.util.AppCheckConfig.kt` con persistencia síncrona mediante `.commit()` y soporte reCAPTCHA Enterprise.
- [x] Conectado `configureAppCheck(this, intent)` al inicio de `MainActivity.onCreate()`.

### Paso 5: Implementación de Servicio Gemini Cloud (`gemini-3.5-flash-lite`)
- [x] Creado `CloudGeminiManager.kt` con inicialización `by lazy` de `Firebase.ai.generativeModel("gemini-3.5-flash-lite")`.
- [x] Soporte unario y de streaming con timeouts de 60 segundos y manejo multimodal (imágenes y documentos).
- [x] Integrado en `AetherViewModel.kt` como motor activo por defecto (`AiEngineType.GEMINI_CLOUD`), conservando `AURA_LOCAL` como respaldo sin conexión.
- [x] Conmutador interactivo añadido en el encabezado de `ChatbotUI` y en `AuraModelSelectorCard.kt`.

---

## 3. Verificación
1. [x] **Huella Digital Verificada**: `certificate_hash` presente en `google-services.json`.
2. [x] **Compilación Exitosa**: `compile_applet` ejecutado exitosamente sin errores de compilación ni dependencias.
3. [x] **App lista para ejecución en Streaming Emulator**.
