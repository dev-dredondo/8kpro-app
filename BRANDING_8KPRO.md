# 8K PRO — cambios sobre OwnTV

Fork de [OwnTV](https://github.com/ahXN00/OwnTV) (GPL-3.0) con marca propia, servidor Xtream fijo e inicio de sesión.

## Configuración (sin tocar código)

| Qué | Dónde | Valor actual |
|---|---|---|
| Nombre visible | `app/src/main/res/values/strings.xml` → `app_name` | `8K PRO` |
| Application ID | `app/build.gradle.kts` → `applicationId` | `com.tuempresa.tuapp` |
| Servidor (DNS) | `-Pbrand.serverUrl=http://host:puerto` (el script lo lee de `server_url.txt`) | fuera del repo |
| Clave TMDB | `-Pbrand.tmdbApiKey=…` (el script la lee de `tmdb_key.txt`) | fuera del repo |
| Repo de actualizaciones | `-Pbrand.updateRepo=usuario/repo` (vacío = desactivado) | vacío |

Si tu panel no usa el puerto 80, añade el puerto: `http://tu-dominio:8080`. La DNS y la clave TMDB nunca se guardan en el repositorio (es público).

## 1. Rebranding

- **Nombre**: `app_name` en `:app` sobrescribe el de la librería `core`.
- **Textos con "OwnTV"**: viven en la librería `tv.own.owntv:core`. `tools/brand/gen_brand_overrides.py`
  genera `res/values*/brand_overrides.xml` (≈66 cadenas × 29 idiomas) que las sobrescriben con "8K PRO".
  **Vuelve a ejecutarlo cada vez que subas `owntvCore` en `gradle/libs.versions.toml`:**
  ```
  git clone https://github.com/ahXN00/OwnTV_Core ../OwnTV_Core && (cd ../OwnTV_Core && git checkout core-<versión>)
  python3 tools/brand/gen_brand_overrides.py ../OwnTV_Core "8K PRO"
  ```
- **Gráficos** (provisionales, generados por `tools/brand/render_placeholder_brand.py`; sustitúyelos por los tuyos con el mismo nombre y tamaño):
  - `res/mipmap-*/brand_icon.png` — icono del launcher
  - `res/drawable-xhdpi/brand_banner.png` — banner 320×180 de Android TV
  - `res/drawable-nodpi/brand_mark.png` — logo dentro de la app (`BrandMark`)
  - `res/drawable-nodpi/brand_splash.png` — pantalla de arranque
  - El logotipo de texto (`Wordmark`) se dibuja a partir de `app_name` ("8K" + "PRO" en color de acento).
- **Actualizador**: desactivado mientras `brand.updateRepo` esté vacío (antes descargaba los APK de OwnTV).
- **Acerca de**: eliminada la tarjeta de Telegram de OwnTV; se mantiene la línea del repositorio original como crédito GPL.

No se ha cambiado (a propósito): el `namespace`/paquete Kotlin `tv.own.owntv` (solo interno; Android identifica la app por
`applicationId`), el esquema interno de deep links `owntv://` (lo define `core`) ni los nombres internos de clases.

## 2. Servidor fijo

- `brand/BrandConfig.kt` centraliza `serverUrl`.
- Ajustes › Playlists: sin "Añadir", "Editar" ni "Eliminar"; muestra la cuenta y **Cerrar sesión**. La URL del servidor no se muestra.
- Asistente inicial: eliminados los pasos de añadir lista manual / desde el móvil / de otro perfil.

## 3. Login

- `features/login/LoginScreen.kt` — pantalla TV (D-Pad: ▲▼ entre Usuario, Contraseña y Entrar; OK abre el teclado de ese campo).
- `features/login/XtreamAuthenticator.kt` — `GET {servidor}/player_api.php?username=…&password=…`; valida `user_info.auth`/`status`/`exp_date`.
- `features/login/SessionStore.kt` — DataStore; contraseña cifrada con AES-256-GCM (clave en Android Keystore).
- Flujo: sin sesión → Login. Primer inicio → crea el perfil (nombre = usuario) y la lista "8K PRO" con esas credenciales e importa.
  Siguientes arranques → directo a la app. Nuevo perfil → comparte la misma lista. Volver a entrar con otra cuenta actualiza las credenciales de la lista.

## Pendiente / a tener en cuenta

- Compilar en Android Studio (este entorno no tenía SDK de Android): `./gradlew :app:assembleStandardDebug`.
- Copias de seguridad (Ajustes › Copia de seguridad) pueden seguir restaurando listas de otros servidores.
- La librería `core` guarda las credenciales de la lista en su base de datos Room (como hacía OwnTV).
- **Licencia**: OwnTV es GPL-3.0. Si distribuyes el APK debes publicar el código fuente modificado bajo GPL-3.0 y conservar los avisos de copyright.
