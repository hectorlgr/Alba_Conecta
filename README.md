# Alba Conecta 🤝

Aplicación Android **100% local** desarrollada en **Kotlin + Jetpack Compose** para el curso
**DSY1105 – Desarrollo de Aplicaciones Móviles**, en el contexto de la labor de
[CORPALIV](https://corpaliv.cl/) (*"Comprometidos con la discapacidad múltiple"*).

Alba Conecta busca visibilizar iniciativas de inclusión social y laboral, informar sobre
el impacto de la fundación y ofrecer una vía sencilla para que personas y organizaciones
manifiesten su interés en colaborar. La aplicación no requiere backend ni conexión:
todos los datos se guardan localmente en el dispositivo.

## Integrantes

- [Hector Gutierrez] 
- [Braulio Gomez]  

## Funcionalidades

| Pantalla | Descripción |
|----------|-------------|
| **Inicio** | Bienvenida, propósito de la fundación y las áreas de la aplicación |
| **Impacto** | Iniciativas de inclusión social, inclusión laboral y trabajo con la comunidad |
| **Talento Alba** | Desarrollo de habilidades, oportunidades inclusivas y vínculo con organizaciones |
| **Colaborar** | Formulario con validaciones por campo: texto, fecha preferida, cantidad de trabajadores (si representa a una empresa), autorización, foto adjunta (cámara) y dictado por voz |
| **Iniciar sesión** | Acceso con correo y contraseña, validaciones visuales y retroalimentación de errores |
| **Registro** | Creación de cuenta: nombre, apellido, RUT, correo, teléfono, fecha de nacimiento, contraseña cifrada y aceptación de términos |
| **Perfil** | Datos del usuario con sesión iniciada y cierre de sesión |

### Reglas de negocio

- RUT chileno con dígito verificador (ej. `12.345.678-9`).
- Teléfono `+56` de 9 dígitos.
- Registro solo para mayores de edad.
- Una cuenta por dispositivo.
- Correo normalizado (sin espacios, en minúsculas).

## Recursos nativos del dispositivo

- **Cámara**: foto adjunta en el formulario "Colaborar" mediante
  `ActivityResultContracts.TakePicture` + `FileProvider`. No se declara el permiso
  `CAMERA`: la captura se delega en la app de cámara del sistema (acceso seguro).
- **Reconocimiento de voz**: dictado del mensaje mediante `RecognizerIntent` (`es-CL`).
  No se declara el permiso `RECORD_AUDIO`: el reconocimiento se delega en otra app.

## Arquitectura

- **Patrón MVVM**: `ViewModel` que expone `StateFlow` inmutables; la UI colecciona el
  estado con `collectAsStateWithLifecycle()`.
- **Capas**:
  - `ui/screens/` — pantallas Compose.
  - `ui/components/` — componentes reutilizables (`MainScaffold`, `AnimatedEntrance`,
    `PulsableButton`, `FieldStateIcons`).
  - `viewmodel/` — estados y lógica de presentación.
  - `validation/` — validadores puros, testeables sin Android.
  - `data/repository/` — acceso a datos (`UserRepository`).
  - `data/local/` — Room (`AlbaDatabase`, `UserDao`) y DataStore (`SessionDataStore`).
  - `navigation/` — grafo de navegación (`NavHost`).
- **Persistencia local**: usuarios en **Room** (contraseñas cifradas con
  **PBKDF2WithHmacSHA1**, 120 000 iteraciones) y sesión en **DataStore**.
- La contraseña nunca se guarda ni se registra en claro.

## Tecnologías

- Kotlin 2.2.10 · Jetpack Compose (BOM 2026.02.01) · Material 3
- Room 2.8.5 (KSP) · DataStore 1.2.1 · Navigation Compose 2.10.2
- minSdk 24 · targetSdk 37

## Ejecución

Requisitos: [Android Studio](https://developer.android.com/studio) con Android SDK (API 37).
El JDK 25 que usa el daemon de Gradle se provisiona automáticamente la primera vez
(requiere internet solo en esa ocasión).

1. Clonar el repositorio:

   ```bash
   git clone https://github.com/hectorlgr/Alba_Conecta.git
   cd Alba_Conecta
   ```

2. Opción A — abrir el proyecto en Android Studio y presionar **Run** sobre un
   emulador o dispositivo físico.

3. Opción B — compilar desde la terminal:

   ```bash
   sh gradlew :app:assembleDebug
   ```

   El APK queda en `app/build/outputs/apk/debug/app-debug.apk`, listo para instalar
   (requiere Android 7.0 / API 24 o superior).

## Tests

Tests unitarios de validadores y cifrado (JUnit 4, sin instrumentación):

```bash
sh gradlew :app:testDebugUnitTest
```

Incluye `RegisterValidatorTest`, `LoginValidatorTest`, `ContactValidatorTest` y
`PasswordHasherTest`.
