# Plano: correção do medidor, Orelha de Ouro e Filhote do Ouvinte

Resultado da conversa de design. Nada aqui está implementado ainda.

Dividido em 6 fases. Cada fase pode ir para o jogo sozinha, com seu teste, sem esperar as próximas.

---

## Fase 1: medidor some quando o Ouvinte morre (bug) (feita)

**Problema.** `NoiseTracker.tickPlayer` liga o medidor com `NightCycle.isHuntingNight(level)`, que não sabe que o Ouvinte já morreu nesta noite. O `killedNight` existe no `ListenerState`, mas só o `ListenerSpawner` consulta.

**Mudanças**
- `NightCycle` (ou `ListenerSpawner`): novo `isListenerAwake(ServerLevel)` = `isHuntingNight(level) && state.killedNight() != day(level)`. É a pergunta "o Ouvinte está ativo esta noite?", usada também pelo Filhote (fase 6).
- `NoiseTracker.tickPlayer`: `active` passa a usar `isListenerAwake`. Revisar os outros usos de `isHuntingNight` e decidir caso a caso (o aviso do anoitecer em `announceNight` continua com `isHuntingNight`).
- `ListenerSpawner.onKilled`: manda para os jogadores do Overworld uma mensagem de vitória na barra de ação (ex.: "A noite volta a ser só noite."), em `pt_br.json` e `en_us.json`.
- `NoiseHud` (cliente): quando a flag `NIGHT` cai, o medidor some aos poucos (~1 s de transparência) em vez de sumir na hora. Guardar um `alpha` que vai a 0 e aplicar nas cores do anel e do texto.

**Teste** (`SilencioTotalClientTest`): noite de caça, Ouvinte vivo, medidor ativo -> matar o Ouvinte -> poucos ticks depois `NoiseTracker` inativo e ruído 0.

---

## Fase 2: contador de mortes e Orelha de Ouro (feita)

**Mudanças**
- `ListenerState`: novo campo `kills` (int), com `optionalFieldOf("kills", 0)` para mundos antigos continuarem abrindo. `killed(night)` incrementa.
- `Listener.dropCustomDeathLoot`: se ele é o Ouvinte "oficial" do mundo (`isCurrent`) e `(kills + 1) % 5 == 0`, também dropa a **Orelha de Ouro**. O `die()` chama o drop antes de `onKilled`, por isso o `+ 1`.
- Ouvintes do ovo gerador (criativo) não contam para o contador.
- `ModItems`: `GOLDEN_EAR` (`golden_ear`), raridade incomum.
- `ModItems`: `PUP_EGG` (`listener_pup_egg`), "Ovo do Filhote". Item próprio (não `SpawnEggItem`), porque precisa definir o dono ao chocar.
- Receita com forma `listener_pup_egg.json`: `listener_ear` nos dois cantos de cima, `minecraft:egg` no meio e `golden_ear` embaixo (as orelhas "em cima" do ovo).
- Textura de item: Orelha de Ouro (recolorir a orelha) e ovo, no `tools/gen_textures.py`. Modelos e arquivos `items/*.json`. Nomes nos dois idiomas.

**Teste:** matar o Ouvinte oficial 5 vezes (chamando `kill`/dano direto) -> a 5ª dropa a Orelha de Ouro e as 4 primeiras não. Receita produz o ovo.

---

## Fase 3: o Filhote existir (sem busca ainda) (feita)

> Feito assim: a textura sai do `tools/filhote/build.py` (que agora grava também em `textures/entity/listener_pup/`), não do `gen_textures.py`. O agachado + clique direito por enquanto só mostra um aviso; o painel vem na fase 4. Retratos no jogo em `tools/filhote/poses_no_jogo.png`.

**Entidade** `ListenerPup`, subclasse de `TamableAnimal` (verificar o nome na 26.3), que já traz dono, sentar e `FollowOwnerGoal` com teleporte quando fica longe, igual ao lobo.
- Registro em `ModEntities` (`listener_pup`), categoria `CREATURE`, tamanho ~0.6 x 0.7.
- Não se reproduz, não come, não ataca. Goals: flutuar, sentar, seguir o dono, olhar para o jogador, andar à toa.
- **Clique direito** (dono, mão vazia ou qualquer item): senta ou levanta.
- **Agachado + clique direito** (dono): abre o painel (fase 4).
- Ovo do Filhote: usar num bloco faz surgir o filhote já domesticado pelo jogador e consome o ovo.
- Dados salvos: `searchesLeft` (começa em `pupSearches` da config) e o estado da busca (fase 5).

**Cliente**
- `ListenerPupModel` + `ListenerPupRenderer` + `ListenerPupRenderState`. Versão pequena do pai: cabeça grande, orelhas enormes, sem olhos.
- Poses (ver `tools/filhote/preview.html`): sentado, **ouvindo** (cabeça inclinada e orelhas em pé, a mesma ideia do estado "desconfiado" do Ouvinte), **pulinhos batendo as duas patas da frente no chão**, **balançando a cabeça**. Geometria e textura de partida: `tools/filhote/template.html` e `tools/filhote/build.py`.
- Textura no `gen_textures.py`, com a mesma paleta do Ouvinte.

**Sons** (`ModSounds` + `sounds.json`): batida no chão, eco, ganido (balançar a cabeça), enterrar. Onde der, reaproveitar os sons do Ouvinte com pitch mais alto.

**Teste:** usar o ovo -> filhote existe, tem dono, segue, senta com clique direito (chamando `mobInteract` direto; o input simulado não funciona neste Mac).

---

## Fase 4: o painel (feita)

> Feito assim: menu `pup/ListenerPupMenu` registrado como `ExtendedMenuType` (dados de abertura = id do filhote, para o retrato). A tela usa o fundo do inventário do cavalo do próprio jogo, sem a moldura dos baús: retrato do filhote à esquerda; à direita o espaço do minério com "Buscas" e uma marquinha por busca (o número aparece ao passar o mouse; com mais de 10 buscas na config vira "7 de 12"), e o botão; o fundo é esticado no meio para uma linha de aviso com a largura toda, que só aparece quando ele não pode procurar ("O Ouvinte está acordado." ou "Procurando..."). Na noite de caça o aviso fica vermelho. A tabela de minérios (`pup/PupOres`) já existe. Por enquanto **Procurar** só faz o filhote bater no chão e avisa que a busca ainda não existe; o item não é gasto. Prints em `tools/filhote/painel_no_jogo.png` e `tools/filhote/prints/`.

Usar um **menu do próprio Minecraft** (`AbstractContainerMenu`) em vez de pacotes próprios. Assim o jogo já cuida do espaço do item, de devolver o item ao fechar e de sincronizar números.
- `ListenerPupMenu`: 1 espaço que só aceita os itens da tabela de minérios, mais o inventário do jogador. Ao fechar (`removed`), devolve o item que sobrou.
- Botão "Procurar" via `clickMenuButton` (o mesmo mecanismo da mesa de encantamento e do cortador de pedra, sem pacote novo).
- `ContainerData` com `searchesLeft` e o estado (livre, noite de caça, já buscando) para a tela.
- Registro do `MenuType` com o id do filhote nos dados de abertura (a Fabric tem versão "extended" para isso; verificar o nome na 26.3).
- `ListenerPupScreen` (cliente):

```
+----------------------------------+
|  Filhote do Ouvinte              |
|  Item:  [  ]                     |
|  Buscas: 7/10                    |
|  [ Procurar ]                    |
+----------------------------------+
```

- O botão fica desativado sem item, em noite de caça (com a frase "O filhote se recusa a fazer barulho esta noite.") ou durante uma busca.

**Teste:** abrir o menu no servidor, colocar um item que não é minério (recusado), colocar diamante, fechar (o diamante volta).

---

## Fase 5: a busca (feita)

> Feito assim: `pup/OreScanner` (um só para o servidor, fila com 0,3 ms por tick no total) e as etapas no `ListenerPup` (`LISTENING`, `GUIDING`, `MARKING` e `NOTHING` para "não achou"), com um goal que tira o seguir/passear durante a busca. Procurar tira 1 item do espaço e fecha o painel. Ouvindo ou guiando, o clique direito não senta. A chegada marca a até 5 blocos na horizontal; o eco sai depois dos pulinhos, com tom pela profundidade (sem partículas). Ouvindo (com o item) e guiando (com o alvo) são salvos com a entidade.
>
> Desempenho medido no `PupScanBenchmark` (mundo normal, mediana de 5 depois de aquecer): média de 0,31 a 0,37 ms por tick e pior tick de 0,36 a 0,42 ms por minério; 4 buscas juntas, 0,45 ms de média e 0,70 ms no pior tick. A primeira busca de cada sessão é mais lenta (código ainda frio na JVM): 1 a 4 ms num tick, uma vez só. Nenhum chunk carregado (conferido no teste principal). Prints em `tools/filhote/busca_no_jogo.png`.


**Tabela de minérios** (`PupOres`): item -> tag de blocos do jogo.

| Item | Blocos (tag) |
|---|---|
| Carvão | `minecraft:coal_ores` |
| Ferro bruto | `minecraft:iron_ores` |
| Cobre bruto | `minecraft:copper_ores` |
| Ouro bruto | `minecraft:gold_ores` (já inclui o ouro do Nether) |
| Redstone | `minecraft:redstone_ores` |
| Lápis-lazúli | `minecraft:lapis_ores` |
| Diamante | `minecraft:diamond_ores` |
| Esmeralda | `minecraft:emerald_ores` |
| Quartzo | `minecraft:nether_quartz_ore` |
| Fragmento de netherite | `minecraft:ancient_debris` |

**Estados do filhote:** `IDLE` -> `LISTENING` -> `GUIDING` -> `MARKING` (e de volta a `IDLE`).

1. **Procurar** -> `LISTENING`: tira 1 item do espaço, bate no chão (som e animação), fica na pose de ouvir por ~40 ticks.
2. **Varredura** (`OreScanner`) durante esses 40 ticks: chunks já carregados num raio de 64 blocos, todas as alturas. Guarda o bloco mais próximo em **distância 3D**. Ver as regras de desempenho logo abaixo, que são requisito.
3. **Não achou:** balança a cabeça, ganido, devolve o item ao jogador e volta a `IDLE`. Não gasta busca.
4. **Achou:** o item já foi consumido. `searchesLeft--`. Vira para o alvo e passa a `GUIDING`.
5. **`GUIDING`:** recalcula o caminho até o minério a cada ~2 s (`createPath(alvo, alcance)`). Quando o alvo é inalcançável (ex.: 80 blocos abaixo), o caminho do jogo já termina no ponto mais perto, que é o "para onde der".
   - Se o dono estiver a mais de 10 blocos, para e olha para ele até ele chegar perto.
   - Chega quando está a **3 a 5 blocos na horizontal** do minério, ou quando o caminho acaba / ele fica parado ~3 s.
6. **`MARKING`:** senta, bate no chão. O som do eco tem pitch pela profundidade (`dy` de 0 a ~100 blocos -> pitch de ~1.6 a ~0.5). Fica sentado até o dono levantar com clique direito.
7. Se o filhote morrer no caminho, o item já foi gasto (decisão de design).

Busca em andamento é salva com a entidade (alvo e estado), então sair e entrar no mundo não perde a busca.

### Desempenho da varredura (requisito)

A busca não pode ser sentida no jogo: nada de travadinha ao clicar em "Procurar", nem com vários filhotes buscando ao mesmo tempo num servidor.

Tamanho do problema: raio de 64 blocos = ~81 chunks x 24 seções de 16x16x16 (Overworld) = ~2.000 seções, ou ~8 milhões de blocos se lidos um a um. Ler tudo de uma vez travaria o servidor. As regras abaixo reduzem isso a quase nada:

1. **Nunca carregar nem gerar chunk.** Só olhar chunks que já estão na memória (`getChunkNow`, que devolve nulo se não estiver carregado). Carregar chunk é a parte cara de verdade. Chunk descarregado é ignorado; com a distância de simulação padrão, 64 blocos já está dentro do que fica carregado.
2. **Pular seções inteiras sem ler bloco nenhum.** Cada seção guarda uma paleta com os tipos de bloco que existem nela. Se a paleta não tem o minério (`LevelChunkSection.maybeHas`, verificar o nome na 26.3), a seção inteira é descartada consultando só a paleta. Seções só de ar também são puladas. Para diamante, quase todas as seções caem aqui.
3. **Da mais perto para a mais longe, e parar cedo.** Ordenar as seções pela distância até o filhote. Quando achar um minério a distância `d`, descartar todas as seções cuja distância mínima possível já é maior que `d`. Para minérios comuns (carvão, ferro), a busca termina nas primeiras seções.
4. **Orçamento fixo por tick, compartilhado.** Um único `OreScanner` no servidor atende todos os filhotes, numa fila. Ele gasta no máximo um orçamento fixo por tick (ex.: 0,5 ms medido com `System.nanoTime()`, ou um número fixo de seções lidas por completo) e continua no tick seguinte. Assim o custo total por tick fica limitado, não importa quantos filhotes estejam buscando.
5. **A animação esconde o tempo.** Os ~2 s de "ouvindo" são o tempo da varredura. Se ela ainda não acabou, ele continua ouvindo mais um pouco. Ninguém percebe.
6. **Só na thread do servidor** (o mundo não é seguro para outras threads). Não guardar referência a chunk de um tick para o outro: guardar só as coordenadas e buscar o chunk de novo, porque ele pode ter sido descarregado.
7. **Caminho barato.** Recalcular o caminho a cada ~2 s, não a cada tick. Para alvos longe, andar em trechos de até ~32 blocos em direção ao alvo em vez de pedir um caminho gigante de uma vez.
8. **Rede leve.** Os sons do eco saem uma vez por evento (batida, chegada), não a cada tick.

**Critério de aceite:** num mundo normal, medir o tempo da varredura (log de desenvolvimento com ms por tick e seções lidas). A média deve ficar abaixo de 0,5 ms por tick e nenhum tick acima de 2 ms por causa da busca. Testar o pior caso (carvão, que existe em quase toda seção) e o caso "não existe" (esmeralda fora das montanhas, que percorre tudo). O teste automatizado também confere que nenhum chunk novo foi carregado durante a busca.

**Teste:** mundo plano, colocar `diamond_ore` a ~30 blocos e 20 abaixo; chamar a busca direto no filhote; depois de N ticks: diamante consumido, `searchesLeft` = 9, filhote sentado a 3 a 5 blocos (na horizontal) do minério. Sem minério no raio: item devolvido, `searchesLeft` intacto.

---

## Fase 6: limites (feita)

- **Noite de caça** (sempre a do Overworld, mesmo com o filhote em outra dimensão): o painel avisa e o servidor recusa a busca. Guiando, ele para encolhido (`isHiding`), avisa o dono ("O filhote se esconde do Ouvinte.") e continua sozinho quando o Ouvinte dorme ou morre. O alvo continua salvo. Ouvindo e marcando terminam normalmente (duram 2 a 4 s).
- **Fim das buscas:** com 0 buscas e sem busca em andamento, ao ser levantado (ou depois de 2 min sentado) ele se enterra: terra do bloco embaixo + som, como o Ouvinte ao amanhecer, sem outras partículas. Mensagem no chat: "O filhote se enterra e não volta mais."
- **Vida zerada:** `ServerLivingEntityEvents.ALLOW_DEATH` cancela a morte (menos `/kill`). Ele fica com 1 de vida, não toma mais dano, corre 2 s para longe de quem bateu (`FLEEING`) e se enterra. A volta fica nos attachments globais do servidor (`PUP_RETURNS`: dono, buscas, dia, nome), não no dono como previsto: assim funciona mesmo com o dono longe ou desconectado. De dia, a partir da manhã seguinte, com o dono online no Overworld, ele ressurge do lado dele com vida cheia. Fugindo, o estado é salvo, então sair do mundo no meio não duplica nem perde o filhote.
- **Eco:** o anel de partículas da fase 5 saiu (ficou só o som).

**Teste:** busca recusada em noite de caça; guiando, o Ouvinte acorda -> ele se esconde, não anda, o painel avisa; o Ouvinte morre -> volta a guiar. A última busca zera as buscas e, levantado, ele some. Dano letal -> foge, nada mais o machuca, a volta fica marcada, some; no mesmo dia não volta; na manhã seguinte volta do lado do dono, com as mesmas buscas, vida cheia e o nome.

---

## Fase 7: textos e divulgação (feita)

- `REGRAS.md` e o resumo do `README.md`: Orelha de Ouro, Filhote, fim do medidor ao matar.
- Visualizador / site (`tools/build_viewer.py`): Filhote em 3D, receitas do ovo e da Orelha de Ouro.
- `branding/descricao.md`, `descricao-curseforge.html` e `.github/release-notes.md`.

> Feito assim: seção "O Filhote" no site, depois do Sino, com o Filhote em 3D (parado, andando, procurando, não achou, achou, com os sons do jogo) e as receitas da Orelha de Ouro e do ovo. Versão 1.4.0 (notas e `gradle.properties`).

---

## XP

Não muda: `xpReward = 100`. Só confirmar no jogo (ou num teste) que as orbes caem quando a morte vem de um golpe do jogador. O Minecraft não solta XP se o jogador não acertou o mob recentemente (morte só por queda ou lava).

---

## Ficam para ajustar nos testes

- Raio da varredura (64), distância de parada (3 a 5), buscas por filhote (10), mapeamento tom x profundidade.
- **Nether:** quartzo e detrito ancestral só existem lá. Lobos não atravessam portal sozinhos, então o jogador precisaria levar o filhote junto (ou isso fica para depois).
- Futuro, combinado para depois: o filhote crescer.
