package com.pedro.silenciototal.entity;

import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.pup.ListenerPupMenu;
import com.pedro.silenciototal.pup.OreScanner;
import com.pedro.silenciototal.pup.PupOres;
import com.pedro.silenciototal.pup.PupReturn;
import com.pedro.silenciototal.pup.PupReturns;
import com.pedro.silenciototal.registry.ModSounds;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Filhote do Ouvinte: pet cego que acha minérios pelo eco. Nasce do Ovo do Filhote já com dono.
 * Segue o dono e senta como o lobo, mas não come, não se reproduz e não ataca.
 * Regras completas em FILHOTE.md.
 *
 * <p>A busca (PLANO-FILHOTE, fase 5) só roda no servidor: {@link #LISTENING} (bate no chão e ouve
 * enquanto o {@link OreScanner} varre) -> {@link #GUIDING} (anda até o minério) -> {@link #MARKING}
 * (pula, dá o eco e senta). Sem minério por perto: {@link #NOTHING} (balança a cabeça e devolve o item).
 *
 * <p>Limites (fase 6): com o Ouvinte acordado ele não procura e, se estava guiando, se esconde até
 * poder continuar. Depois da última busca ele se enterra e vai embora. Com a vida zerada ele não
 * morre: {@link #FLEEING} (corre e se enterra) e volta na manhã seguinte ({@link PupReturns}).
 */
public class ListenerPup extends TamableAnimal {
	public static final float MAX_HEALTH = 16.0f;

	/** O que ele está fazendo, para a animação no cliente. */
	public static final int IDLE = 0;
	/** Cabeça inclinada e orelhas em pé, ouvindo o eco. */
	public static final int LISTEN = 1;
	/** Pulinhos batendo as duas patas da frente no chão (começo da busca). */
	public static final int TAP = 2;
	/** Orelhas caídas balançando a cabeça: não achou nada. */
	public static final int NO = 3;
	/** Pulinhos, eco descendo para a terra e senta marcando o lugar (chegou). */
	public static final int MARK = 4;

	/** Etapas da busca (só no servidor). */
	public static final int NONE = 0;
	public static final int LISTENING = 1;
	public static final int GUIDING = 2;
	public static final int MARKING = 3;
	public static final int NOTHING = 4;
	/** Vida zerada: corre um pouco e se enterra (volta de manhã). */
	public static final int FLEEING = 5;

	/** Duração de um pulinho e dos três pulinhos, igual à animação do modelo. */
	private static final int HOP_TICKS = 11;
	private static final int TAP_TICKS = HOP_TICKS * 3;
	/** Quanto tempo, no mínimo, ele fica ouvindo depois de bater no chão. */
	private static final int LISTEN_TICKS = 40;
	private static final int NOTHING_TICKS = 40;
	private static final int MARK_TICKS = TAP_TICKS + 12;
	/** Para quando estiver a esta distância do minério, na horizontal. */
	public static final double ARRIVE_DISTANCE = 5.0;
	/** Se o dono ficar mais longe que isto, ele espera. */
	private static final double WAIT_FOR_OWNER = 10.0;
	/** Caminho recalculado a cada 2 s; alvos longe vão em trechos. */
	private static final int REPATH_TICKS = 40;
	private static final double SEGMENT = 24.0;
	/** Parado este tempo sem chegar: o caminho acabou, marca onde está ("para onde der"). */
	private static final int STUCK_TICKS = 60;
	/** Fugindo: quanto tempo corre antes de se enterrar. */
	private static final int FLEE_TICKS = 40;
	/** Sem buscas: se o dono não o levantar, vai embora sozinho depois de 2 min. */
	private static final int FAREWELL_TICKS = 2400;

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ListenerPup.class, EntityDataSerializers.INT);

	/** Buscas que ainda restam. Só as que acharam algo gastam. */
	private int searchesLeft = ModConfig.get().pupSearches;
	/** Tick (do cliente) em que a ação atual começou, para a animação começar do início. */
	private int actionStartTick;

	private int searchPhase = NONE;
	private int phaseTicks;
	/** O minério achado (guiando e marcando). */
	private @Nullable BlockPos searchTarget;
	/** O item da busca enquanto ouve: volta para o dono se não achar nada. */
	private ItemStack searchItem = ItemStack.EMPTY;
	private OreScanner.@Nullable Scan scan;
	private int nextRepath;
	private int stuckTicks;
	private @Nullable Vec3 lastProgress;
	/** Guiando, mas parado escondido porque o Ouvinte acordou. */
	private boolean hiding;
	private int farewellTicks;

	public ListenerPup(EntityType<? extends ListenerPup> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new FloatGoal(this));
		goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5));
		goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		goalSelector.addGoal(3, new SearchGoal());
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 6.0f, 2.0f));
		goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(10, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, IDLE);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ACTION.equals(accessor)) {
			actionStartTick = tickCount;
		}
	}

	public int getAction() {
		return entityData.get(ACTION);
	}

	public void setAction(int action) {
		entityData.set(ACTION, action);
	}

	/** Ticks desde que a ação atual começou (no cliente). */
	public int actionTicks() {
		return tickCount - actionStartTick;
	}

	public int searchesLeft() {
		return searchesLeft;
	}

	public void setSearchesLeft(int searchesLeft) {
		this.searchesLeft = Math.max(0, searchesLeft);
	}

	/**
	 * Clique direito do dono: senta ou levanta. Agachado: abre o painel (fase 4).
	 * Outros jogadores não mandam nele.
	 */
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isTame() || !isOwnedBy(player) || searchPhase == FLEEING) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			if (!level().isClientSide()) {
				openPanel(player);
			}
			return InteractionResult.SUCCESS;
		}
		if (isBusy()) {
			// Ouvindo ou guiando: não senta no meio da busca.
			return InteractionResult.SUCCESS;
		}
		if (!level().isClientSide()) {
			setOrderedToSit(!isOrderedToSit());
			jumping = false;
			navigation.stop();
		}
		return InteractionResult.SUCCESS;
	}

	private void openPanel(Player player) {
		ListenerPup pup = this;
		player.openMenu(new ExtendedMenuProvider<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer opener) {
				return pup.getId();
			}

			@Override
			public Component getDisplayName() {
				return pup.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
				return new ListenerPupMenu(containerId, inventory, pup);
			}
		});
	}

	/** Está numa busca (ouvindo, guiando ou marcando). */
	public boolean isSearching() {
		return searchPhase != NONE;
	}

	public int searchPhase() {
		return searchPhase;
	}

	public @Nullable BlockPos searchTarget() {
		return searchTarget;
	}

	/** Ouvindo, guiando, balançando a cabeça ou fugindo: anda sozinho e não aceita sentar. */
	private boolean isBusy() {
		return searchPhase == LISTENING || searchPhase == GUIDING || searchPhase == NOTHING || searchPhase == FLEEING;
	}

	/** Guiando, parado escondido do Ouvinte. */
	public boolean isHiding() {
		return hiding;
	}

	/** Noite de caça com o Ouvinte vivo (sempre a do Overworld, mesmo com o filhote em outra dimensão). */
	public boolean isListenerAwake() {
		return level() instanceof ServerLevel level && NightCycle.isListenerAwake(level.getServer().overworld());
	}

	/**
	 * Botão Procurar do painel. Tira 1 item do espaço (gasto na hora; volta só se não achar nada),
	 * bate no chão e começa a varredura.
	 *
	 * @param offered o espaço do painel
	 */
	public boolean startSearch(ServerPlayer player, ItemStack offered) {
		if (isSearching() || searchesLeft <= 0 || !(level() instanceof ServerLevel level) || isListenerAwake()) {
			return false;
		}
		Predicate<BlockState> target = PupOres.target(offered);
		if (target == null) {
			return false;
		}
		searchItem = offered.split(1);
		setOrderedToSit(false);
		setInSittingPose(false);
		navigation.stop();
		setPhase(LISTENING);
		setAction(TAP);
		scan = OreScanner.start(level, blockPosition(), target);
		// Fecha o painel para o dono ver o filhote trabalhando.
		player.closeContainer();
		return true;
	}

	/** Para a busca no meio (o item já gasto não volta). */
	public void cancelSearch() {
		if (scan != null) {
			scan.cancel();
			scan = null;
		}
		searchTarget = null;
		searchItem = ItemStack.EMPTY;
		hiding = false;
		navigation.stop();
		setPhase(NONE);
		setAction(IDLE);
	}

	private void setPhase(int phase) {
		searchPhase = phase;
		phaseTicks = 0;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (searchPhase != NONE) {
			phaseTicks++;
			switch (searchPhase) {
				case LISTENING -> tickListening(level);
				case GUIDING -> tickGuiding(level);
				case MARKING -> tickMarking(level);
				case FLEEING -> {
					if (phaseTicks >= FLEE_TICKS) {
						burrow(level);
					}
				}
				case NOTHING -> {
					if (phaseTicks >= NOTHING_TICKS) {
						setPhase(NONE);
						setAction(IDLE);
					}
				}
				default -> setPhase(NONE);
			}
		}
		// Acabaram as buscas: espera o dono levantá-lo (ou 2 min) e vai embora.
		if (searchesLeft <= 0 && searchPhase == NONE && isTame() && isAlive()
				&& (!isOrderedToSit() || ++farewellTicks >= FAREWELL_TICKS)) {
			if (getOwner() instanceof ServerPlayer owner) {
				owner.sendSystemMessage(Component.translatable("message.silenciototal.pup_gone").withStyle(ChatFormatting.GRAY));
			}
			burrow(level);
		}
	}

	private void tickListening(ServerLevel level) {
		if (scan == null) {
			// Voltou de um save no meio da busca: varre de novo.
			Predicate<BlockState> target = PupOres.target(searchItem);
			if (target == null) {
				setPhase(NONE);
				setAction(IDLE);
				return;
			}
			scan = OreScanner.start(level, blockPosition(), target);
		}
		if (phaseTicks <= TAP_TICKS && phaseTicks % HOP_TICKS == 0) {
			playSound(ModSounds.PUP_TAP, 0.8f, 0.9f + random.nextFloat() * 0.2f);
		}
		if (phaseTicks == TAP_TICKS) {
			setAction(LISTEN);
		}
		if (phaseTicks < TAP_TICKS + LISTEN_TICKS || !scan.done()) {
			return;
		}
		BlockPos found = scan.found();
		scan = null;
		if (found == null) {
			nothingFound(level);
		} else {
			searchTarget = found;
			searchItem = ItemStack.EMPTY;
			setSearchesLeft(searchesLeft - 1);
			setPhase(GUIDING);
			setAction(IDLE);
			nextRepath = 0;
			stuckTicks = 0;
			lastProgress = position();
		}
	}

	/** Não ouviu nada: orelhas caídas, balança a cabeça e devolve o item ao dono. */
	private void nothingFound(ServerLevel level) {
		setPhase(NOTHING);
		setAction(NO);
		playSound(ModSounds.PUP_WHINE, 1.0f, getVoicePitch());
		ItemStack item = searchItem;
		searchItem = ItemStack.EMPTY;
		if (getOwner() instanceof ServerPlayer owner && owner.level() == level) {
			owner.sendOverlayMessage(Component.translatable("message.silenciototal.pup_nothing").withStyle(ChatFormatting.GRAY));
			owner.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
		} else if (!item.isEmpty()) {
			spawnAtLocation(level, item);
		}
	}

	private void tickGuiding(ServerLevel level) {
		BlockPos target = searchTarget;
		if (target == null) {
			setPhase(NONE);
			return;
		}
		// O Ouvinte acordou: para, se encolhe e espera (de manhã, ou se o Ouvinte morrer, continua).
		if (isListenerAwake()) {
			if (!hiding) {
				hiding = true;
				navigation.stop();
				setInSittingPose(true);
				if (getOwner() instanceof ServerPlayer owner && owner.level() == level) {
					owner.sendOverlayMessage(Component.translatable("message.silenciototal.pup_hiding").withStyle(ChatFormatting.GRAY));
				}
			}
			return;
		}
		if (hiding) {
			hiding = false;
			setInSittingPose(false);
			nextRepath = phaseTicks;
			stuckTicks = 0;
			lastProgress = position();
		}
		Vec3 center = Vec3.atCenterOf(target);
		double dx = center.x - getX();
		double dz = center.z - getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal <= ARRIVE_DISTANCE) {
			startMarking();
			return;
		}
		LivingEntity owner = getOwner();
		if (owner != null && owner.level() == level && distanceToSqr(owner) > WAIT_FOR_OWNER * WAIT_FOR_OWNER) {
			// Ficou para trás: para e olha para o dono até ele chegar.
			navigation.stop();
			getLookControl().setLookAt(owner, 10.0f, getMaxHeadXRot());
			stuckTicks = 0;
			lastProgress = position();
			nextRepath = 0;
			return;
		}
		if (phaseTicks >= nextRepath || navigation.isDone() && phaseTicks >= nextRepath - REPATH_TICKS + 10) {
			repath(center, dx, dz, horizontal);
			nextRepath = phaseTicks + REPATH_TICKS;
		}
		if (navigation.isDone()) {
			getLookControl().setLookAt(center.x, getEyeY(), center.z);
		}
		// Sem sair do lugar: o caminho acabou antes do minério (penhasco, rocha, água). Marca ali mesmo.
		if (phaseTicks % 20 == 0) {
			Vec3 now = position();
			stuckTicks = lastProgress != null && now.distanceToSqr(lastProgress) < 1.0 ? stuckTicks + 20 : 0;
			lastProgress = now;
			if (stuckTicks >= STUCK_TICKS) {
				startMarking();
			}
		}
	}

	/** Caminho até o minério; se está longe, só um trecho na direção dele. */
	private void repath(Vec3 center, double dx, double dz, double horizontal) {
		BlockPos goal;
		if (horizontal > SEGMENT) {
			goal = BlockPos.containing(getX() + dx / horizontal * SEGMENT, getY(), getZ() + dz / horizontal * SEGMENT);
		} else {
			goal = BlockPos.containing(center);
		}
		Path path = navigation.createPath(goal, 1);
		if (path != null) {
			navigation.moveTo(path, 1.0);
		}
	}

	/** Chegou: senta, bate no chão e, depois dos pulinhos, dá o eco (agudo = raso, grave = fundo). */
	private void startMarking() {
		navigation.stop();
		setPhase(MARKING);
		setAction(MARK);
		setOrderedToSit(true);
		setInSittingPose(true);
		if (searchTarget != null) {
			getLookControl().setLookAt(Vec3.atCenterOf(searchTarget));
		}
	}

	private void tickMarking(ServerLevel level) {
		if (phaseTicks <= TAP_TICKS && phaseTicks % HOP_TICKS == 0) {
			playSound(ModSounds.PUP_TAP, 0.8f, 0.9f + random.nextFloat() * 0.2f);
		}
		if (phaseTicks == TAP_TICKS && searchTarget != null) {
			level.playSound(null, getX(), getY(), getZ(), ModSounds.PUP_ECHO, SoundSource.NEUTRAL, 1.5f, echoPitch(getY() - searchTarget.getY()));
		}
		if (phaseTicks >= MARK_TICKS) {
			searchTarget = null;
			setPhase(NONE);
			setAction(IDLE);
		}
	}

	/** Tom do eco pela profundidade: 0 blocos -> 1,6 (agudo), 100 ou mais -> 0,5 (grave). */
	public static float echoPitch(double depth) {
		return 1.6f - (float) Mth.clamp(depth / 100.0, 0.0, 1.0) * 1.1f;
	}

	/**
	* Dano que mataria: em vez de morrer, foge e se enterra, e volta para o dono na manhã seguinte
	* com as mesmas buscas. A busca em andamento (e o item) se perde. Só o /kill mata de verdade.
	*
	* @return true se fugiu (a morte não acontece)
	*/
	public boolean flee(DamageSource source) {
		if (source.is(DamageTypes.GENERIC_KILL) || !isTame() || getOwnerReference() == null || !(level() instanceof ServerLevel level)) {
			return false;
		}
		setHealth(1.0f);
		if (searchPhase == FLEEING) {
			return true;
		}
		cancelSearch();
		setOrderedToSit(false);
		setInSittingPose(false);
		ServerLevel overworld = level.getServer().overworld();
		PupReturns.add(level.getServer(), new PupReturn(getOwnerReference().getUUID(), searchesLeft, NightCycle.day(overworld) + 1,
				Optional.ofNullable(getCustomName())));
		setPhase(FLEEING);
		playSound(ModSounds.PUP_WHINE, 1.0f, getVoicePitch());
		if (getOwner() instanceof ServerPlayer owner) {
			owner.sendSystemMessage(Component.translatable("message.silenciototal.pup_fled").withStyle(ChatFormatting.GRAY));
		}
		Entity attacker = source.getEntity();
		Vec3 danger = attacker != null ? attacker.position() : source.getSourcePosition() != null ? source.getSourcePosition() : position();
		Vec3 away = DefaultRandomPos.getPosAway(this, 12, 4, danger);
		if (away != null) {
			navigation.moveTo(away.x, away.y, away.z, 1.6);
		}
		return true;
	}

	/** Fugindo, nada mais o machuca (só o /kill). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (searchPhase == FLEEING && !source.is(DamageTypes.GENERIC_KILL)) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	/** Some na terra: o filhote vai embora (fim das buscas ou fuga). */
	public void burrow(ServerLevel level) {
		burrowEffect(level);
		discard();
	}

	/** Terra voando e o som de cavar (ao ir embora e ao voltar). */
	public void burrowEffect(ServerLevel level) {
		BlockState ground = level.getBlockState(getOnPos());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2, getZ(), 25, 0.3, 0.15, 0.3, 0.1);
		}
		level.playSound(null, getX(), getY(), getZ(), ModSounds.PUP_BURROW, SoundSource.NEUTRAL, 1.0f, 1.0f);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (scan != null) {
			scan.cancel();
			scan = null;
		}
		super.remove(reason);
	}

	/** Durante a busca ele anda sozinho: tira o seguir o dono, passear e olhar em volta. */
	private class SearchGoal extends Goal {
		SearchGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return isBusy();
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canMate(Animal other) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("searches_left", searchesLeft);
		// Ouvindo (com o item) ou guiando (com o alvo) continua depois de sair e entrar no mundo; fugindo
		// também (a volta já foi marcada: ao carregar ele só termina de se enterrar).
		int saved = searchPhase == LISTENING && !searchItem.isEmpty() || searchPhase == GUIDING && searchTarget != null
				|| searchPhase == FLEEING ? searchPhase : NONE;
		output.putInt("search_phase", saved);
		if (saved == GUIDING) {
			output.store("search_target", BlockPos.CODEC, searchTarget);
		}
		if (saved == LISTENING) {
			output.store("search_item", ItemStack.CODEC, searchItem);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		searchesLeft = Math.max(0, input.getIntOr("searches_left", ModConfig.get().pupSearches));
		searchPhase = input.getIntOr("search_phase", NONE);
		searchTarget = input.read("search_target", BlockPos.CODEC).orElse(null);
		searchItem = input.read("search_item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		if (searchPhase == LISTENING && searchItem.isEmpty() || searchPhase == GUIDING && searchTarget == null
				|| searchPhase != LISTENING && searchPhase != GUIDING && searchPhase != FLEEING) {
			searchPhase = NONE;
		}
		if (searchPhase == LISTENING) {
			setAction(LISTEN);
		}
		phaseTicks = TAP_TICKS;
		nextRepath = 0;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return ModSounds.PUP_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.PUP_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.PUP_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(ModSounds.PUP_STEP, 0.15f, 1.0f);
	}

	@Override
	public float getVoicePitch() {
		return 0.9f + random.nextFloat() * 0.2f;
	}
}
