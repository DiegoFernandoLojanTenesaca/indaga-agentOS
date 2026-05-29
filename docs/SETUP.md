# AgentOS — Guía de montaje en otra PC

Cómo dejar el proyecto listo para compilar e instalar en un Android, desde cero.

## 1. Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 17 | `java -version` debe decir 17 |
| Android SDK | platform 35 + build-tools 35.0.0 + platform-tools | sin necesidad de Android Studio (sirve solo cmdline-tools) |
| Python (host) | **3.13** | lo usa Chaquopy como `buildPython` (compila el bytecode + pip) |
| Gradle | 8.13 | ya incluido vía `./gradlew` (wrapper) |

> El proyecto **no usa Google Play Services ni Firebase**. minSdk 26 (Android 8), targetSdk 35.

## 2. Instalar el Android SDK por línea de comandos (sin Android Studio)

```bash
SDK=$HOME/android-sdk            # o donde prefieras
mkdir -p "$SDK" && cd "$SDK"
curl -fL -o cli.zip "https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip"
mkdir -p cmdline-tools-tmp && unzip -q cli.zip -d cmdline-tools-tmp
mkdir -p cmdline-tools/latest && mv cmdline-tools-tmp/cmdline-tools/* cmdline-tools/latest/
rm -rf cmdline-tools-tmp cli.zip
export ANDROID_HOME="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"
yes | sdkmanager --sdk_root="$SDK" --licenses
sdkmanager --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## 3. Python 3.13 para Chaquopy (buildPython)

Necesitas un `python3.13` en el PATH del host. Opciones:
- Linux con `/usr/bin/python3.13` ya instalado → nada que hacer.
- `pyenv install 3.13` o `uv python install 3.13`.

Si tu `python3.13` no está en una ruta estándar, ajusta en `app/build.gradle.kts`:
```kotlin
chaquopy { defaultConfig { buildPython("/ruta/a/python3.13") } }
```

## 4. Configurar el proyecto

```bash
git clone git@github.com:DiegoFernandoLojanTenesaca/indaga-agentOS.git
cd indaga-agentOS
echo "sdk.dir=$HOME/android-sdk" > local.properties     # ruta de tu SDK
```

## 5. Compilar e instalar

```bash
export ANDROID_HOME=$HOME/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64       # tu JDK 17
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Instalar en Huawei / EMUI (sin Google)
`adb install` puede ser bloqueado por EMUI. Receta que funciona:
```bash
adb shell settings put global verifier_verify_adb_installs 0
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/a.apk
adb shell pm install -r -i com.android.vending /data/local/tmp/a.apk
adb shell rm /data/local/tmp/a.apk
```

## 6. Uso

1. Abre **AgentOS** → pantalla de bienvenida → **Comenzar**.
2. Pestaña **Config**: pega tu **token de Telegram** (de @BotFather) y, opcional, variables `KEY=VALOR` (OWNER_ID, GROQ_API_KEY, CITY…). **Guardar**.
3. Pestaña **Inicio** → **Iniciar agente**.
4. Escríbele a tu bot por Telegram. El primer chat que escribe queda como **dueño** automáticamente.

## 7. Notas

- Las API keys se guardan **solo en el dispositivo** (`.env` y secretos nunca van al repo).
- Reinstalar el APK (`pm install -r`) **detiene** el agente: hay que tocar **Iniciar** de nuevo.
- El bridge de hardware escucha en `127.0.0.1:8765`. Para depurar desde el PC:
  `adb forward tcp:8765 tcp:8765 && curl localhost:8765/health`
