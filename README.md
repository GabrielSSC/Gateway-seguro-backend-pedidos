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
o gateway. A variável fica disponível somente na janela atual. Use o mesmo
terminal em que vai iniciar o gateway:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

Mantenha essa variável definida na mesma janela em que executar o gateway. Não
compartilhe nem versione o valor gerado.

## Executar as aplicações

Pré-requisitos: JDK 17 ou superior e PowerShell no Windows. O repositório contém
dois projetos Maven separados; inicie cada aplicação em um terminal diferente.

1. No primeiro terminal, a partir da raiz do repositório, inicie o backend:

   ```powershell
   .\backend-pedidos\mvnw.cmd -f .\backend-pedidos\pom.xml spring-boot:run
   ```

   O backend fica disponível em `http://localhost:8081`.

2. No segundo terminal, também a partir da raiz, gere a variável secreta e
   inicie o gateway. Execute os comandos na mesma janela:

   ```powershell
   $bytes = New-Object byte[] 32
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
   .\api-gateway\mvnw.cmd -f .\api-gateway\pom.xml spring-boot:run
   ```

   O gateway usa HTTPS em `https://localhost:8082`. O certificado local é
   autoassinado; os exemplos abaixo usam `curl.exe -k` para aceitá-lo apenas
   durante estes testes. Não use `-k` em produção.

## Testes manuais da API

Execute os comandos abaixo em um terceiro terminal PowerShell, a partir da raiz
do repositório. Eles usam `curl.exe` (não o alias `curl` do Windows PowerShell),
os arquivos versionados em `requests/` para os corpos de login e esperam que
ambos os serviços estejam em execução.

### 1. Requisição sem token: `401 Unauthorized`

```powershell
curl.exe -k -i https://localhost:8082/api/pedidos
```

O gateway deve responder `401` com um JSON padronizado.

### 2. Login válido e consulta de pedidos: `200 OK`

Obtenha um token de usuário:

```powershell
$loginUsuario = curl.exe -k -sS -X POST https://localhost:8082/auth/login `
  -H "Content-Type: application/json" `
  -d "@requests/login-user.json" | ConvertFrom-Json
$userToken = $loginUsuario.token
```

Use-o para consultar pedidos:

```powershell
curl.exe -k -i https://localhost:8082/api/pedidos `
  -H "Authorization: Bearer $userToken"
```

O resultado esperado é `200 OK` com a lista de pedidos. O gateway encaminha a
requisição ao backend e inclui `X-Gateway-Authenticated: true`.

### 3. Credenciais inválidas: `401 Unauthorized`

```powershell
curl.exe -k -i -X POST https://localhost:8082/auth/login `
  -H "Content-Type: application/json" `
  -d "@requests/login-errado.json"
```

### 4. Token sem a role necessária: `403 Forbidden`

O token de usuário não tem a role `ADMIN`:

```powershell
curl.exe -k -i https://localhost:8082/api/admin/relatorio `
  -H "Authorization: Bearer $userToken"
```

O resultado esperado é `403 Forbidden`. Para confirmar o acesso autorizado,
faça login como administrador e repita a chamada:

```powershell
$loginAdmin = curl.exe -k -sS -X POST https://localhost:8082/auth/login `
  -H "Content-Type: application/json" `
  -d "@requests/login-admin.json" | ConvertFrom-Json
$adminToken = $loginAdmin.token

curl.exe -k -i https://localhost:8082/api/admin/relatorio `
  -H "Authorization: Bearer $adminToken"
```

O resultado esperado é `200 OK`.

### 5. Assinatura adulterada: `401 Unauthorized`

Altere a primeira parte da assinatura do token sem modificar o restante:

```powershell
$partes = $userToken.Split('.')
$primeiroCaractere = if ($partes[2][0] -eq 'A') { 'B' } else { 'A' }
$tokenAdulterado = "$($partes[0]).$($partes[1]).$primeiroCaractere$($partes[2].Substring(1))"

curl.exe -k -i https://localhost:8082/api/pedidos `
  -H "Authorization: Bearer $tokenAdulterado"
```

O gateway deve rejeitar o token com `401 Unauthorized`.

### 6. Token expirado: `401 Unauthorized`

Os tokens emitidos pelo gateway expiram após 15 minutos. Faça login para obter
um token novo, aguarde pelo menos 15 minutos e então consulte um endpoint
protegido usando o mesmo `$userToken`:

```powershell
curl.exe -k -i https://localhost:8082/api/pedidos `
  -H "Authorization: Bearer $userToken"
```

O resultado esperado é `401 Unauthorized`.

### 7. Excesso de requisições: `429 Too Many Requests`

O limite é de 10 requisições por minuto e o contador de usuários autenticados é
separado do contador de chamadas sem token. Aguarde 60 segundos sem consultar
`/api/**` com o usuário `user`, depois execute a sequência:

```powershell
1..11 | ForEach-Object {
  curl.exe -k -sS -o NUL -w "%{http_code}`n" `
    https://localhost:8082/api/pedidos `
    -H "Authorization: Bearer $userToken"
}
```

As primeiras 10 respostas devem ser `200`; a 11ª deve ser `429` e incluir o
cabeçalho `Retry-After` e o JSON padronizado. O rate limit usa memória local do
processo; reiniciar o gateway também reinicia os contadores.

### 8. Backend sem cabeçalho de confiança: `403 Forbidden`

O backend não deve aceitar acesso direto sem o cabeçalho injetado pelo gateway:

```powershell
curl.exe -i http://localhost:8081/api/pedidos
```

O resultado esperado é `403 Forbidden`.

## Formato das respostas de erro

Os erros do gateway usam um JSON padronizado com `timestamp`, `status`,
`error`, `message` e `path`. Respostas `401`, `403` e `429` usam mensagens
seguras; erros inesperados retornam uma mensagem genérica, sem stack trace ou
detalhes internos.

```json
{
  "timestamp": "2026-10-27T12:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Autenticacao necessaria.",
  "path": "/api/pedidos"
}
```
