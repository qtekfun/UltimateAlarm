# Paso 0 — Claves de firma (lo ejecutas tú, no Claude Code)

Claude Code **nunca** genera, lee ni mueve material de claves. Solo declara los nombres de los secrets que usa el workflow.

> Si UltimateGallery/UltimatePhone usan otros nombres de secrets, usa esos mismos aquí y en `03-ci-release.md` para mantener la familia homogénea.

## 1. Generar el keystore

```bash
mkdir -p ~/keys && chmod 700 ~/keys

# Contraseña sin dejarla en el historial (PKCS12: misma para store y key)
read -rs -p "Contraseña del keystore UltimateAlarm: " UA_PASS; echo

keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore ~/keys/ultimatealarm-release.jks \
  -alias ultimatealarm \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storepass "$UA_PASS" -keypass "$UA_PASS" \
  -dname "CN=Cipriano Moreno Mateos, OU=qtekfun, O=qtekfun, L=Madrid, C=ES"

chmod 600 ~/keys/ultimatealarm-release.jks
```

## 2. Comprobar y anotar la huella (para F-Droid / verificación)

```bash
keytool -list -v -keystore ~/keys/ultimatealarm-release.jks \
  -alias ultimatealarm -storepass "$UA_PASS" | grep -E "SHA256|Valid"
```

## 3. Subir los secrets al repo

```bash
REPO=qtekfun/UltimateAlarm

base64 -w0 ~/keys/ultimatealarm-release.jks | gh secret set RELEASE_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$UA_PASS"   | gh secret set RELEASE_KEYSTORE_PASSWORD -R "$REPO"
printf '%s' "ultimatealarm" | gh secret set RELEASE_KEY_ALIAS -R "$REPO"
printf '%s' "$UA_PASS"   | gh secret set RELEASE_KEY_PASSWORD -R "$REPO"

unset UA_PASS
gh secret list -R "$REPO"
```

## 4. Copia de seguridad

Guarda `ultimatealarm-release.jks` y su contraseña fuera del portátil (gestor de contraseñas + copia cifrada). Si pierdes la clave no podrás actualizar la app instalada ni en F-Droid.
