**Hushfall**: at night, a blind creature hunts by sound.

### What's new in 1.4.0
- **The Listener Pup**: every 5th time the Listener dies, it also drops a **Golden Ear**. Craft it with 2 Listener's Ears and an egg into a **Pup Egg**: a blind pet that finds ores by listening to the echo.
  - Crouch + right-click it, put in an ore and click **Search**. It taps the ground, listens and leads you to the nearest ore of the same kind within 64 blocks.
  - When it gets there, it echoes and sits to mark the spot. A high echo means shallow, a low echo means deep.
  - 10 searches per pup (`pupSearches` in the config). It won't search while the Listener is awake. If it gets badly hurt, it runs off and comes back the next morning.
- **Killing the Listener ends the night**: the noise meter fades away and there is no noise for the rest of that night.
- **Fire counts as an attack**: setting the Listener on fire (or luring it into lava) makes it hunt the nearest player, even if you're quiet.

### Which file?
- **`hushfall.jar`**: just the mod. You also need **Fabric API**.
- **`hushfall.zip`** (GitHub only): the mod + Fabric API + instructions. Unzip it and copy the `.jar` files into the `mods` folder.

### How to install
1. Install **Fabric Loader** for Minecraft **26.3** ([official installer](https://fabricmc.net/use/installer/)).
2. Put the `.jar` files in the `mods` folder (delete older Hushfall versions first).
3. Launch the game with the Fabric profile.

How to play, the creature and the items: see the [README](https://github.com/pedrorreiro/hushfall#readme) and the [full rules](https://github.com/pedrorreiro/hushfall/blob/main/REGRAS.md) (Portuguese).

---

**Hushfall (Silêncio Total)**: à noite, uma criatura cega caça pelo som.

### Novidades da 1.4.0
- **Filhote do Ouvinte**: a cada 5 mortes do Ouvinte cai também uma **Orelha de Ouro**. Com ela, 2 Orelhas do Ouvinte e um ovo, você faz o **Ovo do Filhote**: um pet cego que acha minérios ouvindo o eco.
  - Agachado, clique direito nele, coloque um minério e clique em **Procurar**. Ele bate no chão, escuta e te leva até o minério igual mais perto, num raio de 64 blocos.
  - Chegando lá, ele dá o eco e senta marcando o lugar. Eco agudo é raso, grave é fundo.
  - São 10 buscas por filhote (`pupSearches` na config). Com o Ouvinte acordado ele não procura. Se a vida dele acabar, ele foge e volta na manhã seguinte.
- **Matar o Ouvinte encerra a noite**: o medidor some aos poucos e não há ruído pelo resto daquela noite.
- **Fogo conta como ataque**: botar fogo no Ouvinte (ou atraí-lo para a lava) faz ele caçar o jogador mais perto, mesmo em silêncio.

### Qual baixar?
- **`hushfall.jar`**: só o mod. Você também precisa do **Fabric API**.
- **`hushfall.zip`** (só no GitHub): o mod + Fabric API + instruções. Descompacte e copie os `.jar` para a pasta `mods`.

### Como instalar
1. Instale o **Fabric Loader** para o Minecraft **26.3** ([instalador oficial](https://fabricmc.net/use/installer/)).
2. Coloque os `.jar` na pasta `mods` (apague versões antigas do Hushfall antes).
3. Abra o jogo pelo perfil do Fabric.

Como jogar, a criatura e os itens: veja o [README](https://github.com/pedrorreiro/hushfall#readme) e as [regras completas](https://github.com/pedrorreiro/hushfall/blob/main/REGRAS.md).

*Fan project, not official. Not approved by or associated with Mojang or Microsoft. / Projeto de fã, não oficial. Não aprovado nem associado à Mojang ou à Microsoft.*
