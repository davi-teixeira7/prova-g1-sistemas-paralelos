ATIVIDADE DE LEILAO DISTRIBUIDO
================================

1. ENUNCIADO DA ATIVIDADE
-------------------------

Foi solicitado o desenvolvimento de uma plataforma distribuida de leiloes eletronicos em que multiplos servidores coordenam o processo de licitacao e atualizam os participantes em tempo real.

Os requisitos pedidos pela atividade foram:

- comunicacao via sockets TCP/UDP
- gerenciamento de threads para conexoes concorrentes
- servidor capaz de:
  - gerenciar o estado do leilao
  - cadastrar item de leilao
  - receber e armazenar lances identificando o autor
  - informar aos compradores o lance atual e quem enviou
  - encerrar o leilao e notificar o vencedor e o valor pago
  - validar lances maiores que o anterior
  - notificar todos os participantes sobre novos lances
- cliente capaz de:
  - conectar ao servidor
  - enviar lances
  - monitorar o leilao em tempo real
- persistencia:
  - registro historico completo do leilao em arquivo
- bonus de seguranca:
  - autenticacao de participantes
  - criptografia das comunicacoes


2. VISAO GERAL DA SOLUCAO IMPLEMENTADA
--------------------------------------

A aplicacao foi implementada de forma propositalmente simples para fins de prova e defesa, mas cobrindo os conceitos centrais pedidos na atividade.

A arquitetura ficou assim:

- 1 servidor primario
- 2 servidores replica
- 1 ou mais clientes

Papeis de cada componente:

- o servidor primario recebe clientes, autentica usuarios, aceita lances, decide o maior lance valido, notifica os clientes e grava o historico final
- os servidores replica nao recebem clientes; eles apenas recebem atualizacoes do primario por UDP e espelham o estado do leilao em memoria
- os clientes se conectam somente ao primario por TCP, entram com usuario e senha, acompanham o leilao e enviam lances

Protocolos usados:

- TCP entre cliente e servidor primario
- UDP entre servidor primario e servidores replica

Seguranca usada:

- autenticacao simples por usuario e senha
- criptografia AES na camada da aplicacao
- todas as mensagens que trafegam pela rede sao cifradas antes do envio e decifradas no recebimento

Persistencia usada:

- `src/data/users.json` guarda as contas dos usuarios
- `src/data/leilao.log` guarda o historico completo do leilao ao final


3. FUNCIONAMENTO COMPLETO DA APLICACAO
--------------------------------------

3.1. Inicializacao do sistema

O sistema pode ser executado em tres tipos de processo:

- replica 1
- replica 2
- primario

As replicas sao iniciadas primeiro. Cada replica abre um socket UDP local e fica ouvindo atualizacoes vindas do primario.

Depois disso o primario e iniciado. Ao iniciar:

- o usuario do terminal informa o nome do item que sera leiloado
- o primario inicializa o servico de autenticacao
- o primario garante que os arquivos de dados existem
- o primario abre o socket TCP na porta `8080`
- o primario prepara os destinos UDP das replicas
- o primario registra internamente o evento de abertura do leilao
- o primario replica esse evento de abertura para as replicas

Assim, logo no comeco do sistema, as replicas ja sabem:

- qual item esta sendo leiloado
- qual e o valor inicial
- quem e o maior ganhador atual
- se o leilao esta ativo

3.2. Diferenca entre primario e replicas

O mesmo arquivo `AuctionServer.java` executa os dois papeis.

Se o programa for iniciado como primario:

- ele abre um `ServerSocket`
- aceita conexoes TCP de clientes
- cria threads para cada cliente
- gerencia o leilao real
- grava persistencia em arquivo

Se o programa for iniciado como replica:

- ele abre um `DatagramSocket`
- nao recebe clientes
- nao grava usuarios nem log
- apenas recebe os eventos replicados do primario
- atualiza o estado local em memoria
- mostra no terminal o estado sincronizado

Essa divisao demonstra a parte distribuida da atividade com o menor nivel de complexidade possivel.

3.3. Fluxo de conexao do cliente

Quando o cliente e iniciado:

- ele abre um socket TCP com `127.0.0.1:8080`
- ele espera comandos do primario
- todos os comandos chegam cifrados
- o cliente descriptografa cada mensagem recebida antes de interpretar

O servidor inicia o fluxo enviando:

- `AUTH_MENU`

Entao o cliente escolhe:

- `1` para entrar em uma conta existente
- `2` para criar uma nova conta

3.4. Fluxo de autenticacao

Se o usuario escolher entrar:

- o servidor envia `AUTH_REQUIRED`
- o cliente envia usuario
- o cliente envia senha
- o servidor consulta o `AuthService`
- se o usuario e a senha baterem com o que esta salvo em `users.json`, a autenticacao e aceita
- se nao bater, o servidor permite nova tentativa com `AUTH_RETRY`
- apos 3 tentativas falhas, o servidor envia `ACESSO_NEGADO`

Se o usuario escolher cadastrar:

- o servidor envia `REGISTER_REQUIRED`
- o cliente envia o novo nome de usuario
- o cliente envia a senha
- o `AuthService` verifica se o nome ja existe
- se nao existir, ele salva o usuario e a senha no `users.json`
- o servidor envia `REGISTER_OK`
- o usuario entra imediatamente no leilao

3.5. Entrada no leilao

Depois que o cliente se autentica com sucesso:

- o servidor envia `LOGIN_OK:item:valorAtual`
- o cliente mostra na tela:
  - nome do produto
  - lance atual

Nesse momento o cliente cria uma thread secundaria para monitoramento em tempo real.

Essa thread secundaria fica ouvindo continuamente as mensagens vindas do servidor, enquanto a thread principal continua livre para o usuario digitar novos lances.

Isso atende o requisito de painel em tempo real e tambem demonstra o uso de concorrencia no lado do cliente.

3.6. Envio de lances

Quando o usuario digita um valor:

- o cliente normaliza o texto
- o cliente cifra a mensagem com AES
- o cliente envia a mensagem pelo socket TCP

No servidor:

- a thread do cliente recebe a linha
- o servidor descriptografa a mensagem
- o servidor chama `processBid`

Dentro de `processBid`, o fluxo e:

1. verificar se o leilao ainda esta ativo
2. converter o texto recebido para numero
3. comparar com o lance atual
4. se o valor for maior:
   - atualizar `currentBid`
   - atualizar `highestBidder`
   - registrar evento `ACEITO`
   - replicar o evento para as replicas via UDP
   - notificar todos os clientes sobre o novo lance
5. se o valor nao for maior:
   - registrar evento `NEGADO`
   - replicar o evento para as replicas
   - responder ao cliente que o lance foi negado
6. se o valor nem puder ser convertido:
   - registrar evento `INVALIDO`
   - replicar o evento para as replicas
   - responder ao cliente que o valor e invalido

3.7. Notificacao em tempo real

Quando um lance e aceito:

- o primario envia `NOVO_LANCE:usuario:valor` para todos os clientes conectados
- os clientes recebem a mensagem na thread de monitoramento
- os clientes mostram o novo estado imediatamente na tela

Isso garante que todos os participantes acompanhem o leilao em tempo real.

3.8. Replicacao entre servidores

Sempre que acontece um evento importante, o primario replica o estado para as replicas.

Eventos replicados:

- abertura do leilao
- lance aceito
- lance negado
- lance invalido
- encerramento

O payload logico de replicacao contem:

- tipo do evento
- status do evento
- usuario relacionado
- produto
- valor
- timestamp
- maior ganhador atual
- valor atual do leilao
- se o leilao ainda esta ativo
- descricao textual do evento

Fluxo de replicacao:

- o primario monta o payload textual
- o primario cifra esse payload com AES
- o primario envia o datagrama UDP para cada replica
- cada replica recebe o datagrama
- cada replica descriptografa a mensagem
- cada replica reconstrui o estado local em memoria
- cada replica imprime no console o estado recebido

Com isso, o sistema demonstra o uso conjunto de:

- TCP para operacoes confiaveis com clientes
- UDP para sincronizacao simples entre servidores

3.9. Encerramento do leilao

Quando o operador do primario digita `encerrar` no terminal:

- o servidor muda o estado para inativo
- registra o evento `ENCERRADO`
- replica o encerramento para as replicas
- imprime o vencedor e o valor final no terminal do primario
- notifica todos os clientes conectados com a mensagem final
- grava o historico completo no arquivo `leilao.log`
- fecha os sockets e finaliza o processo

As replicas, ao receberem o evento `CLOSE`:

- atualizam o estado final
- imprimem que o leilao foi encerrado
- fecham o socket UDP
- encerram a execucao

3.10. Persistencia do historico

Durante o leilao, o historico fica em cache de memoria na lista `bidHistory`.

Cada evento armazenado guarda:

- timestamp
- produto
- usuario
- valor
- status
- descricao textual

Ao final do leilao:

- o servidor percorre todos os eventos em memoria
- gera linhas formatadas de texto
- grava tudo em `src/data/leilao.log`

Isso atende o requisito do enunciado que diz que o registro historico pode ser feito ao final do leilao.

3.11. Persistencia dos usuarios

As contas dos usuarios nao ficam no servidor em memoria temporaria apenas.

Elas ficam gravadas em:

- `src/data/users.json`

Cada usuario e salvo com:

- `username`
- `password`

O objetivo aqui foi simplificar a demonstracao do conceito de autenticacao para a prova, sem depender de banco SQL nem de bibliotecas externas.

3.12. Criptografia das comunicacoes

A criptografia esta implementada na camada da aplicacao com AES.

Isso significa que:

- antes de enviar uma mensagem, o programa cifra o texto
- ao receber, o programa descriptografa o conteudo

Isso foi aplicado em:

- autenticacao
- cadastro
- envio de lances
- notificacoes do servidor para os clientes
- replicacao UDP entre primario e replicas

Ou seja, o trafego nao circula em texto puro nos sockets da aplicacao.


4. EXPLICACAO DETALHADA POR MODULO
----------------------------------

4.1. `src/AuctionServer.java`

Este e o modulo principal do lado do servidor. Ele concentra a maior parte da logica distribuida do trabalho.

Responsabilidades principais:

- decidir se o processo sobe como primario ou replica
- abrir os sockets necessarios
- manter o estado do leilao
- aceitar conexoes concorrentes de clientes
- validar lances
- identificar o autor de cada lance
- notificar clientes
- replicar eventos para replicas
- encerrar o leilao
- gravar o log final

Partes mais importantes dentro dele:

- `configureRole`
  - interpreta os argumentos de execucao
  - define se o processo sera primario ou replica

- `runPrimary`
  - inicializa o leilao principal
  - abre o `ServerSocket`
  - prepara a replicacao UDP
  - registra a abertura do leilao
  - aceita clientes em loop

- `runReplica`
  - inicia o servidor replica
  - abre o `DatagramSocket`
  - recebe mensagens do primario
  - atualiza o estado espelhado

- `processBid`
  - valida o lance recebido
  - atualiza o estado do leilao
  - registra eventos aceitos, negados ou invalidos
  - dispara notificacoes e replicacao

- `broadcast`
  - envia atualizacoes para todos os clientes conectados

- `encerrarLeilao`
  - finaliza oficialmente o leilao
  - notifica clientes e replicas
  - grava o historico

- `registerAuctionEvent`
  - cria e adiciona um evento na lista em memoria

- `persistAuctionHistory`
  - grava o historico no `leilao.log`

- `ClientHandler`
  - representa a thread individual de cada cliente
  - faz autenticacao
  - processa as mensagens daquele cliente

- `BidEvent`
  - representa cada evento do historico do leilao
  - sabe converter o evento para linha de log
  - sabe converter o evento para payload de replicacao

4.2. `src/AuctionClient.java`

Este modulo representa o participante do leilao.

Responsabilidades principais:

- abrir conexao TCP com o primario
- participar do fluxo de autenticacao
- enviar usuario e senha
- receber o estado inicial do leilao
- permitir o envio de lances
- acompanhar o leilao em tempo real
- cifrar e decifrar as mensagens da rede

Partes principais:

- conexao inicial com `Socket`
- `sendSecure`
  - cifra e envia qualquer mensagem para o servidor
- `readSecure`
  - recebe e descriptografa as respostas do servidor
- thread de monitoramento
  - fica ouvindo notificacoes enquanto o usuario ainda digita lances

Esse modulo cobre a parte de cliente pedida na atividade.

4.3. `src/auth/AuthService.java`

Este modulo e o servico de autenticacao da aplicacao.

Ele e o modulo citado diretamente para a parte de auth.

Responsabilidades principais:

- garantir que o arquivo `users.json` exista
- cadastrar novos usuarios
- impedir usuario duplicado
- autenticar usuario e senha
- salvar e ler os dados do arquivo JSON

Metodos principais:

- `initialize`
  - prepara o arquivo de usuarios

- `registerUser`
  - valida nome e senha
  - verifica se o usuario ja existe
  - adiciona o novo usuario ao JSON

- `authenticate`
  - procura o usuario salvo
  - compara usuario e senha

- `loadUsers`
  - le o `users.json`
  - converte o conteudo em lista de usuarios

- `saveUsers`
  - escreve a lista de usuarios no JSON

Em resumo:

- toda a parte de cadastro e login de usuarios esta centralizada aqui

4.4. `src/security/CryptoService.java`

Este modulo e o servico de criptografia.

Responsabilidades principais:

- criptografar mensagens antes do envio
- descriptografar mensagens no recebimento
- manter a chave AES fixa usada pela aplicacao

Metodos principais:

- `encrypt`
  - recebe texto puro
  - cifra com AES
  - converte o resultado para Base64

- `decrypt`
  - recebe a mensagem cifrada em Base64
  - reconstrui os bytes
  - descriptografa para texto puro

Esse modulo cobre a parte do bonus relacionada a criptografia das comunicacoes.

4.5. `src/storage/AppPaths.java`

Este modulo e um utilitario de infraestrutura para caminhos de arquivos.

Responsabilidades principais:

- localizar corretamente a pasta `src/data`
- funcionar tanto executando pela raiz do projeto quanto dentro de `./src`
- garantir a existencia de `users.json`
- garantir a existencia de `leilao.log`

Isso evita erro de caminho relativo e garante que a persistencia funcione em qualquer uma das duas formas de execucao usadas no projeto.


5. RELACAO ENTRE MODULOS E REQUISITOS DA ATIVIDADE
--------------------------------------------------

5.1. Requisito tecnico: comunicacao via sockets TCP/UDP

Atendido por:

- `src/AuctionServer.java`
  - `ServerSocket` para clientes TCP
  - `DatagramSocket` para replicacao UDP
- `src/AuctionClient.java`
  - `Socket` para conexao TCP com o primario

Como ficou implementado:

- TCP foi usado entre cliente e servidor primario
- UDP foi usado entre servidor primario e servidores replica

5.2. Requisito tecnico: gerenciamento de threads para conexoes concorrentes

Atendido por:

- `src/AuctionServer.java`
  - criacao de `new Thread(new ClientHandler(socket)).start()` para cada cliente
  - thread separada para ouvir o comando `encerrar`
- `src/AuctionClient.java`
  - thread separada para monitoramento em tempo real

Como ficou implementado:

- cada cliente conectado ao primario e atendido em sua propria thread
- o cliente consegue ouvir atualizacoes enquanto o usuario ainda digita

5.3. Servidor: gerenciar o estado do leilao

Atendido por:

- `src/AuctionServer.java`

Estado mantido em:

- `itemName`
- `currentBid`
- `highestBidder`
- `isActive`

5.4. Servidor: cadastrar item de leilao

Atendido por:

- `src/AuctionServer.java`

Como ficou implementado:

- o operador informa o nome do item no inicio do primario
- esse valor passa a ser o item oficial do leilao

5.5. Servidor: receber e armazenar os lances identificando o autor

Atendido por:

- `src/AuctionServer.java`
  - `ClientHandler`
  - `processBid`
  - `registerAuctionEvent`
  - `BidEvent`

Como ficou implementado:

- o servidor sabe qual usuario esta associado a cada thread de cliente
- cada lance recebido gera um evento com usuario, valor, produto, status e descricao
- os eventos ficam na lista `bidHistory`

5.6. Servidor: informar os compradores sobre o lance atual e quem enviou

Atendido por:

- `src/AuctionServer.java`
  - `broadcast`
- `src/AuctionClient.java`
  - thread de monitoramento

Como ficou implementado:

- ao aceitar um lance, o servidor envia `NOVO_LANCE:usuario:valor` para todos
- os clientes mostram isso em tempo real

5.7. Servidor: encerrar o leilao e notificar vencedor e valor pago

Atendido por:

- `src/AuctionServer.java`
  - `encerrarLeilao`
- `src/AuctionClient.java`
  - tratamento da mensagem `ENCERRADO`

Como ficou implementado:

- o primario envia a mensagem final para todos os clientes
- as replicas recebem tambem o fechamento por UDP

5.8. Servidor: validar lances maiores que o anterior

Atendido por:

- `src/AuctionServer.java`
  - `processBid`

Como ficou implementado:

- o servidor so aceita se `amount > currentBid`

5.9. Servidor: notificar todos os participantes sobre novos lances

Atendido por:

- `src/AuctionServer.java`
  - `broadcast`
- `src/AuctionClient.java`
  - thread de leitura continua

5.10. Cliente: conexao com servidor

Atendido por:

- `src/AuctionClient.java`

Como ficou implementado:

- cliente abre `Socket` TCP para `127.0.0.1:8080`

5.11. Cliente: interface para enviar lances

Atendido por:

- `src/AuctionClient.java`

Como ficou implementado:

- interface de console
- usuario digita o valor e o cliente envia ao servidor

5.12. Cliente: painel de monitoramento em tempo real

Atendido por:

- `src/AuctionClient.java`

Como ficou implementado:

- thread separada fica ouvindo notificacoes do servidor continuamente

5.13. Persistencia: registro historico completo do leilao em arquivo

Atendido por:

- `src/AuctionServer.java`
  - `bidHistory`
  - `persistAuctionHistory`
  - `BidEvent`
- `src/storage/AppPaths.java`
  - localizacao do arquivo de log

Como ficou implementado:

- o historico fica em memoria durante o leilao
- ao final ele e gravado em `src/data/leilao.log`

5.14. Bonus: autenticacao de participantes

Atendido por:

- `src/auth/AuthService.java`
- `src/AuctionServer.java`
  - `authenticateUser`
  - `attemptLogin`
  - `registerUser`
- `src/AuctionClient.java`
  - fluxo de menu, login e cadastro

Como ficou implementado:

- cada usuario pode criar conta
- as contas ficam salvas em `users.json`
- o login e validado antes da entrada no leilao

5.15. Bonus: criptografia das comunicacoes

Atendido por:

- `src/security/CryptoService.java`
- `src/AuctionClient.java`
  - `sendSecure`
  - `readSecure`
- `src/AuctionServer.java`
  - `sendSecure`
  - `readSecure`
  - `replicateEvent`
  - `runReplica`

Como ficou implementado:

- todas as mensagens de rede sao cifradas com AES
- isso vale para TCP e para UDP


6. CONCLUSAO
------------

A aplicacao final demonstra os principais conceitos cobrados pela atividade:

- sockets TCP
- sockets UDP
- processamento concorrente com threads
- servidor principal com estado compartilhado
- cliente interativo
- replicacao entre servidores
- persistencia em arquivo
- autenticacao de usuarios
- criptografia das comunicacoes

Ao mesmo tempo, a implementacao foi mantida propositalmente simples para facilitar:

- explicacao na defesa
- leitura do codigo
- execucao local
- demonstracao pratica do entendimento dos conceitos de sistemas paralelos e distribuidos
