# Hushfall (Silêncio Total): regras

> À noite, o mundo pertence a uma criatura cega que caça só pelo som.
> De dia você constrói em paz. De noite, você aprende a ficar quieto.

O mod só funciona **à noite** e só no **Overworld**. De dia nada disso existe.

## 0. Quais noites

- A **primeira noite** de um mundo novo é sempre de graça.
- Depois, **cada noite tem 50% de chance** de ter o Ouvinte. O sorteio é fixo por mundo e por noite: sair e entrar no mundo não muda o resultado.
- **Ao anoitecer** aparece uma mensagem: *"A noite caiu. Algo acordou... fique em silêncio."* ou *"A noite está calma. Ele não vem esta noite."*
- O **medidor de ruído** só aparece nas noites em que ele vem. Nas noites calmas, pode caçar, minerar e fazer barulho à vontade.

---


---

## 1. O Ouvinte

**Só existe um Ouvinte no mundo.** Ele é um chefe: a ideia é **fugir**, não lutar.

- Nas noites em que vem, surge **logo ao anoitecer**, a **32–45 blocos** de um jogador e **nunca a menos de 30 blocos de ninguém**, sempre na mesma camada que você (superfície ou caverna).
- Ao surgir, solta um **rosnado distante**. É o primeiro aviso da noite.
- **Ronda a sua área**: mesmo sem ouvir nada, a patrulha dele vai se aproximando aos poucos.
- É **cego**: luz, invisibilidade e linha de visão não importam. Ele só reage ao **ruído**.
- **3 blocos de altura**, mas se espreme para passar em túneis e portas de 2 blocos. Golpes e flechas acertam o corpo todo.
- **Chefe**: 150 de vida (75 corações), 12 de dano, resistente a empurrão e mais rápido que você correndo. Barra de chefe aparece a até 32 blocos.
- **Não regenera**: todo dano que ele leva fica, inclusive de uma noite para outra. Dá para enfraquecê-lo ao longo de várias noites. Ele só volta com a vida cheia depois de morrer.
- **Ataque surpresa** (fora da caçada, encurralado ou atordoado): dano ×1,5.
- **Não percebe quedas**: anda até 12 blocos de desnível sem hesitar. Buraco fundo é armadilha.
- **Nunca se perde**: se passar 20 segundos longe de todos, ele se enterra e ressurge perto de alguém com a mesma vida.
- **Ouve de qualquer distância**: barulho Audível ou ALTO chega até ele onde quer que esteja. Se estiver longe demais (mais de 128 blocos), ele se enterra e ressurge a 32–45 blocos de quem fez barulho (no máximo uma vez a cada 30 s).
- **Ao amanhecer se enterra** e volta na próxima noite com o dano que já levou. Se for morto, só volta na noite seguinte e dropa a **Orelha do Ouvinte**.

### Comportamento

| Estado | O que faz | Como você percebe |
|---|---|---|
| **Patrulha** | Ronda a sua região devagar | Orelhas balançando, cliques de tempos em tempos |
| **Investiga** | Vai até a origem do som, fareja e ronda o lugar | Clique alto quando vira para o som, fungadas |
| **Alerta** | Ouviu ou sentiu você: para por ~1 s e ruge | Rugido "Ouvinte ouviu você!" — **corra ou se esconda** |
| **Caça** | Corre direto em você | Rosnado, orelhas para trás, braços esticados |

**Perder o rastro**: se ele passar 3 segundos sem te ouvir, vai até o último lugar onde te ouviu, fareja e volta a patrulhar.

**Sair do mundo não salva ninguém**: a sua barra fica salva com você. Se você sair no meio de uma caçada, volta já com barulho alto (80) e ele ruge e caça de novo. Fora da caçada, você volta com a barra como estava. Ele também guarda o que estava fazendo (caçando, investigando, rondando) quando o mundo é salvo.

### De perto ele te sente

- A até **8 blocos**, com a sua barra em **10 ou mais** (em pé ou agachado): ele te descobre, ruge e caça.
- Com a barra **entre 5 e 10**: ele **desconfia** (vira a cabeça, orelhas em pé tremendo, faíscas nas orelhas). Pare!
- Com a barra **abaixo de 10**: ele não ataca. Chega perto, **fareja você** e fica rondando. Se a barra subir nesse tempo, ele te descobre.
- Com ele a até 14 blocos você ouve o **próprio coração** e as bordas da tela escurecem, mais forte quanto mais perto do limite.

---

## 2. Casas e portas

Não existe esconderijo perfeito: ele ouve através das paredes.

- **Em silêncio dentro de casa** ele não tem motivo para entrar.
- **Fazendo barulho dentro de casa**, ele vem e **arromba portas de madeira** (leva uns 5 segundos, batendo alto). Você ouve as pancadas antes.
- **Portas de ferro aguentam.** Uma casa de pedra com porta de ferro é o refúgio de verdade.

### Dormir

Ele é cego: se você está em silêncio, ele não sabe que você está ali e **a cama funciona** mesmo com ele rondando a casa. Ele só impede o sono quando:

- está **caçando** você ou em **alerta**;
- está **desconfiado** (orelhas tremendo) ou **farejando** por perto;
- está investigando um barulho seu ali do lado;
- a sua barra está em **5 ou mais**.

Chegue em casa, fique parado até a barra zerar e deite.

---

## 3. A barra de ruído (0 a 100)

Medidor redondo no **canto superior esquerdo** (dá para mudar o canto, a margem e o tamanho pelo Mod Menu), só à noite: o anel enche com a cor da faixa, a orelha fica no meio e ao lado aparece a faixa e o valor (ex.: "Audível · 52").

| Ação | Ruído |
|---|---|
| Andar | +5 por segundo (**sozinho, andar nunca passa de 70**) |
| Correr (ou cavalgar) | +10 por segundo |
| De carrinho de mina | +8 por segundo (sem teto, como correr) |
| De barco | +3 por segundo (teto de 70, como andar) |
| Pular | +6 |
| Agachar / ficar parado | +0, e o ruído **cai 4 por segundo** |
| **Bater num bloco** (cada segundo golpeando) | pedra +5/s, madeira +4/s, terra/folhas +2/s |
| Quebrar pedra (ou vidro) / madeira / terra | +15 / +10 / +5 |
| Abrir baú, barril, porta, alçapão, portão | +8 |
| Bater ou ser atingido | +15 (com a **Adaga Silenciosa**: 0) |
| Explosão a até 10 blocos | +40 |

| Faixa | O Ouvinte... |
|---|---|
| **1–30 (Baixo)** | Ouve de 16 a 46 blocos. Só sabe a região, mas **vem rondar**. Zerado ele não ouve. |
| **31–70 (Audível)** | Ouve **de qualquer distância** e vem **investigar** perto de você. |
| **71+ (ALTO!)** | Ouve **de qualquer distância**, ruge e **caça você**. Se estiver longe, você ouve o eco do rugido vindo da direção dele. |

### Modificadores

| Situação | Efeito |
|---|---|
| Piso de lã, tapete, neve ou água | Passos −50% |
| Cascalho ou vidro | Passos +50% |
| **Botas de Feltro** | Passos −60% |
| Chuva / Trovoada | **Todo** ruído −30% / −50% |

---

## 4. Distrações

Sons no mundo que o Ouvinte vai investigar. Cada ponto tem **cooldown**.

| Fonte | Alcance | Cooldown no mesmo bloco |
|---|---|---|
| Sino | 48 blocos | 10 s |
| Note block | 24 blocos | 5 s |
| Dispensador (qualquer disparo) | 20 blocos | 5 s |
| Pistão | 16 blocos | 5 s |
| Projétil caindo (flecha, bola de neve, ovo...) | 12 blocos | 2 s |
| Explosão / raio | 64 blocos | 1 s |

- **Ele aprende**: dois alarmes falsos no mesmo lugar e ele ignora sons dali por 3 minutos. A memória fica no mundo: ele não esquece ao se enterrar e ressurgir.
- **Durante a caçada**, só um estrondo forte (sino, explosão, raio) tira a atenção dele, e só se você não estiver fazendo barulho alto.

---

## 5. Itens

| Item | Receita | Efeito |
|---|---|---|
| **Botas de Feltro** | lã + couro (`W _ W` / `L _ L`) | Passos −60%. Defesa 1, reparo com lã. |
| **Adaga Silenciosa** | ferro + lã + graveto (`_ _ I` / `_ W _` / `S _ _`) | Golpes **sem barulho**. Dano 5, ataque rápido. Levar golpe ainda faz +15. |
| **Orelha do Ouvinte** | Drop ao matar o Ouvinte | Ingrediente do Sino Ensurdecedor. |
| **Sino Ensurdecedor** | graveto + ouro + Orelha (`_ S _` / `G E G` / `G _ G`) | Tocado (botão direito), **atordoa o Ouvinte por 10 s** se ele estiver a até 16 blocos. **Uma vez por noite.** O toque não conta no seu ruído, mas quando o atordoamento passa ele vai investigar de onde veio o sino. |
| **Ovo Gerador de Ouvinte** | Só no criativo | Para testar. Como só existe um, gerar outro faz o antigo sumir. |

---

## 6. As duas regras de ouro

1. **Sempre há um aviso antes do perigo**: rosnado distante quando ele surge, estalo quando ouve algo, rugido antes de toda caçada, passos pesados, coração disparado. Todos os sons têm legenda.
2. **Nunca surge a menos de 30 blocos de ninguém.**

---

## 7. Configuração

`config/silenciototal.json`:

| Opção | Padrão | O que faz |
|---|---|---|
| `graceNights` | `1` | Noites iniciais sem Ouvinte (a primeira noite é de graça). |
| `nightChance` | `0.5` | Chance de cada noite ter o Ouvinte. `1.0` = toda noite; `0.25` = uma a cada quatro, em média. |
| `minSpawnDistance` | `32` | Distância mínima de qualquer jogador (nunca menos de 30). |
| `maxSpawnDistance` | `45` | Distância máxima do jogador escolhido. |
| `noiseMultiplier` | `1.0` | Multiplica todo o ruído do jogador. |

O spawn respeita a dificuldade (nada no Pacífico) e a regra `spawn_monsters`. Arrombar portas respeita a regra `mob_griefing`.

### Medidor (de cada jogador)

`config/silenciototal-client.json`, ou pelo botão de opções do **Mod Menu** (opcional), que mostra o medidor na tela enquanto você ajusta:

| Opção | Padrão | O que faz |
|---|---|---|
| `corner` | `TOP_LEFT` | Canto da tela: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT` ou `BOTTOM_RIGHT`. |
| `offsetX` / `offsetY` | `6` / `6` | Distância da borda lateral e da borda de cima (ou de baixo), de 0 a 200. |
| `scale` | `1.0` | Tamanho do medidor, de 0.5 a 2.0. |
| `showText` | `true` | Mostra a faixa e o valor ao lado do anel. |
