# Filhote do Ouvinte

> Status: **pronto.** O medidor sumindo ao matar o Ouvinte, o contador de mortes, a Orelha de Ouro, o ovo, o filhote (segue, senta, poses e sons), o painel, a busca, os limites, as regras, o README e o site. O plano técnico está em [PLANO-FILHOTE.md](PLANO-FILHOTE.md). A prévia do modelo está em [tools/filhote/preview.html](tools/filhote/preview.html).

O Filhote é a recompensa por vencer o Ouvinte várias vezes. É um pet cego, como o pai, que encontra minérios **ouvindo o eco** quando bate no chão. Você entrega um minério, ele procura outro igual e te leva até lá.

---

## 1. Como conseguir

### Orelha de Ouro
- O mundo conta quantas vezes o Ouvinte já morreu. O contador é **do mundo**, não de cada jogador.
- Na **5ª, 10ª, 15ª... morte**, além da Orelha do Ouvinte normal, ele também dropa uma **Orelha de Ouro**.
- Só conta o Ouvinte "de verdade", que surge à noite. Ouvintes criados com ovo gerador (criativo) não contam.

### Ovo do Filhote
- Receita: **2 Orelhas do Ouvinte + 1 ovo de galinha + 1 Orelha de Ouro**, nesta posição:

```
[Orelha] [      ] [Orelha]
[      ] [ Ovo  ] [      ]
[      ] [ Ouro ] [      ]
```

  Ouro = Orelha de Ouro.

- Use o ovo num bloco: o filhote nasce já domesticado por você.
- As orelhas também servem para o Sino Ensurdecedor. Gastar no sino agora ou guardar para o ovo é escolha sua.

---

## 2. Controles

| Ação | O que faz |
|---|---|
| **Clique direito** no filhote | Senta ou levanta. Sentado, ele fica parado e não te segue (igual ao lobo). |
| **Agachado + clique direito** | Abre o painel do filhote. |

No resto do tempo ele te segue como um lobo, inclusive aparecendo do seu lado quando fica longe demais.

---

## 3. O painel

```
+------------------------------------------+
| Filhote do Ouvinte                       |
|  +---------+  [item]  Buscas             |
|  | retrato |          ■■■■■■■□□□         |
|  |  do     |  [      Procurar      ]     |
|  | filhote |                             |
|  +---------+                             |
|         O Ouvinte está acordado.         |
| Inventário                               |
+------------------------------------------+
```

- **Item:** o espaço onde você coloca o minério que ele deve procurar. Só aceita os itens da tabela da seção 5.
- **Buscas:** uma marquinha por busca; as cheias são as que ainda restam (ver seção 6). Passe o mouse para ver o número.
- **Aviso:** só aparece quando ele não pode procurar: "O Ouvinte está acordado." (vermelho) em noite de caça e "Procurando..." durante uma busca.
- **Procurar:** começa a busca. Fica desativado sem item, em noite de caça ou durante uma busca.
- Fechou sem procurar? O item volta para o seu inventário.
- Nada acontece sem passar pelo painel, para evitar clique errado.

---

## 4. A busca, passo a passo

1. Você coloca o minério e clica em **Procurar**. **O item é consumido na hora.**
2. Ele dá **pulinhos batendo as patas da frente no chão** e depois fica parado de **cabeça inclinada e orelhas em pé**, ouvindo o eco por 1 ou 2 segundos.
3. **Não achou nada** num raio de **64 blocos** (em qualquer altura): abaixa as orelhas, balança a cabeça e **devolve o item**. Não gasta busca.
4. **Achou:** vira para o lado do minério e sai andando. Ele vai até o minério **mais próximo**, contando também a altura (na mina, ele prefere o veio do túnel ao lado em vez de um muito mais fundo).
5. **No caminho:**
   - Se você ficar mais de ~10 blocos para trás, ele para e olha para você até você chegar.
   - Se não houver caminho (penhasco, água funda, rocha), ele vai até o ponto mais perto possível e para ali.
6. **Chegou:** para a **3 a 5 blocos** do minério (na horizontal), pula batendo as patas, dá o **eco** e **senta**, marcando o lugar.
   - O **tom do eco** diz a profundidade: **agudo = raso, grave = fundo.**
   - Ele continua sentado até você levantá-lo com clique direito.
7. Daí em diante é com você: cavar.

**Se ele morrer no caminho, o item já foi gasto.** Esse é o risco da busca.

---

## 5. Minérios aceitos

Você entrega o que o minério dropa. As variantes de deepslate contam como o mesmo minério.

| Você entrega | Ele procura |
|---|---|
| Carvão | Minério de carvão |
| Ferro bruto | Minério de ferro |
| Cobre bruto | Minério de cobre |
| Ouro bruto | Minério de ouro (e ouro do Nether) |
| Redstone | Minério de redstone |
| Lápis-lazúli | Minério de lápis |
| Diamante | Minério de diamante |
| Esmeralda | Minério de esmeralda |
| Quartzo | Minério de quartzo do Nether |
| Fragmento de netherite | Detrito ancestral |

---

## 6. Limites

### Noite de caça
- Enquanto o Ouvinte estiver vivo numa noite de caça, o filhote **se recusa a procurar** (o painel avisa: "O Ouvinte está acordado.").
- Nas noites tranquilas (sem Ouvinte) e de dia, funciona normalmente.
- Se você **matar o Ouvinte**, o filhote volta a funcionar no resto da noite.
- Se a noite de caça começa enquanto ele te guia, ele **se encolhe e espera** ("O filhote se esconde do Ouvinte."). Continua de manhã, ou antes, se o Ouvinte morrer. Nesse tempo ele não te segue nem senta.

### Vida em buscas
- Cada filhote tem **10 buscas** (valor ajustável na config).
- Só contam as buscas que acharam algo. "Não achou" não gasta.
- Depois da última marcação, quando você o levanta (ou depois de 2 minutos sentado), ele **se enterra e vai embora para sempre**: "O filhote se enterra e não volta mais."
- Para ter outro, é preciso outra Orelha de Ouro (mais 5 mortes do Ouvinte).

### Se a vida dele acabar
- Ele não morre de vez: **foge e se enterra** ("O filhote fugiu. Volta de manhã."). Enquanto foge, nada mais o machuca.
- Na **manhã seguinte**, quando você estiver no jogo e no Overworld, ele ressurge do seu lado com as mesmas buscas restantes, vida cheia e o mesmo nome ("O filhote voltou.").
- Uma busca em andamento se perde (e o item também).
- Só o comando `/kill` mata de verdade.

---

## 7. Multiplayer

- O contador de mortes e as Orelhas de Ouro são do **mundo**. Quem pegar a orelha pode fazer o ovo.
- Cada filhote tem **um dono**. Só o dono senta, levanta e abre o painel.

---

## 8. Mudança junto: o medidor depois de matar o Ouvinte

Antes, o medidor de ruído continuava aparecendo depois que o Ouvinte morria (bug). Agora:
- Ao matar o Ouvinte, aparece uma mensagem de vitória e o medidor **some aos poucos**.
- Pelo resto daquela noite, sem medidor e sem ruído.

---

## 9. Aparência

- Versão pequena do pai, com cerca de **1 bloco de altura** contando as orelhas.
- Anda de quatro, apoiado nos braços compridos. Cabeça grande, orelhas enormes, sem olhos (pálpebras costuradas), boquinha com dois dentes.
- Poses: parado, andando, **pulinhos batendo as patas** (busca e chegada), **ouvindo** (cabeça inclinada, orelhas em pé tremendo), **não achou** (orelhas caídas, balança a cabeça) e **sentado** marcando o lugar.

---

## 10. Por que as regras são assim

- **Item consumido antes da busca:** a busca é uma aposta. Se o filhote morre no caminho, você perde o minério.
- **"Não achou" devolve o item:** evita a sensação de pet inútil.
- **Para a 3 a 5 blocos, não em cima:** em cima seria só cavar reto para baixo, e só "dentro do raio" seria difícil demais. Perto, mais o tom da profundidade, deixa você cavar e procurar um pouco.
- **Vida em buscas, não em dias:** não depende do calendário, então não conflita com o sorteio das noites nem com a Orelha de Ouro.
- **Sem detector de "quente ou frio" e sem se enterrar no caminho:** mais simples.
- **Só o jogador ganha poder:** o Ouvinte não fica mais forte a cada morte.

---

## 11. Em aberto

- Ajustar nos testes: raio (64), distância de parada (3 a 5), 10 buscas, a escala do tom da profundidade.
- **Nether:** quartzo e detrito ancestral só existem lá, e pets não atravessam portal sozinhos. Decidir se o filhote vai junto ou se isso fica para depois.
- **Futuro:** o filhote crescer.
