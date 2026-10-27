# Gateway seguro de pedidos

O repositório contém o gateway (`api-gateway`) e o serviço de pedidos
(`backend-pedidos`).

## Certificado HTTPS de desenvolvimento

O gateway está configurado para carregar `api-gateway/src/main/resources/keystore.p12`
como um keystore PKCS#12, com o alias `gateway`. Esse arquivo deve conter
somente um certificado autoassinado para desenvolvimento local; não o utilize
em produção.

O keystore contém uma chave privada, portanto não o versione nem o compartilhe.
Cada desenvolvedor pode gerar seu próprio arquivo local com o JDK:

```powershell
keytool -genkeypair -alias gateway -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore api-gateway/src/main/resources/keystore.p12 -validity 365 -storepass changeit -keypass changeit -dname "CN=localhost"
```

O comando usa a senha de desenvolvimento `changeit`. Mantenha a senha
configurada localmente para coincidir com a senha do keystore. Clientes de teste
precisam confiar explicitamente no certificado autoassinado.
