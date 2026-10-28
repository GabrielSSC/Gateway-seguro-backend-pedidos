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

## Segredo JWT de desenvolvimento

O gateway exige a variável de ambiente `JWT_SECRET`; não há um segredo padrão
no projeto. Gere uma chave aleatória de 32 bytes no PowerShell antes de iniciar
o gateway. A variável fica disponível somente na janela atual:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

Mantenha essa variável definida na mesma janela em que executar o gateway. Não
compartilhe nem versione o valor gerado. Os testes usam uma chave exclusiva de
teste e não dependem dessa variável.
