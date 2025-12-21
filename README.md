# game-wallet-service

## Security & Setup

This application uses SSL (HTTPS). For security reasons, the keystore is **not** committed to the repository.

### Generate Development Certificate
Before running the application (locally or via Docker), generate a self-signed certificate:

```bash
keytool -genkeypair -alias wallet -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore src/main/resources/keystore.p12 \
  -validity 3650 -storepass changeit -dname "CN=localhost"