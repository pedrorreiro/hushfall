package com.pedro.silenciototal.client.render;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.entity.ListenerPup;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Versão pequena do Ouvinte: anda de quatro apoiado nos braços compridos, cabeça grande sem olhos
 * e orelhas enormes. Mesma geometria e poses da prévia em tools/filhote/template.html.
 */
public class ListenerPupModel extends EntityModel<ListenerPupRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(SilencioTotal.id("listener_pup"), "main");

	/** Duração de cada pulinho batendo as patas, em ticks. */
	private static final float HOP = 11;
	private static final int HOPS = 3;

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart leftEar;
	private final ModelPart rightEar;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public ListenerPupModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = root.getChild("head");
		this.leftEar = head.getChild("left_ear");
		this.rightEar = head.getChild("right_ear");
		this.leftArm = root.getChild("left_arm");
		this.rightArm = root.getChild("right_arm");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Pivô na frente-baixo do tronco: ao sentar, só o traseiro desce.
		root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 16).addBox(-2.5f, -4.0f, 0.0f, 5, 4, 6),
				PartPose.offsetAndRotation(0.0f, 21.0f, -2.5f, -0.06f, 0.0f, 0.0f));
		PartDefinition head = root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -5.0f, -4.0f, 6, 5, 5),
				PartPose.offset(0.0f, 17.0f, -2.5f));
		head.addOrReplaceChild("jaw",
				CubeListBuilder.create().texOffs(0, 10).addBox(-2.5f, 0.0f, -4.5f, 5, 1, 4),
				PartPose.offset(0.0f, -0.6f, 0.0f));
		head.addOrReplaceChild("left_ear",
				CubeListBuilder.create().texOffs(22, 0).addBox(0.0f, -6.0f, -2.0f, 1, 6, 4),
				PartPose.offsetAndRotation(2.5f, -3.5f, -1.0f, 0.0f, 0.0f, 0.6f));
		head.addOrReplaceChild("right_ear",
				CubeListBuilder.create().texOffs(22, 0).mirror().addBox(-1.0f, -6.0f, -2.0f, 1, 6, 4),
				PartPose.offsetAndRotation(-2.5f, -3.5f, -1.0f, 0.0f, 0.0f, -0.6f));

		root.addOrReplaceChild("left_arm",
				CubeListBuilder.create().texOffs(22, 10).addBox(-1.0f, 0.0f, -1.0f, 2, 6, 2),
				PartPose.offset(3.0f, 18.0f, -2.0f));
		root.addOrReplaceChild("right_arm",
				CubeListBuilder.create().texOffs(22, 10).mirror().addBox(-1.0f, 0.0f, -1.0f, 2, 6, 2),
				PartPose.offset(-3.0f, 18.0f, -2.0f));
		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(22, 18).addBox(-1.0f, 0.0f, -1.0f, 2, 3, 2),
				PartPose.offset(2.0f, 21.0f, 2.5f));
		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(22, 18).mirror().addBox(-1.0f, 0.0f, -1.0f, 2, 3, 2),
				PartPose.offset(-2.0f, 21.0f, 2.5f));

		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(ListenerPupRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float t = state.actionTime;

		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;

		// Orelhas mexendo à toa, com uma sacudida de vez em quando.
		float idle = Mth.sin(age * 0.12f) * 0.12f;
		float flick = age % 60 < 5 ? Mth.sin(age * 2.4f) * 0.35f : 0;
		leftEar.zRot = 0.6f + idle + flick;
		rightEar.zRot = -0.6f - idle;
		leftEar.yRot = Mth.sin(age * 0.05f) * 0.25f;
		rightEar.yRot = -Mth.sin(age * 0.05f + 1) * 0.25f;
		body.yScale = 1 + Mth.sin(age * 0.1f) * 0.025f;

		// Anda de quatro: braço de um lado com a perna do outro.
		float walk = Mth.cos(state.walkAnimationPos * 0.6662f * 1.5f) * 1.2f * state.walkAnimationSpeed;
		leftArm.xRot = walk;
		rightLeg.xRot = walk;
		rightArm.xRot = -walk;
		leftLeg.xRot = -walk;

		boolean hopping = false;
		switch (state.action) {
			case ListenerPup.LISTEN -> listenPose(age, true);
			case ListenerPup.TAP, ListenerPup.MARK -> {
				int hop = (int) (t / HOP);
				if (hop < HOPS) {
					hop(Mth.sin(Mth.PI * (t % HOP) / HOP));
					hopping = true;
				} else {
					listenPose(age, state.action == ListenerPup.TAP);
					head.xRot = 0.35f;
				}
			}
			case ListenerPup.NO -> {
				// Orelhas caídas, balança a cabeça e vai parando.
				float k = Math.max(0, 1 - t / 30);
				head.yRot += Mth.sin(t * 1.4f) * 0.55f * k;
				head.xRot = 0.25f;
				leftEar.zRot = 1.25f;
				rightEar.zRot = -1.25f;
				leftEar.xRot = 0.5f;
				rightEar.xRot = 0.5f;
				leftEar.yRot = 0;
				rightEar.yRot = 0;
			}
			default -> {
			}
		}
		if (state.sitting && !hopping) {
			sitPose();
		}
	}

	/** Cabeça inclinada e orelhas em pé viradas para a frente, tremendo: ouvindo o eco. */
	private void listenPose(float age, boolean tilt) {
		float tremble = Mth.sin(age * 3.1f) * 0.05f;
		head.zRot = tilt ? 0.38f : 0;
		head.xRot = -0.1f;
		leftEar.zRot = 0.05f + tremble;
		rightEar.zRot = -0.05f - tremble;
		leftEar.yRot = -0.7f;
		rightEar.yRot = 0.7f;
		leftEar.xRot = -0.25f;
		rightEar.xRot = -0.25f;
	}

	/** Um pulinho batendo as duas patas da frente; {@code air} vai de 0 (no chão) a 1 (no alto). */
	private void hop(float air) {
		float lift = -air * 3.5f;
		body.y += lift;
		head.y += lift;
		leftArm.y += lift;
		rightArm.y += lift;
		leftLeg.y += lift;
		rightLeg.y += lift;
		leftArm.xRot = -1.1f * air;
		rightArm.xRot = -1.1f * air;
		leftArm.zRot = 0.25f * air;
		rightArm.zRot = -0.25f * air;
		leftLeg.xRot = 0.5f * air;
		rightLeg.xRot = 0.5f * air;
		body.xRot = -0.06f - 0.25f * air;
		head.xRot = 0.25f - 0.3f * air;
		leftEar.zRot = 0.35f - 0.3f * air;
		rightEar.zRot = -0.35f + 0.3f * air;
		leftEar.xRot = 0.5f * air;
		rightEar.xRot = 0.5f * air;
	}

	/** Senta nas patas de trás: o traseiro desce até o chão, peito e cabeça ficam onde estão. */
	private void sitPose() {
		body.xRot = -0.5f;
		leftLeg.xRot = -1.45f;
		rightLeg.xRot = -1.45f;
		leftLeg.y += 2.3f;
		rightLeg.y += 2.3f;
		leftLeg.z += 0.4f;
		rightLeg.z += 0.4f;
		leftArm.xRot = 0;
		rightArm.xRot = 0;
		head.xRot -= 0.15f;
	}
}
