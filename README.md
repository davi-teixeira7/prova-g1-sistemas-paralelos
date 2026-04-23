# Sistema de Leilao Distribuido em Java

Este projeto implementa um leilao em tempo real com arquitetura cliente-servidor, usando TCP para clientes, UDP para replicacao simples entre servidores locais, threads para conexoes concorrentes, autenticacao manual em JSON e criptografia AES na camada da aplicacao.

## Funcionalidades

* **Servidor primario:** gerencia o estado do leilao, valida lances, atende clientes por TCP e replica eventos para replicas por UDP.
* **Servidores replica:** recebem eventos do primario via UDP cifrado e espelham o estado do leilao em memoria.
* **Cliente de leilao:** autentica usuario, envia lances e monitora atualizacoes em tempo real.
* **Concorrencia:** usa sincronizacao para aceitar apenas o maior lance.
* **Persistencia:** grava o historico completo em `src/data/leilao.log` ao final do leilao.
* **Autenticacao:** salva usuarios em `src/data/users.json`.
* **Criptografia:** cifra todas as mensagens TCP e UDP com AES.

## Estrutura do Projeto

* `src/AuctionServer.java`: servidor capaz de subir como primario ou replica.
* `src/AuctionClient.java`: cliente que se conecta ao primario.
* `src/auth/AuthService.java`: cadastro e autenticacao manual de usuarios no arquivo JSON.
* `src/security/CryptoService.java`: cifra e decifra mensagens com AES.
* `src/storage/AppPaths.java`: resolve a pasta `src/data` tanto da raiz quanto de `./src`.
* `src/data/users.json`: usuarios salvos em JSON simples.
* `src/data/leilao.log`: historico textual do leilao.
* `compile-all.ps1`: compila automaticamente todos os arquivos `.java` dentro de `src`.
* `start-local-test.ps1`: compila o projeto e abre as janelas da replica 1, replica 2 e primario.

## Pre-requisitos

* Java JDK 11 ou superior.
* Permissao de leitura e escrita em `src/data`.

## Rede Local

O sistema esta configurado apenas para localhost:

* TCP do primario: `127.0.0.1:8080`
* UDP da replica 1: `127.0.0.1:9091`
* UDP da replica 2: `127.0.0.1:9092`

## Como Executar

Compile na raiz do projeto:

```bash
javac src/storage/AppPaths.java src/auth/AuthService.java src/security/CryptoService.java src/AuctionServer.java src/AuctionClient.java
```

Ou use o script de compilacao automatica:

```powershell
powershell -ExecutionPolicy Bypass -File .\compile-all.ps1
```

### Script para abrir o ambiente local

Se quiser abrir rapidamente as replicas e o primario em janelas separadas:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local-test.ps1
```

Esse script:

* compila o projeto
* abre a replica 1
* abre a replica 2
* abre o primario

Depois disso, abra o cliente manualmente com:

```bash
java -cp src AuctionClient
```

### 1. Subir as replicas

Na raiz:

```bash
java -cp src AuctionServer replica 1
java -cp src AuctionServer replica 2
```

Se estiver dentro de `./src`:

```bash
java AuctionServer replica 1
java AuctionServer replica 2
```

### 2. Subir o primario

Na raiz:

```bash
java -cp src AuctionServer
```

ou:

```bash
java -cp src AuctionServer primary
```

Se estiver dentro de `./src`:

```bash
java AuctionServer
```

O primario pede o nome do item, escuta clientes em TCP e envia atualizacoes para as replicas em UDP.

### 3. Subir o cliente

Na raiz:

```bash
java -cp src AuctionClient
```

Se estiver dentro de `./src`:

```bash
java AuctionClient
```

O cliente escolhe entre entrar em conta existente ou cadastrar nova conta. Depois disso entra no leilao.

### 4. Encerrar o leilao

Digite `encerrar` no terminal do primario.

Ao encerrar:

* o primario notifica os clientes com vencedor e valor final;
* as replicas recebem o fechamento via UDP;
* o historico completo e gravado em `src/data/leilao.log`.

## Protocolo de Comunicacao

As mensagens logicas continuam em texto, mas trafegam cifradas com AES.

Mensagens TCP entre cliente e primario:

* `AUTH_MENU`
* `AUTH_REQUIRED`
* `AUTH_RETRY`
* `REGISTER_REQUIRED`
* `REGISTER_OK`
* `REGISTER_FAIL:...`
* `LOGIN_OK:item:valorAtual`
* `NOVO_LANCE:usuario:valor`
* `ENCERRADO: Vencedor: usuario | Produto: item | Total: R$ valor`
* `SISTEMA: ...`

Payload logico UDP entre primario e replicas:

* `REPL|OPEN|...`
* `REPL|BID|...`
* `REPL|CLOSE|...`

## Observacoes

* Todo leilao e publico para usuarios autenticados.
* A autenticacao e da conta do usuario, nao do leilao.
* As replicas existem para demonstrar coordenacao distribuida e nao aceitam clientes.
* A criptografia usa AES com chave fixa hardcoded, apenas para demonstrar o conceito na atividade.
* O sistema foi mantido o mais simples possivel para fins de prova e defesa.
