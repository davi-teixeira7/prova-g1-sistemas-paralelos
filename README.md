# Sistema de Leilão Distribuído em Java

Este projeto consiste em uma aplicação de leilão em tempo real baseada na arquitetura cliente-servidor, utilizando Sockets TCP para comunicação e Threads para gerenciamento de múltiplas conexões simultâneas.

## Funcionalidades

* **Servidor de Leilão:** Gerencia o estado do item, valida lances concorrentes e notifica todos os clientes conectados sobre novos eventos.
* **Cliente de Leilão:** Interface via console para participação ativa, permitindo o envio de lances e monitoramento do status atual.
* **Gerenciamento de Concorrência:** Uso de mecanismos de sincronização para garantir que apenas o lance de maior valor seja aceito em casos de envios simultâneos.
* **Persistência de Dados:** Geração automática de um arquivo de histórico (`historico_leilao.txt`) contendo todos os lances e o resultado final após o encerramento.
* **Teste de Estresse:** Inclusão de um script para simulação de múltiplos robôs para validação de carga e robustez do sistema.

## Estrutura do Projeto

* `AuctionServer.java`: Classe principal do servidor que mantém o estado do leilão e processa as conexões.
* `AuctionClient.java`: Classe do participante que se comunica com o servidor.
* `AuctionStressTest.java`: Script para automação de testes com múltiplos clientes simultâneos.

## Pré-requisitos

* Java JDK 11 ou superior instalado.
* Configuração da variável de ambiente `JAVA_HOME`.

### Configuração de Rede e Endereçamento

O sistema está configurado por padrão para operar em ambiente de desenvolvimento local:
* **Endereço IP:** `127.0.0.1` (localhost). Este endereço permite que o Cliente e o Servidor se comuniquem internamente no mesmo sistema operacional.
* **Porta TCP:** `8080`. Porta definida para a escuta de conexões de entrada.

**Nota para execução em rede externa:** Caso deseje conectar computadores diferentes, o endereço `SERVER_IP` na classe `AuctionClient` deve ser alterado para o endereço IPv4 real da máquina servidora na rede local (ex: `192.168.x.x`).


## Como Executar

### 1. Inicialização do Servidor
Abra o terminal na pasta raiz do projeto e execute:
```bash
javac AuctionServer.java
java AuctionServer
```
O servidor solicitará o nome do item a ser leiloado e iniciará a escuta na porta 8080.

### 2. Inicialização do Cliente
Em um novo terminal, execute:
```bash
javac AuctionClient.java
java AuctionClient
```
O sistema solicitará um nome de usuário para identificação dos lances.

### 3. Execução do Teste de Estresse
Para validar o comportamento do servidor sob carga:
```bash
javac AuctionStressTest.java
java AuctionStressTest
```

## Protocolo de Comunicação

A comunicação entre os nós ocorre via mensagens de texto estruturadas:
* `NOVO_LANCE:usuario:valor`: Notificação enviada pelo servidor aos clientes.
* `LEILAO_ENCERRADO:vencedor:valor`: Notificação final de encerramento.
* `ERRO:mensagem`: Mensagem de feedback para lances inválidos ou menores que o atual.

