package com.pedro.silenciototal.entity;

import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.noise.NoiseLevel;
import com.pedro.silenciototal.noise.NoiseTracker;
import com.pedro.silenciototal.registry.ModItems;
import com.pedro.silenciototal.registry.ModSounds;
import com.pedro.silenciototal.spawn.ListenerSpawner;
import com.pedro.silenciototal.spawn.ListenerState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * O Ouvinte: cego, caça só pelo som. Existe um só por mundo e funciona como um chefe:
 * a ideia é fugir dele, não lutar.
 *
 * <ul>
 *   <li><b>Patrulha</b>: anda devagar ao acaso, as orelhas mexendo.</li>
 *   <li><b>Investiga</b>: vai até a origem do último som, fareja e depois ronda o lugar.</li>
 *   <li><b>Alerta</b>: ouviu ou sentiu alguém. Para, ruge (o aviso) e um instante depois...</li>
 *   <li><b>Caça</b>: corre atrás do jogador. Se o ruído cai e ele sai do raio, perde o rastro.</li>
 * </ul>
 *
 * De perto (8 blocos) ele descobre quem está com a barra de ruído em 10 ou mais. Quem está em
 * silêncio (barra abaixo de 10) ele só fareja de pertinho e fica rondando, sem atacar.
 */
public class Listener extends Monster {
	public static final int PATROL = 0;
	public static final int INVESTIGATE = 1;
	public static final int ALERT = 2;
	public static final int HUNT = 3;

	public static final double MAX_HEALTH = 150.0;
	public static final double PATROL_SPEED = 0.5;
	public static final double INVESTIGATE_SPEED = 0.95;
	public static final double HUNT_SPEED = 1.35;
	/** Duração do aviso antes de começar a caçar. */
	public static final int ALERT_TICKS = 25;
	/** Sem ouvir o alvo por esse tempo, perde o rastro. */
	public static final int LOSE_TRACK_TICKS = 60;
	public static final float SURPRISE_MULTIPLIER = 1.5f;
	/** Raio em que ele sente quem está de pé (ou agachado mas ainda barulhento). */
	public static final double SENSE_RADIUS = 8;
	/** De perto, com a barra abaixo disso ele só fareja e não ataca. */
	public static final float QUIET_NOISE = 10;
	/** Distância em que ele para para farejar quem está em silêncio. */
	public static final double SNIFF_RADIUS = 4;
	/** Depois de farejar sem achar nada, fica rondando o lugar por esse tempo. */
	public static final int PROWL_TICKS = 300;
	private static final double PROWL_RADIUS = 6;
	public static final double BOSS_BAR_RADIUS = 32;
	private static final int CORNERED_TICKS = 40;
	/** Longe de todo mundo (ou numa altura muito diferente) por esse tempo, ele se enterra e ressurge perto de alguém. */
	public static final int REPOSITION_TICKS = 400;
	private static final double LOST_DISTANCE = 56;
	private static final double LOST_HEIGHT = 14;
	/** Depois de ir duas vezes ao mesmo lugar sem achar nada, ignora sons dali por 3 minutos. */
	private static final int HABITUATION_VISITS = 2;
	private static final int HABITUATION_TICKS = 3600;
	private static final double HABITUATION_RADIUS = 4;

	private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(Listener.class, EntityDataSerializers.INT);
	/** Desconfiado: alguém perto está quase fazendo barulho demais (orelhas em pé, cabeça virada). */
	private static final EntityDataAccessor<Boolean> SUSPICIOUS = SynchedEntityData.defineId(Listener.class, EntityDataSerializers.BOOLEAN);
	/** Atordoado pelo Sino Ensurdecedor: não ouve, não anda, não ataca. */
	private static final EntityDataAccessor<Boolean> STUNNED = SynchedEntityData.defineId(Listener.class, EntityDataSerializers.BOOLEAN);
	/** Tempo para arrombar uma porta de madeira (o zumbi leva 12 s). */
	public static final int DOOR_BREAK_TICKS = 100;
	/** A partir desse ruído, perto dele, ele desconfia (o aviso antes de te descobrir em {@link #QUIET_NOISE}). */
	public static final float SUSPICIOUS_NOISE = 5;
	private static final int SUSPICIOUS_TICKS = 30;

	private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.silenciototal.listener"),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private @Nullable Vec3 soundTarget;
	private float soundLoudness;
	private boolean soundFromPlayer;
	private int soundVersion;
	private int stateTicks;
	private @Nullable ServerPlayer alertTarget;
	private long lastHeardTarget;
	private int stuckTicks;
	private @Nullable Vec3 lastStuckCheck;
	private @Nullable Vec3 prowlCenter;
	private long prowlUntil;
	private final List<Habituation> habituations = new ArrayList<>();
	private int lostTicks;
	private long nextSniff;
	private long suspiciousUntil;
	private long stunnedUntil;
	private @Nullable Vec3 stunSource;

	private record Habituation(Vec3 pos, int visits, long expires) {
	}

	/** Só no cliente: escala atual do desenho (ele se encolhe sob tetos baixos). */
	public float clientScale = -1;

	public Listener(EntityType<? extends Listener> type, Level level) {
		super(type, level);
		this.xpReward = 100;
		// Cego: não enxerga fogo, espinhos ou frutas silvestres no caminho.
		this.setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 0.0f);
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 0.8)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(STATE, PATROL);
		entityData.define(SUSPICIOUS, false);
		entityData.define(STUNNED, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DoorBashGoal(this));
		this.goalSelector.addGoal(2, new HuntGoal(this));
		this.goalSelector.addGoal(3, new InvestigateGoal(this));
		this.goalSelector.addGoal(4, new ProwlGoal(this));
		this.goalSelector.addGoal(5, new PatrolGoal(this));
		// Sem NearestAttackableTargetGoal: ele não enxerga ninguém. Só o som define o alvo.
	}

	public int getState() {
		return this.entityData.get(STATE);
	}

	/** Altura do desenho em pé (3 blocos). A caixa de colisão é menor para caber em túneis de 2. */
	public static final double VISUAL_HEIGHT = 3.0;

	/**
	 * Caixa para mirar golpes e flechas: da altura do desenho, para acertar peito e cabeça.
	 * No cliente acompanha o encolhimento sob tetos baixos; no servidor usa a altura cheia.
	 */
	public AABB getVisualBox(AABB box) {
		double height = clientScale > 0 ? VISUAL_HEIGHT * clientScale / 1.6 : VISUAL_HEIGHT;
		return box.setMaxY(Math.max(box.maxY, box.minY + height));
	}

	public boolean isSuspicious() {
		return this.entityData.get(SUSPICIOUS);
	}

	private void setState(int state) {
		if (getState() != state) {
			this.entityData.set(STATE, state);
			this.stateTicks = 0;
		}
		updateDoorPathing();
	}

	/**
	 * Só atravessa (arrombando) portas de madeira quando vai atrás de alguém que fez barulho.
	 * Patrulhando ou indo atrás de uma distração, porta fechada é parede.
	 */
	private void updateDoorPathing() {
		boolean afterPlayer = getState() == HUNT || getState() == INVESTIGATE && soundFromPlayer;
		getNavigation().setCanOpenDoors(afterPlayer && !isStunned());
	}

	public boolean isStunned() {
		return this.entityData.get(STUNNED);
	}

	/**
	 * Sino Ensurdecedor: fica atordoado, sem ouvir, andar ou atacar. Quando passa, vai
	 * investigar de onde veio o som do sino.
	 */
	public void stun(int ticks, Vec3 source) {
		if (!isAlive()) {
			return;
		}
		stunnedUntil = level().getGameTime() + ticks;
		stunSource = source;
		entityData.set(STUNNED, true);
		entityData.set(SUSPICIOUS, false);
		setTarget(null);
		alertTarget = null;
		prowlCenter = null;
		soundTarget = null;
		setState(PATROL);
		getNavigation().stop();
		playSound(ModSounds.LISTENER_HURT, 1.6f, 0.6f);
	}

	public @Nullable Vec3 getSoundTarget() {
		return soundTarget;
	}

	public boolean isProwling() {
		return getState() == PATROL && prowlCenter != null && level().getGameTime() < prowlUntil;
	}

	// ------------------------------------------------------------------ audição

	/** Chamado pelo {@link NoiseTracker} quando um jogador está dentro do raio de audição. */
	public void hearPlayer(ServerPlayer player, float noise) {
		if (isStunned()) {
			return;
		}
		int state = getState();
		if (state == HUNT) {
			if (getTarget() == player) {
				lastHeardTarget = level().getGameTime();
			}
			return;
		}
		if (state == ALERT) {
			return;
		}
		if (NoiseLevel.of(noise) == NoiseLevel.HIGH) {
			startAlert(player);
			return;
		}
		// Médio: sabe a região, não o ponto exato. Quanto mais barulho, mais preciso.
		// Baixo: só uma ideia vaga da região (12 blocos de erro), mas já é motivo para vir rondar.
		double spread = NoiseLevel.of(noise) == NoiseLevel.LOW ? 12 : 2 + (NoiseLevel.HIGH_FROM - noise) / 40.0 * 8.0;
		Vec3 guess = player.position().add(
				(random.nextDouble() * 2 - 1) * spread, 0, (random.nextDouble() * 2 - 1) * spread);
		investigate(guess, noise, true);
	}

	/** Um som no mundo (distração, explosão, raio...) com a força dada em blocos de alcance. */
	public void hearSound(Vec3 pos, float loudness) {
		if (isStunned()) {
			return;
		}
		int state = getState();
		if (state == ALERT) {
			return;
		}
		if (state == HUNT) {
			// Só um estrondo forte tira a atenção da caça, e só se a presa já não estiver gritando.
			LivingEntity target = getTarget();
			float targetNoise = target instanceof ServerPlayer player ? NoiseTracker.get(player) : 0;
			if (loudness < 40 || targetNoise >= NoiseLevel.HIGH_FROM) {
				return;
			}
			setTarget(null);
		}
		if (isHabituatedTo(pos)) {
			return;
		}
		investigate(pos, loudness, false);
	}

	/**
	 * De perto ele descobre quem está com a barra de ruído em 10 ou mais (em pé ou agachado).
	 * Em silêncio, ele pode passar do seu lado e só farejar.
	 */
	public static boolean canSense(ServerPlayer player) {
		return NoiseTracker.get(player) >= QUIET_NOISE;
	}

	private void senseNearby(ServerLevel level) {
		ServerPlayer noisy = null;
		ServerPlayer quiet = null;
		ServerPlayer edgy = null;
		double bestEdgy = SENSE_RADIUS * SENSE_RADIUS;
		double bestNoisy = SENSE_RADIUS * SENSE_RADIUS;
		double bestQuiet = SNIFF_RADIUS * SNIFF_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (!isHuntable(player)) {
				continue;
			}
			double distance = distanceToSqr(player);
			if (canSense(player)) {
				if (distance <= bestNoisy) {
					noisy = player;
					bestNoisy = distance;
				}
			} else {
				if (distance <= bestQuiet) {
					quiet = player;
					bestQuiet = distance;
				}
				if (distance <= bestEdgy && NoiseTracker.get(player) >= SUSPICIOUS_NOISE) {
					edgy = player;
					bestEdgy = distance;
				}
			}
		}
		if (noisy != null) {
			startAlert(noisy);
			return;
		}
		if (edgy != null) {
			becomeSuspicious(edgy);
		}
		if (quiet != null) {
			sniffAt(quiet);
		}
	}

	/**
	 * Quase: alguém perto está com a barra entre 5 e 10. Ele vira a cabeça, levanta as orelhas,
	 * estala e solta ondas pelas orelhas. É o aviso visível de "mais um pouquinho e ele te acha".
	 */
	private void becomeSuspicious(ServerPlayer player) {
		long now = level().getGameTime();
		getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
		if (!isSuspicious()) {
			entityData.set(SUSPICIOUS, true);
			playSound(ModSounds.LISTENER_CLICK, 1.4f, 1.0f + random.nextFloat() * 0.2f);
		}
		suspiciousUntil = now + SUSPICIOUS_TICKS;
		if (level() instanceof ServerLevel level && now % 6 == 0) {
			Vec3 look = Vec3.directionFromRotation(0, getYHeadRot());
			Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.45);
			double y = getY() + 3.2;
			for (int sign = -1; sign <= 1; sign += 2) {
				level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, getX() + side.x * sign, y, getZ() + side.z * sign, 3, 0.1, 0.15, 0.1, 0.02);
			}
		}
	}

	/**
	 * Alguém em silêncio colado nele: para, vira o focinho para a pessoa e fareja. Não ataca, mas
	 * fica rondando ali por um tempo; se a barra subir nesse meio tempo, ele descobre.
	 */
	private void sniffAt(ServerPlayer player) {
		long now = level().getGameTime();
		if (now < nextSniff) {
			return;
		}
		nextSniff = now + 60;
		getNavigation().stop();
		getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
		playSound(ModSounds.LISTENER_SNIFF, 1.3f, 0.75f + random.nextFloat() * 0.15f);
		if (getState() == PATROL && !isProwling()) {
			startProwl(player.position());
		}
	}

	private void investigate(Vec3 pos, float loudness, boolean fromPlayer) {
		if (getState() == INVESTIGATE && soundTarget != null && loudness < soundLoudness * 0.6f && stateTicks < 200) {
			return;
		}
		boolean wasCalm = getState() == PATROL;
		soundTarget = pos;
		soundLoudness = loudness;
		soundFromPlayer = fromPlayer;
		soundVersion++;
		prowlCenter = null;
		setState(INVESTIGATE);
		updateDoorPathing();
		stateTicks = 0;
		if (wasCalm) {
			// Aviso: o clique das orelhas virando para o som.
			playSound(ModSounds.LISTENER_CLICK, 1.6f, 0.8f + random.nextFloat() * 0.3f);
		}
	}

	private void startAlert(ServerPlayer player) {
		alertTarget = player;
		soundTarget = player.position();
		prowlCenter = null;
		setState(ALERT);
		getNavigation().stop();
		// Regra de ouro: sempre um aviso sonoro antes do ataque.
		float pitch = 0.9f + random.nextFloat() * 0.2f;
		level().playSound(null, getX(), getY(), getZ(), ModSounds.LISTENER_ALERT, SoundSource.HOSTILE, 2.5f, pitch);
		if (distanceTo(player) > 32) {
			// Longe demais para o rugido chegar: o alvo ouve um eco dele vindo da direção certa.
			Vec3 dir = position().subtract(player.position()).normalize().scale(12);
			player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.LISTENER_ALERT),
					SoundSource.HOSTILE, player.getX() + dir.x, player.getEyeY() + dir.y, player.getZ() + dir.z, 0.8f, pitch, random.nextLong()));
		}
	}

	private void startHunt(ServerPlayer player) {
		setTarget(player);
		setState(HUNT);
		lastHeardTarget = level().getGameTime();
		stuckTicks = 0;
	}

	private void loseTrack() {
		LivingEntity target = getTarget();
		setTarget(null);
		alertTarget = null;
		if (target != null && target.isAlive() && target.level() == level()) {
			// Vai até o último lugar onde ouviu a presa, fareja e ronda por lá.
			soundTarget = target.position();
			soundLoudness = 0;
			soundFromPlayer = true;
			soundVersion++;
			setState(INVESTIGATE);
		} else {
			setState(PATROL);
		}
		playSound(ModSounds.LISTENER_SNIFF, 1.2f, 0.8f);
	}

	private void startProwl(Vec3 center) {
		soundTarget = null;
		prowlCenter = center;
		prowlUntil = level().getGameTime() + PROWL_TICKS;
		setState(PATROL);
	}

	private boolean isHabituatedTo(Vec3 pos) {
		long now = level().getGameTime();
		habituations.removeIf(h -> h.expires() < now);
		for (Habituation h : habituations) {
			if (h.visits() >= HABITUATION_VISITS && h.pos().closerThan(pos, HABITUATION_RADIUS)) {
				return true;
			}
		}
		return false;
	}

	private void rememberFruitlessVisit(Vec3 pos) {
		long now = level().getGameTime();
		for (int i = 0; i < habituations.size(); i++) {
			Habituation h = habituations.get(i);
			if (h.pos().closerThan(pos, HABITUATION_RADIUS)) {
				habituations.set(i, new Habituation(h.pos(), h.visits() + 1, now + HABITUATION_TICKS));
				return;
			}
		}
		habituations.add(new Habituation(pos, 1, now + HABITUATION_TICKS));
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level) || !isAlive()) {
			return;
		}
		// Fora da IA (vale até para Ouvintes com NoAI): ao amanhecer ele se enterra.
		if (!NightCycle.isNight(level)) {
			burrow(level);
			return;
		}
		if (tickCount % 20 == 0) {
			if (!claimUniqueness(level)) {
				return;
			}
			if (checkLost(level)) {
				return;
			}
		}
		if (tickCount % 10 == 0) {
			updateBossBar(level);
		}
	}

	/**
	 * Só existe um Ouvinte. Se outro já é "o" Ouvinte e está carregado, este some; se não há
	 * nenhum, este assume o posto (ex.: um Ouvinte antigo cujo chunk acabou de carregar, ou um ovo gerador).
	 */
	private boolean claimUniqueness(ServerLevel level) {
		MinecraftServer server = level.getServer();
		ListenerState state = ListenerSpawner.state(server);
		long night = NightCycle.day(level);
		if (state.isCurrent(getUUID())) {
			ListenerSpawner.setState(server, state.withHealth(getHealth()));
			return true;
		}
		Entity current = state.current().map(level::getEntity).orElse(null);
		if (current instanceof Listener other && other.isAlive() && other != this) {
			discard();
			return false;
		}
		ListenerSpawner.setState(server, state.withCurrent(getUUID(), night, getHealth()));
		return true;
	}

	/**
	 * Se ele está patrulhando e ninguém está ao alcance (longe demais ou numa camada diferente,
	 * tipo ele numa caverna e você num morro), se enterra; o {@link ListenerSpawner} o faz ressurgir
	 * perto de um jogador com a mesma vida.
	 */
	private boolean checkLost(ServerLevel level) {
		if (getState() != PATROL || isProwling()) {
			lostTicks = 0;
			return false;
		}
		boolean someoneNear = false;
		for (ServerPlayer player : level.players()) {
			if (isHuntable(player) && distanceTo(player) <= LOST_DISTANCE && Math.abs(player.getY() - getY()) <= LOST_HEIGHT) {
				someoneNear = true;
				break;
			}
		}
		lostTicks = someoneNear ? 0 : lostTicks + 20;
		if (lostTicks >= REPOSITION_TICKS) {
			burrow(level);
			return true;
		}
		return false;
	}

	private void updateBossBar(ServerLevel level) {
		bossEvent.setProgress(getHealth() / getMaxHealth());
		for (ServerPlayer player : level.players()) {
			boolean near = player.isAlive() && distanceToSqr(player) <= BOSS_BAR_RADIUS * BOSS_BAR_RADIUS;
			if (near && !bossEvent.getPlayers().contains(player)) {
				bossEvent.addPlayer(player);
			} else if (!near && bossEvent.getPlayers().contains(player)) {
				bossEvent.removePlayer(player);
			}
		}
		for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
			if (player.level() != level || player.isRemoved()) {
				bossEvent.removePlayer(player);
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		bossEvent.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (isStunned()) {
			getNavigation().stop();
			if (tickCount % 8 == 0) {
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 2.8, getZ(), 4, 0.4, 0.2, 0.4, 0.05);
			}
			if (level.getGameTime() >= stunnedUntil) {
				entityData.set(STUNNED, false);
				Vec3 source = stunSource;
				stunSource = null;
				if (source != null) {
					investigate(source, 40, false);
				}
			}
			return;
		}
		stateTicks++;
		if (isSuspicious() && (level.getGameTime() > suspiciousUntil || getState() == ALERT || getState() == HUNT)) {
			entityData.set(SUSPICIOUS, false);
		}

		switch (getState()) {
			case ALERT -> {
				getNavigation().stop();
				if (alertTarget != null) {
					getLookControl().setLookAt(alertTarget.getX(), alertTarget.getEyeY(), alertTarget.getZ());
				}
				if (stateTicks >= ALERT_TICKS) {
					if (alertTarget != null && isHuntable(alertTarget)) {
						startHunt(alertTarget);
					} else {
						setState(PATROL);
					}
					alertTarget = null;
				}
			}
			case HUNT -> tickHunt(level);
			default -> {
				stuckTicks = 0;
				if (tickCount % 5 == 0) {
					senseNearby(level);
				}
				// Fora da caçada ele se recupera devagar: bater e fugir não funciona.
				if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
					heal(1.0f);
				}
			}
		}
	}

	private void tickHunt(ServerLevel level) {
		if (!(getTarget() instanceof ServerPlayer target) || !isHuntable(target)) {
			loseTrack();
			return;
		}
		boolean touching = distanceToSqr(target) < 2.5 * 2.5;
		if (touching) {
			lastHeardTarget = level.getGameTime();
		}
		if (level.getGameTime() - lastHeardTarget > LOSE_TRACK_TICKS) {
			loseTrack();
			return;
		}
		// Encurralado: quer chegar na presa e não consegue sair do lugar.
		Vec3 pos = position();
		if (lastStuckCheck != null && pos.distanceToSqr(lastStuckCheck) < 0.0025 && !touching) {
			stuckTicks++;
		} else {
			stuckTicks = Math.max(0, stuckTicks - 2);
		}
		lastStuckCheck = pos;
	}

	private boolean isHuntable(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator() && player.level() == level();
	}

	public boolean isCornered() {
		return stuckTicks >= CORNERED_TICKS;
	}

	/** Ao amanhecer ele se enterra e some. */
	private void burrow(ServerLevel level) {
		BlockState ground = level.getBlockState(getOnPos());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2, getZ(), 40, 0.4, 0.2, 0.4, 0.1);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 10, 0.3, 0.6, 0.3, 0.01);
		level.playSound(null, getX(), getY(), getZ(), ModSounds.LISTENER_BURROW, SoundSource.HOSTILE, 1.5f, 1.0f);
		discard();
	}

	// ------------------------------------------------------------------ combate

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		boolean surprised = attacker instanceof Player && (getState() == PATROL || getState() == INVESTIGATE || isCornered() || isStunned());
		if (surprised) {
			damage *= SURPRISE_MULTIPLIER;
			level.sendParticles(ParticleTypes.CRIT, getX(), getY(1.0), getZ(), 15, 0.3, 0.4, 0.3, 0.2);
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && !isStunned() && attacker instanceof ServerPlayer player && isHuntable(player)) {
			// Quem bate denuncia onde está: caça imediata.
			alertTarget = null;
			prowlCenter = null;
			startHunt(player);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		playSound(ModSounds.LISTENER_ATTACK, 1.5f, 0.9f + random.nextFloat() * 0.2f);
		return super.doHurtTarget(level, target);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level && ListenerSpawner.state(level.getServer()).isCurrent(getUUID())) {
			ListenerSpawner.onKilled(level);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		spawnAtLocation(level, new ItemStack(ModItems.LISTENER_EAR));
	}

	@Override
	public int getMaxFallDistance() {
		// Cego, não percebe o tamanho da queda. É assim que caem em armadilhas.
		return 12;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		// Nada de sumir do nada a 40 blocos. Longe de todo mundo ele ressurge perto de alguém (ListenerSpawner).
		return distSqr > 128 * 128;
	}

	// ------------------------------------------------------------------ sons

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return getState() == HUNT ? ModSounds.LISTENER_GROWL : ModSounds.LISTENER_CLICK;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 100;
	}

	@Override
	protected float getSoundVolume() {
		return 1.4f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.LISTENER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.LISTENER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState blockState) {
		playSound(ModSounds.LISTENER_STEP, getState() == HUNT ? 0.7f : 0.35f, 1.0f);
	}

	// ------------------------------------------------------------------ goals

	/**
	 * Arromba portas de madeira no caminho quando vai atrás de quem fez barulho (porta de ferro
	 * aguenta). Bem mais rápido que o zumbi: {@link #DOOR_BREAK_TICKS}.
	 */
	private static class DoorBashGoal extends BreakDoorGoal {
		private final Listener listener;

		DoorBashGoal(Listener listener) {
			super(listener, difficulty -> true);
			this.listener = listener;
		}

		@Override
		protected int getDoorBreakTime() {
			return DOOR_BREAK_TICKS;
		}

		@Override
		public boolean canUse() {
			int state = listener.getState();
			boolean afterPlayer = state == HUNT || state == INVESTIGATE && listener.soundFromPlayer;
			if (!afterPlayer || listener.isStunned() || !listener.horizontalCollision) {
				return false;
			}
			if (!(listener.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
				return false;
			}
			// A checagem do jogo base exige estar colado na porta (feita para o zumbi, mais fino).
			// Ele é largo, então procura uma porta de madeira nos próximos passos do caminho, até 2,5 blocos.
			Path path = listener.getNavigation().getPath();
			if (path == null || path.isDone()) {
				return false;
			}
			int from = Math.max(0, path.getNextNodeIndex() - 1);
			int to = Math.min(path.getNextNodeIndex() + 3, path.getNodeCount());
			for (int i = from; i < to; i++) {
				Node node = path.getNode(i);
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos pos = new BlockPos(node.x, node.y + dy, node.z);
					if (DoorBlock.isWoodenDoor(level, pos) && Vec3.atCenterOf(pos).closerThan(listener.position(), 2.5)) {
						doorPos = pos;
						hasDoor = true;
						return !isOpen();
					}
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return !listener.isStunned() && super.canContinueToUse();
		}
	}

	/** Caça: ataque corpo a corpo seguindo o alvo mesmo sem "ver" (ele nunca vê). */
	private static class HuntGoal extends MeleeAttackGoal {
		private final Listener listener;

		HuntGoal(Listener listener) {
			super(listener, HUNT_SPEED, true);
			this.listener = listener;
		}

		@Override
		public boolean canUse() {
			return listener.getState() == HUNT && !listener.isStunned() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return listener.getState() == HUNT && !listener.isStunned() && super.canContinueToUse();
		}
	}

	/** Vai até a origem do som, fareja um pouco e fica rondando o lugar. */
	private static class InvestigateGoal extends Goal {
		private static final int SNIFF_TICKS = 50;
		private static final int GIVE_UP_TICKS = 400;
		private final Listener listener;
		private int version = -1;
		private int sniffTicks = -1;
		private int repathCooldown;

		InvestigateGoal(Listener listener) {
			this.listener = listener;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return listener.getState() == INVESTIGATE && listener.soundTarget != null && !listener.isStunned();
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			version = -1;
			sniffTicks = -1;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Vec3 target = listener.soundTarget;
			if (target == null) {
				return;
			}
			if (version != listener.soundVersion) {
				version = listener.soundVersion;
				sniffTicks = -1;
				repathCooldown = 0;
			}
			if (sniffTicks >= 0) {
				sniff(target);
				return;
			}
			double dx = target.x - listener.getX();
			double dz = target.z - listener.getZ();
			boolean arrived = dx * dx + dz * dz < 2.5 * 2.5;
			if (arrived || listener.stateTicks > GIVE_UP_TICKS || listener.getNavigation().isDone() && repathCooldown > 0) {
				listener.getNavigation().stop();
				sniffTicks = 0;
				listener.playSound(ModSounds.LISTENER_SNIFF, 1.0f, 0.9f + listener.getRandom().nextFloat() * 0.2f);
				return;
			}
			if (--repathCooldown <= 0) {
				repathCooldown = 20;
				listener.getNavigation().moveTo(target.x, target.y, target.z, INVESTIGATE_SPEED);
			}
			listener.getLookControl().setLookAt(target.x, target.y + 1, target.z);
		}

		private void sniff(Vec3 target) {
			sniffTicks++;
			if (sniffTicks % 15 == 1) {
				float yaw = listener.getYRot() + (listener.getRandom().nextFloat() - 0.5f) * 180f;
				listener.getLookControl().setLookAt(
						listener.getX() + Mth.sin(-yaw * Mth.DEG_TO_RAD) * 4, listener.getEyeY() - 0.5,
						listener.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * 4);
			}
			if (sniffTicks >= SNIFF_TICKS) {
				if (!listener.soundFromPlayer) {
					listener.rememberFruitlessVisit(target);
				}
				listener.startProwl(target);
			}
		}
	}

	/** Ronda: depois de farejar, anda em volta do lugar do som por um tempo antes de ir embora. */
	private static class ProwlGoal extends Goal {
		private final Listener listener;
		private int pause;

		ProwlGoal(Listener listener) {
			this.listener = listener;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return listener.isProwling() && !listener.isStunned();
		}

		@Override
		public boolean canContinueToUse() {
			return listener.isProwling() && !listener.isStunned();
		}

		@Override
		public void stop() {
			listener.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (!listener.getNavigation().isDone() || --pause > 0) {
				return;
			}
			Vec3 center = listener.prowlCenter;
			if (center == null) {
				return;
			}
			var random = listener.getRandom();
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 2 + random.nextDouble() * (PROWL_RADIUS - 2);
			listener.getNavigation().moveTo(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, PATROL_SPEED + 0.15);
			pause = 10 + random.nextInt(30);
		}
	}

	/**
	 * Patrulha: ele não sabe onde você está, mas vai rondando a sua região. A cada trecho anda no
	 * máximo {@link #PATROL_LEG} blocos na direção de um ponto qualquer a 6–18 blocos do jogador
	 * mais próximo, então vai chegando aos poucos e às vezes passa bem perto. Sem jogador por perto,
	 * anda à toa.
	 */
	private static class PatrolGoal extends RandomStrollGoal {
		private static final double PATROL_LEG = 10;
		private static final double AREA_RANGE = 128;
		private final Listener listener;

		PatrolGoal(Listener listener) {
			// checkNoActionTime=false: a patrulha padrão para quando ninguém está a menos de 32 blocos,
			// e era exatamente aí que ele surgia, parado esperando barulho.
			super(listener, PATROL_SPEED, 40, false);
			this.listener = listener;
		}

		@Override
		public boolean canUse() {
			return listener.getState() == PATROL && !listener.isProwling() && !listener.isStunned() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return listener.getState() == PATROL && !listener.isProwling() && !listener.isStunned() && super.canContinueToUse();
		}

		@Override
		protected @Nullable Vec3 getPosition() {
			Player player = listener.level().getNearestPlayer(listener.getX(), listener.getY(), listener.getZ(), AREA_RANGE,
					p -> p instanceof ServerPlayer sp && listener.isHuntable(sp));
			if (player == null) {
				return LandRandomPos.getPos(listener, 10, 7);
			}
			var random = listener.getRandom();
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 6 + random.nextDouble() * 12;
			Vec3 area = player.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
			Vec3 step = area.subtract(listener.position());
			double length = step.horizontalDistance();
			if (length > PATROL_LEG) {
				step = step.scale(PATROL_LEG / length);
			}
			Vec3 target = listener.position().add(step.x, 0, step.z);
			int y = listener.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					Mth.floor(target.x), Mth.floor(target.z));
			// Em caverna (jogador bem abaixo da superfície), mantém a altura atual em vez de subir.
			double targetY = Math.abs(y - listener.getY()) > 6 ? listener.getY() : y;
			return new Vec3(target.x, targetY, target.z);
		}
	}
}
