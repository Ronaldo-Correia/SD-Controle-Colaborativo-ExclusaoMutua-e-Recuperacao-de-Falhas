# Trabalho de Sistemas Distribuídos – Sistema Distribuído de Controle Colaborativo com Exclusão Mútua e Recuperação de Falhas
- **Instituição:** IFBA - Instituto Federal da Bahia
- **Curso:** Análise e Desenvolvimento de Sistemas (ADS)
- **Disciplina:** Sistemas Distribuídos
- **Projeto:** Sistema Distribuído de Controle Colaborativo com Exclusão Mútua 
e Recuperação de Falhas
- **Professor:** Felipe de Souza Silva
- **Semestre:** 5
- **Ano:** 2025.1

---
## 📌 Projeto: Sistema Distribuído de Controle Colaborativo

### Objetivo:
Projetar, implementar e avaliar um sistema distribuído que simule o acesso concorrente a um recurso crítico compartilhado, empregando algoritmos clássicos de exclusão mútua distribuída, técnicas de replicação de dados com consistência eventual e recuperação de falhas com checkpoints e rollback.

---
## Integrantes do Projeto

<table>
  <tr>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/u/129338943?v=4" width="100px;" alt="Foto da Integrante Ronaldo"/><br />
      <sub><b><a href="https://github.com/Ronaldo-Correia">Ronaldo Correia</a></b></sub>
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/u/114780494?v=4" width="100px;" alt="Foto da Integrante Marcelo"/><br />
      <sub><b><a href="https://github.com/marceloteclas">Marcelo Jesus</a></b></sub>
    </td>
  </tr>
</table>

---

## 📁Estrutura do Projeto
```
br.ifba.saj.distribuido
├── coordinator          # Coordenador Central (servidor principal do sistema)
│   └── CoordinatorServer.java
│
├── node                 # Nós replicados (clientes que acessam o recurso compartilhado)
│   └── NodeClient.java
│
├── model                # Modelos de dados e classes utilitárias
│   ├── LamportClock.java     # Implementação do relógio lógico de Lamport
│   ├── Message.java          # Estrutura de mensagem trocada entre os nós e o coordenador
│   └── MessageType.java      # Enum com os tipos de mensagens (JOIN, REQUEST, GRANT etc.)
│
└── Main.java            # Classe opcional para inicialização ou testes
```

---
## 🚀 Requisitos

- Java 21
- Maven 3.8+

---

## 👨‍💻Como Executar
1. 📥Clone este repositório:
```bash
git clone https://github.com/Ronaldo-Correia/SD-Controle-Colaborativo-ExclusaoMutua-e-Recuperacao-de-Falhas.git
```
2. 🧪 Executando o Projeto
Inicie o Coordenador (servidor central):
```
mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath br.ifba.saj.distribuido.coordinator.CoordinatorServer"
```

Deve aparecer: [COORD] Listening on 5000
 
3. Inicie os nós clientes (em terminais diferentes):
```
mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath br.ifba.saj.distribuido.node.NodeClient 1"```
```
```
mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath br.ifba.saj.distribuido.node.NodeClient 2"
```
```
mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath br.ifba.saj.distribuido.node.NodeClient 3"
```
O número passado (1, 2, 3) representa o PID do nó.
   
