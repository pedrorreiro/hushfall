# Silêncio Total: regras

> À noite, o mundo pertence a uma criatura cega que caça só pelo som.
> De dia você constrói em paz. De noite, você aprende a ficar quieto.

O mod só funciona **à noite** e só no **Overworld**. Durante o dia nada disso existe e nenhum processamento extra roda.

---

## 1. O Ouvinte

**Só existe um Ouvinte no mundo.** Ele é um chefe: a ideia é **fugir**, não lutar.

- Surge **logo ao anoitecer**, a **32–45 blocos** de um jogador e **nunca a menos de 30 blocos de ninguém**.
- **Ronda a sua área**: mesmo sem ouvir nada, a patrulha dele vai se aproximando aos poucos da região do jogador mais próximo. Em menos de um minuto ele já está passando por perto.
- **3 blocos de altura**, mas se espreme para passar em túneis e portas de 2 blocos: buraco não é esconderijo.
- Ao surgir, solta um **rosnado distante**. É o primeiro aviso da noite.
- É **cego**: luz, invisibilidade e linha de visão não importam. Ele só reage ao **ruído** e, de perto, à sua respiração.
- **Chefe**: 150 de vida (75 corações), 12 de dano (2 golpes sem armadura), resistente a empurrão e corre mais que você correndo.
- **Barra de chefe** aparece quando ele está a até 32 blocos. Ela também serve de aviso de que ele está perto.
- **Regenera** 1 de vida por segundo quando não está caçando: bater e fugir não funciona.
- **Ataque surpresa** (enquanto ele não está caçando, ou quando está encurralado): dano ×1,5.
- **Dá para acertar o corpo todo**: golpes e flechas acertam até a cabeça, mesmo ele se espremendo em túneis.
- **Não percebe quedas**: anda até 12 blocos de desnível sem hesitar. Armadilhas de queda, lava e TNT são o jeito de vencê-lo.
- **Ao amanhecer se enterra** e volta na noite seguinte com a vida cheia.
- **Se for morto**, só volta na noite seguinte. Ele dropa a **Orelha do Ouvinte** e bastante XP.
- **Sempre na sua camada**: surge na superfície se você está na superfície e na caverna se você está numa caverna.
- **Nunca se perde**: se passar 20 segundos patrulhando longe de todos (mais de 56 blocos, ou numa altura muito diferente), ele se enterra e ressurge perto de alguém **com a mesma vida**. Fugir para longe não cura o bicho nem livra você dele.

### Comportamento

| Estado | O que faz | Como você percebe |
|---|---|---|
| **Patrulha** | Anda devagar ao acaso | Orelhas balançando, cliques de tempos em tempos |
| **Investiga** | Vai até a origem do último som e fareja por lá | Clique alto quando vira para o som, orelhas em pé, fungadas |
| **Alerta** | Ouviu barulho alto: para por ~1 s e ruge | Rugido "Ouvinte ouviu você!" — **é a sua chance de correr ou se esconder** |
| **Caça** | Corre direto em você | Rosnado, orelhas para trás, braços esticados |

### De perto ele te sente

A até **8 blocos** dele, ele te descobre se a sua barra de ruído estiver em **10 ou mais** (em pé ou agachado): dá o aviso e caça.

**Em silêncio (barra abaixo de 10) ele não ataca.** Pode chegar colado em você, parar, virar o focinho e **farejar**, e depois fica rondando por ali. Se a barra subir enquanto ele está perto, ele te descobre. Como andar em pé enche a barra rápido (+5/s), para se mexer perto dele é preciso **agachar**.

### Sinais de que você está quase sendo descoberto

- **Coração disparado e bordas escuras**: com ele a até 14 blocos você ouve o próprio coração, e as bordas da tela escurecem no ritmo. Quanto mais perto ele estiver e mais perto sua barra estiver de 10, mais rápido o coração e mais escura a tela. Se ele está caçando você, é pânico total.
- **Ele desconfia**: com ele a até 8 blocos e sua barra entre **5 e 10**, ele vira a cabeça para você, levanta as orelhas (tremendo, viradas para a frente), estala e solta faíscas pelas orelhas. É o "mais um pouquinho e ele te acha": **pare e deixe a barra baixar**.

**Rondar**: depois de farejar o lugar de um som e não achar nada, ele fica uns 15 segundos andando em volta dali antes de voltar a patrulhar.

**Perder o rastro**: na caçada, se ele passar 3 segundos sem te ouvir (ruído baixo e você fora do raio), vai até o último lugar onde te ouviu, fareja e volta a patrulhar. Entrar numa **sala silenciosa** também faz ele perder o rastro.

**Distração durante a caçada**: um estrondo forte (sino, explosão, raio) tira a atenção dele, mas só se você não estiver fazendo barulho alto naquele momento.

---

## 2. A barra de ruído (0 a 100)

Aparece como um **medidor redondo no canto superior esquerdo**, **só à noite**: o anel enche no sentido horário com a cor da faixa (verde, amarelo, vermelho), com a orelha no meio e, ao lado, o nome da faixa e o valor ("Audível · 52"). Embaixo aparece o que está afetando o ruído (sala silenciosa, chuva, noite de graça).

| Ação | Ruído |
|---|---|
| Andar | +5 por segundo (**sozinho, andar nunca passa de 70**) |
| Correr (ou cavalgar) | +10 por segundo |
| Pular | +6 |
| Agachar / ficar parado | +0, e o ruído **cai 4 por segundo** |
| **Bater num bloco** (cada segundo golpeando) | pedra +5/s, madeira +4/s, terra/folhas +2/s, lã quase nada |
| Quebrar pedra (ou vidro) | +15 |
| Quebrar madeira | +10 |
| Quebrar terra, lã, folhas... | +5 |
| Abrir baú, barril, porta, alçapão, portão | +8 |
| Bater ou ser atingido | +15 (bater com a **Adaga Silenciosa**: 0) |
| Explosão a até 10 blocos | +40 |

> Exemplo: cortar uma tora no soco (~3 s batendo + quebrar) faz uns 22 de ruído; com machado é menos, porque você bate por menos tempo. Minerar 3 ou 4 blocos de pedra seguidos já passa de 70.

| Faixa | O Ouvinte... |
|---|---|
| **1–30 (Baixo)** | Ouve de 16 a 46 blocos. Só sabe a região (erro de ~12 blocos), mas **vem rondar**. Zerado (0) ele não ouve. |
| **31–70 (Audível)** | Ouve de 48 a 96 blocos e vai **investigar** (erro de 2 a 10 blocos; quanto mais barulho, mais preciso). |
| **71+ (ALTO!)** | Ouve a 128 blocos, dá o aviso e **caça você**. Se estiver longe, você ouve o eco do rugido vindo da direção dele. |

> **Andar chega a 70 em ~14 s**: andar em pé à noite mantém o Ouvinte vindo na sua direção, e a 8 blocos com a barra em 10+ ele te descobre. Para andar sem ser achado, **agache**; parado e em silêncio, ele só fareja. Correr, pular, minerar ou brigar passa de 70 e dispara a caçada direto.

### Modificadores

| Situação | Efeito |
|---|---|
| Piso de lã ou tapete | Passos -50% |
| Neve (bloco, camada, neve fofa) | Passos -50% |
| Na água | Passos -50% |
| Cascalho ou vidro | Passos +50% |
| **Botas de Feltro** | Passos -60% |
| Chuva | **Todo** ruído -30% |
| Trovoada | **Todo** ruído -50% |

---

## 3. Distrações

Sons no mundo que o Ouvinte vai investigar. Cada ponto tem **cooldown** para não virar exploit.

| Fonte | Alcance | Cooldown no mesmo bloco |
|---|---|---|
| Sino | 48 blocos | 10 s |
| Note block | 24 blocos | 5 s |
| Dispensador (qualquer disparo) | 20 blocos | 5 s |
| Pistão (estender/recolher) | 16 blocos | 5 s |
| Projétil caindo (flecha, bola de neve, ovo...) | 12 blocos | 2 s |
| Explosão / raio | 64 blocos | 1 s |

**Ele aprende**: se o Ouvinte for **2 vezes ao mesmo lugar** (raio de 4 blocos) e não achar nada, ignora sons dali por **3 minutos**. Um relógio de redstone tocando sem parar não prende ele para sempre; armadilhas precisam funcionar de primeira (ou em lugares diferentes).

**Truque clássico**: atire uma flecha ou jogue uma bola de neve longe de você. Ele vai olhar para onde ela caiu.

**Armadilha sonora**: note block longe da base + um buraco fundo no caminho (ele não percebe quedas). Preso no buraco, fica **encurralado** e leva dano triplo.

---

## 4. Sala silenciosa

Um espaço fechado com **paredes, piso e teto de lã** (qualquer cor).

- **Portas, alçapões e portões fechados** também vedam (senão não daria para entrar). Abertos, o som vaza.
- Qualquer outro bloco sólido na casca (pedra, madeira, vidro) deixa o som vazar.
- Interior de até ~400 blocos (por exemplo 9×9×4). Móveis, tochas, camas e baús lá dentro não atrapalham.
- Lá dentro **seu ruído é zero e cai rápido**, o Ouvinte não te escuta e, se estava caçando, perde o rastro.
- **Dormir numa sala silenciosa pula a noite normalmente**, mesmo com o Ouvinte rondando lá fora (o jogo normalmente proíbe dormir com monstros perto).
- O HUD mostra "Sala silenciosa: ninguém te ouve aqui".

---

## 5. Itens

| Item | Receita | Efeito |
|---|---|---|
| **Botas de Feltro** | `L` = couro, `W` = qualquer lã:<br>`W _ W`<br>`L _ L` | Passos -60%. Defesa 1 (como couro). Reparo com lã. |
| **Adaga Silenciosa** | `I` = ferro, `W` = qualquer lã, `S` = graveto:<br>`_ _ I`<br>`_ W _`<br>`S _ _` | **Golpear com ela não faz barulho** (levar golpe ainda faz +15). Dano um pouco menor que a espada de ferro, mas ataca mais rápido. Para lidar com um zumbi sem chamar o Ouvinte. |
| **Orelha do Ouvinte** | Drop ao matar o Ouvinte | Segurando (em qualquer mão), você ouve um batimento cardíaco que acelera e fica mais alto quanto mais perto ele está (alcance de 96 blocos, sem direção). |
| **Ovo Gerador de Ouvinte** | Só no criativo | Para testar. Como só existe um, gerar outro faz o antigo sumir. |

---

## 6. As duas regras de ouro (e como o mod cumpre)

1. **Sempre há um aviso sonoro antes do perigo.**
   - Rosnado distante quando ele surge.
   - Clique alto quando ele vira para investigar um som seu.
   - Rugido + ~1 segundo parado antes de toda caçada.
   - Passos pesados audíveis por perto.
   - Todos os sons têm legenda própria (ative as legendas para jogar sem som).
2. **Nunca spawna a menos de 30 blocos de ninguém.** O padrão é 40–60; a configuração não aceita menos de 30.

---

## 7. Mudanças e sugestões em relação à ideia original

- **Ouvinte único e chefe** (150 de vida, regenera, barra de chefe): a ideia é fugir dele.
- **Faro de perto**: a 8 blocos, barra em 10+ = ele te descobre; em silêncio ele só fareja e ronda, não ataca.
- **Ronda**: depois de investigar, fica uns 15 s em volta do lugar.
- **Orelha do Ouvinte**: a recompensa por matá-lo.
- **Noite de graça**: a primeira noite de um mundo novo não tem Ouvinte (a barra aparece para você aprender). Dá tempo de juntar lã para a primeira sala. `graceNights`.
- **Andar tem teto (70)**: explicado na seção 2.
- **Ataque surpresa ×1,5 e "encurralado"**: ainda vale atacar de surpresa, mas não mata ele.
- **Habituação**: ele para de cair no mesmo truque (seção 3), complementando o cooldown por bloco.
- **Projéteis como distração** e **raios como estrondo**.
- **Trovoada -50%**: a tempestade é a noite mais segura de todas.
- **Vidro quebrando faz barulho de pedra** (+8).
- **Portas fechadas vedam a sala silenciosa**.
- **Cego para quedas**: é o que viabiliza armadilhas de buraco.
- **Opção `sleepOnlyInSilentRoom`** (desligada): para quem quer modo difícil, à noite só se dorme em sala silenciosa.

---

## 8. Configuração

`config/silenciototal.json`:

| Opção | Padrão | O que faz |
|---|---|---|
| `graceNights` | `1` | Noites iniciais sem Ouvinte. |
| `minSpawnDistance` | `32` | Distância mínima de qualquer jogador (nunca menos de 30). |
| `maxSpawnDistance` | `45` | Distância máxima do jogador escolhido. |
| `sleepOnlyInSilentRoom` | `false` | Modo difícil: à noite só se dorme em sala silenciosa. |
| `noiseMultiplier` | `1.0` | Multiplica todo o ruído do jogador. |

O spawn também respeita a dificuldade (nada no Pacífico) e a regra `spawn_monsters`.
