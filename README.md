# Gateway seguro de pedidos

Projeto final: um **API Gateway** com Spring Boot e Spring Security que centraliza
a autenticação, a autorização e a proteção de um microsserviço interno de pedidos.

O repositório contém dois projetos Maven independentes:

| Projeto | Porta | Papel |
|---|---|---|
| `api-gateway` | `8082` (HTTPS) | Único ponto de entrada. Valida o JWT, aplica RBAC, rate limiting e cabeçalhos de segurança, e encaminha a requisição ao backend. |
| `backend-pedidos` | `8081` (HTTP) | Serviço interno simplificado (`GET /api/pedidos`). Só aceita requisições com o cabeçalho de confiança `X-Gateway-Authenticated: true`. |

```
Cliente --HTTPS--> api-gateway (:8082) --HTTP + X-Gateway-Authenticated--> backend-pedidos (:8081)
```

## Pré-requisitos

- JDK 17 ou superior
- PowerShell no Windows (os exemplos usam `curl.exe`, que já vem com o Windows 10/11)

Execute **todos os comandos a partir da raiz do repositório** (a pasta que contém
`api-gateway`, `backend-pedidos` e `requests`).

## Certificado HTTPS de desenvolvimento

O gateway carrega `api-gateway/src/main/resources/keystore.p12` (PKCS#12, alias
`gateway`). Esse arquivo contém uma chave privada e **não é versionado**. Cada
desenvolvedor gera o seu, com um certificado autoassinado, válido apenas para
desenvolvimento local:

```powershell
keytool -genkeypair -alias gateway -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore api-gateway/src/main/resources/keystore.p12 -validity 365 -storepass changeit -keypass changeit -dname "CN=localhost"
```

A senha padrão de desenvolvimento é `changeit`. Para usar outra, defina a variável
de ambiente `KEYSTORE_PASSWORD` com o mesmo valor usado na geração do arquivo.
Como o certificado é autoassinado, os clientes de teste precisam aceitá-lo
explicitamente (por isso os exemplos usam `curl.exe -k`).

## Segredo JWT

O gateway exige a variável de ambiente `JWT_SECRET`. **Não existe segredo padrão
no código**: sem a variável, a aplicação não inicia. Os comandos para gerar uma
chave aleatória de 32 bytes estão no passo 2 abaixo.

Observações importantes:

- A variável vale somente na janela do PowerShell em que foi criada. Gere o
  segredo e inicie o gateway **na mesma janela**.
- Cada vez que o gateway é reiniciado com um novo `JWT_SECRET`, **os tokens
  emitidos antes deixam de ser válidos**. Faça login de novo depois de reiniciar.
- Nunca versione nem compartilhe o valor gerado.

## Executar as aplicações

Use um terminal para cada aplicação.

1. **Terminal 1: backend.**

   ```powershell
   .\backend-pedidos\mvnw.cmd -f .\backend-pedidos\pom.xml spring-boot:run
   ```

   O backend fica disponível em `http://localhost:8081`.

2. **Terminal 2: gateway.** Gere o segredo e inicie o gateway na mesma janela:

   ```powershell
   $bytes = New-Object byte[] 32
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
   .\api-gateway\mvnw.cmd -f .\api-gateway\pom.xml spring-boot:run
   ```

   Aguarde a mensagem `Started ApiGatewayApplication`. O gateway usa HTTPS em
   `https://localhost:8082`.

3. **Terminal 3: testes.** Use-o para os comandos da próxima seção.

## Usuários de teste

Os usuários são fixos no código, apenas para fins didáticos:

| Usuário | Senha | Roles |
|---|---|---|
| `user` | `user123` | `USER` |
| `admin` | `admin123` | `ADMIN`, `USER` |

Os corpos JSON dos logins ficam em `requests/` (`login-user.json`,
`login-admin.json` e `login-errado.json`). Usá-los com `-d "@requests/..."`
evita problemas de aspas no PowerShell.

## Testes manuais da API

Execute no terceiro terminal, a partir da raiz do repositório, com os dois
serviços em execução. Os exemplos usam `curl.exe` (não o alias `curl` do
PowerShell).

### 1. Requisição sem token: `401 Unauthorized`

```powershell
curl.exe -k -i https://localhost:8082/api/pedidos
```

O gateway responde `401` com um JSON padronizado.

### 2. Login válido e consulta de pedidos: `200 OK`

```powershell
$loginUsuario = curl.exe -k -sS -X POST https://localhost:8082/auth/login `
  -H "Content-Type: application/json" `
  -d "@requests/login-user.json" | ConvertFrom-Json
$userToken = $loginUsuario.token

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

O token de `user` não tem a role `ADMIN`:

```powershell
curl.exe -k -i https://localhost:8082/api/admin/relatorio `
  -H "Authorization: Bearer $userToken"
```

O resultado esperado é `403 Forbidden`. Para confirmar o acesso autorizado, faça
login como administrador e repita a chamada:

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

Altera o primeiro caractere da assinatura do token, sem modificar o restante:

```powershell
$partes = $userToken.Split('.')
$primeiroCaractere = if ($partes[2][0] -eq 'A') { 'B' } else { 'A' }
$tokenAdulterado = "$($partes[0]).$($partes[1]).$primeiroCaractere$($partes[2].Substring(1))"

curl.exe -k -i https://localhost:8082/api/pedidos `
  -H "Authorization: Bearer $tokenAdulterado"
```

O gateway rejeita o token com `401 Unauthorized`.

### 6. Excesso de requisições: `429 Too Many Requests`

O limite é de 10 requisições por minuto. O contador de usuários autenticados é
separado do contador de chamadas sem token (que usa o IP). Aguarde 60 segundos
sem consultar `/api/**` com o usuário `user` e execute:

```powershell
1..11 | ForEach-Object {
  curl.exe -k -sS -o NUL -w "%{http_code}`n" `
    https://localhost:8082/api/pedidos `
    -H "Authorization: Bearer $userToken"
}
```

As 10 primeiras respostas devem ser `200`. A 11ª deve ser `429`, com o cabeçalho
`Retry-After` e o JSON padronizado. Para ver a resposta completa:

```powershell
curl.exe -k -i https://localhost:8082/api/pedidos `
  -H "Authorization: Bearer $userToken"
```

### 7. Corpo da requisição inválido: `400 Bad Request`

Um JSON malformado é erro do cliente e retorna `400` (não `500`):

```powershell
curl.exe -k -i -X POST https://localhost:8082/auth/login `
  -H "Content-Type: application/json" `
  --data-raw '{username:admin}'
```

### 8. Método não permitido: `405 Method Not Allowed`

O gateway só encaminha requisições `GET` em `/api/**`:

```powershell
curl.exe -k -i -X POST https://localhost:8082/api/pedidos `
  -H "Authorization: Bearer $adminToken"
```

### 9. Backend sem cabeçalho de confiança: `403 Forbidden`

O backend não aceita acesso direto sem o cabeçalho injetado pelo gateway:

```powershell
curl.exe -i http://localhost:8081/api/pedidos
```

O resultado esperado é `403 Forbidden`. Com o cabeçalho, o backend responde
normalmente, o que mostra que a proteção depende de ele não ficar exposto
diretamente aos clientes:

```powershell
curl.exe -i http://localhost:8081/api/pedidos -H "X-Gateway-Authenticated: true"
```

### 10. Token expirado: `401 Unauthorized`

Por padrão os tokens expiram em 15 minutos. Para demonstrar a expiração sem
esperar tanto, reduza o prazo para 1 minuto com a variável de ambiente
`JWT_EXPIRATION_MINUTES` (ela sobrescreve a propriedade `jwt.expiration-minutes`).

**a)** No terminal do gateway, pare a aplicação (`Ctrl + C`) e reinicie com o
prazo reduzido, na mesma janela:

```powershell
$env:JWT_EXPIRATION_MINUTES = "1"
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
.\api-gateway\mvnw.cmd -f .\api-gateway\pom.xml spring-boot:run
```

**b)** No terminal de testes, faça login e consulte logo em seguida (`200`), depois
espere e consulte de novo com o mesmo token (`401`):

```powershell
$resp = curl.exe -k -sS -X POST https://localhost:8082/auth/login `
  -H "Content-Type: application/json" -d "@requests/login-user.json"
$tokenCurto = ($resp | ConvertFrom-Json).token

# Dentro do prazo: 200 OK
curl.exe -k -i https://localhost:8082/api/pedidos -H "Authorization: Bearer $tokenCurto"

Start-Sleep -Seconds 70

# Depois de expirar: 401 Unauthorized
curl.exe -k -i https://localhost:8082/api/pedidos -H "Authorization: Bearer $tokenCurto"
```

**c)** Ao terminar, volte ao padrão: pare o gateway, rode
`Remove-Item Env:JWT_EXPIRATION_MINUTES` e reinicie normalmente (passo 2). Como o
segredo muda a cada reinício, será preciso fazer login de novo.

## Formato das respostas de erro

Todos os erros do gateway usam o mesmo JSON, com `timestamp`, `status`, `error`,
`message` e `path`. As mensagens são genéricas e seguras: erros inesperados
retornam uma mensagem padrão, sem stack trace, versão de framework ou detalhes
da infraestrutura. A causa real fica apenas no log do servidor.

```json
{
  "timestamp": "2026-10-27T12:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Autenticacao necessaria.",
  "path": "/api/pedidos"
}
```

| Status | Quando acontece | Quem trata |
|---|---|---|
| `400` | Corpo da requisição malformado | `GlobalExceptionHandler` |
| `401` | Sem token, token inválido, adulterado ou expirado, ou credenciais inválidas | `AuthenticationEntryPoint` / `AuthController` |
| `403` | Token válido, mas sem a role exigida | `AccessDeniedHandler` |
| `405` | Método HTTP não permitido na rota | `GlobalExceptionHandler` |
| `429` | Limite de requisições excedido | `RateLimitFilter` |
| `502` | Backend de pedidos indisponível | `ProxyController` |
| `500` | Erro inesperado | `GlobalExceptionHandler` |

Os erros de autenticação (`401`), autorização (`403`) e limite de taxa (`429`)
acontecem em filtros de segurança, antes de a requisição chegar ao Spring MVC.
Por isso um `@RestControllerAdvice` sozinho não os captura, e cada um desses
pontos usa o mesmo formato de resposta JSON.

## Justificativa técnica das decisões de segurança

- **JWT validado no gateway (assinatura, `exp` e `iss`):** o gateway é o único
  ponto de entrada, então centraliza a autenticação. Tokens adulterados,
  expirados ou emitidos por outro emissor são rejeitados com `401`. A assinatura
  usa uma chave simétrica (HMAC) lida de variável de ambiente.
- **RBAC por roles:** as rotas `/api/admin/**` exigem `ADMIN`; as demais rotas
  `/api/**` exigem apenas autenticação. Tudo o que não foi liberado
  explicitamente é negado (`denyAll`). A role vem do claim `roles` do token.
- **CSRF desabilitado e sessão stateless:** o CSRF explora sessões e cookies que
  o navegador envia sozinho. Como a API não usa sessão e o token vai no
  cabeçalho `Authorization`, que o navegador não envia automaticamente a outros
  sites, esse vetor não se aplica.
- **XSS:** o cabeçalho `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
  impede o carregamento de qualquer recurso a partir das respostas da API, que
  só devolve JSON. O `X-XSS-Protection` fica em `0` de propósito: o filtro XSS
  embutido nos navegadores antigos foi removido por ter falhas, e a defesa atual
  recomendada é o CSP. Também são enviados `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY` e `Referrer-Policy: no-referrer`.
- **Rate limiting (10 requisições por minuto):** a chave é o usuário do token
  ou, sem token, o IP, o que também limita tentativas de login em massa. Ao
  exceder, o gateway responde `429` com `Retry-After`, sem consultar o backend.
- **HTTPS e HSTS:** o gateway só responde por HTTPS, exige canal seguro em todas
  as rotas e envia `Strict-Transport-Security` por 1 ano, incluindo subdomínios.
- **Erros padronizados e genéricos:** todos os erros usam o mesmo JSON e mensagens
  que não revelam detalhes internos. A resposta de login inválido não diz se
  foi o usuário ou a senha que falhou, para não permitir descobrir usuários.
- **Cabeçalho de confiança e cabeçalhos do cliente:** o gateway não copia os
  cabeçalhos da requisição do cliente para o backend; ele apenas adiciona
  `X-Gateway-Authenticated: true` depois de validar o token. Assim, o cliente
  não consegue forjar esse cabeçalho passando pelo gateway.
- **Segredos fora do código:** o `JWT_SECRET` vem de variável de ambiente e não
  há valor padrão. O keystore não é versionado.

## Limitações conhecidas

Estas simplificações são conscientes e não devem ser levadas para produção:

- **Rate limit em memória:** o contador é local ao processo. Com várias instâncias
  do gateway, cada uma teria o seu; em produção seria compartilhado (por exemplo,
  Redis). Reiniciar o gateway zera os contadores.
- **Cabeçalho de confiança previsível:** `X-Gateway-Authenticated: true` só protege
  se o backend estiver em rede privada, inacessível diretamente aos clientes.
  Em produção, usaria um segredo compartilhado ou mTLS.
- **Tráfego interno sem TLS:** a comunicação do gateway com o backend usa HTTP,
  por se supor uma rede interna. Em produção, seria cifrada.
- **Usuários fixos no código:** `admin` e `user` existem só para demonstração.
  Um sistema real consultaria um repositório de usuários com senhas protegidas
  por hash.
- **Certificado autoassinado e senha `changeit`:** servem apenas para o ambiente
  de desenvolvimento.
