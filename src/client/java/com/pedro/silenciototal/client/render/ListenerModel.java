package com.pedro.silenciototal.client.render;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.entity.Listener;
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
 * Corpo magro e alto, braços compridos, cabeça sem olhos e duas orelhas enormes em leque.
 * As orelhas contam o estado: mexem à toa na patrulha, ficam em pé tremendo ao investigar e
 * coladas para trás na caçada.
 */
public class ListenerModel extends EntityModel<ListenerRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(SilencioTotal.id("listener"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart leftEar;
	private final ModelPart rightEar;
	private final ModelPart jaw;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public ListenerModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.leftEar = head.getChild("left_ear");
		this.rightEar = head.getChild("right_ear");
		this.jaw = head.getChild("jaw");
		this.leftArm = body.getChild("left_arm");
		this.rightArm = body.getChild("right_arm");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Tronco: de y=1 (pescoço) a y=13 (quadril), meio curvado para a frente.
		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 14).addBox(-4.0f, 0.0f, -2.5f, 8, 12, 5),
				PartPose.offsetAndRotation(0.0f, 1.0f, 0.0f, 0.12f, 0.0f, 0.0f));

		PartDefinition head = body.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -7.0f, -4.0f, 7, 7, 7),
				PartPose.offset(0.0f, 0.0f, -0.5f));
		head.addOrReplaceChild("jaw",
				CubeListBuilder.create().texOffs(0, 31).addBox(-3.0f, 0.0f, -4.0f, 6, 2, 6),
				PartPose.offset(0.0f, -1.0f, 0.0f));
		head.addOrReplaceChild("left_ear",
				CubeListBuilder.create().texOffs(28, 0).addBox(0.0f, -9.0f, -3.0f, 1, 9, 6),
				PartPose.offsetAndRotation(3.0f, -4.0f, 0.0f, 0.0f, 0.0f, 0.55f));
		head.addOrReplaceChild("right_ear",
				CubeListBuilder.create().texOffs(28, 0).mirror().addBox(-1.0f, -9.0f, -3.0f, 1, 9, 6),
				PartPose.offsetAndRotation(-3.0f, -4.0f, 0.0f, 0.0f, 0.0f, -0.55f));

		body.addOrReplaceChild("left_arm",
				CubeListBuilder.create().texOffs(28, 15).addBox(-1.0f, -1.0f, -1.5f, 3, 17, 3),
				PartPose.offset(5.0f, 1.5f, 0.0f));
		body.addOrReplaceChild("right_arm",
				CubeListBuilder.create().texOffs(28, 15).mirror().addBox(-2.0f, -1.0f, -1.5f, 3, 17, 3),
				PartPose.offset(-5.0f, 1.5f, 0.0f));

		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(40, 15).addBox(-1.5f, 0.0f, -1.5f, 3, 11, 3),
				PartPose.offset(2.2f, 13.0f, 0.5f));
		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(40, 15).mirror().addBox(-1.5f, 0.0f, -1.5f, 3, 11, 3),
				PartPose.offset(-2.2f, 13.0f, 0.5f));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(ListenerRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float walkPos = state.walkAnimationPos;
		float walkSpeed = state.walkAnimationSpeed;

		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;

		float legSwing = Mth.cos(walkPos * 0.6662f) * 1.3f * walkSpeed;
		rightLeg.xRot = legSwing;
		leftLeg.xRot = -legSwing;
		rightArm.xRot = -legSwing * 0.6f;
		leftArm.xRot = legSwing * 0.6f;
		// Braços pendurados balançando de leve, como quem tateia.
		rightArm.zRot = 0.08f + Mth.sin(age * 0.07f) * 0.04f;
		leftArm.zRot = -0.08f - Mth.sin(age * 0.07f) * 0.04f;

		int mode = state.suspicious && (state.mode == Listener.PATROL || state.mode == Listener.INVESTIGATE) ? -1 : state.mode;
		switch (mode) {
			case -1 -> {
				// Desconfiado: orelhas retas para cima e viradas para a frente, tremendo rápido, boca entreaberta.
				float tremble = Mth.sin(age * 3.1f) * 0.05f;
				leftEar.zRot = 0.05f + tremble;
				rightEar.zRot = -0.05f - tremble;
				leftEar.yRot = -0.7f;
				rightEar.yRot = 0.7f;
				leftEar.xRot = -0.25f;
				rightEar.xRot = -0.25f;
				jaw.xRot = 0.2f;
				rightArm.zRot = 0.25f;
				leftArm.zRot = -0.25f;
			}
			case Listener.INVESTIGATE -> {
				// Orelhas em pé, viradas para a frente, tremendo.
				float flutter = Mth.sin(age * 1.7f) * 0.06f;
				leftEar.zRot = 0.2f + flutter;
				rightEar.zRot = -0.2f - flutter;
				leftEar.yRot = -0.45f;
				rightEar.yRot = 0.45f;
				head.xRot -= 0.15f;
				jaw.xRot = 0.1f;
			}
			case Listener.ALERT -> {
				// Para, abre a boca e ergue os braços: é o aviso.
				leftEar.zRot = 0.1f;
				rightEar.zRot = -0.1f;
				head.xRot = -0.5f;
				jaw.xRot = 0.6f + Mth.sin(age * 2.0f) * 0.1f;
				rightArm.xRot = -1.6f;
				leftArm.xRot = -1.6f;
				rightArm.zRot = 0.4f;
				leftArm.zRot = -0.4f;
			}
			case Listener.HUNT -> {
				// Orelhas coladas para trás, braços esticados para a frente, boca aberta.
				leftEar.zRot = 1.1f;
				rightEar.zRot = -1.1f;
				leftEar.xRot = 0.6f;
				rightEar.xRot = 0.6f;
				jaw.xRot = 0.35f + Mth.sin(age * 0.8f) * 0.1f;
				rightArm.xRot = -1.3f + Mth.cos(walkPos * 0.6662f) * 0.4f * walkSpeed;
				leftArm.xRot = -1.3f - Mth.cos(walkPos * 0.6662f) * 0.4f * walkSpeed;
				body.xRot = 0.35f;
			}
			default -> {
				// Patrulha: orelhas balançando à toa, com uma "virada" rápida de vez em quando.
				float idle = Mth.sin(age * 0.12f) * 0.12f;
				float flick = (age % 70f) < 6f ? Mth.sin(age * 2.2f) * 0.35f : 0f;
				leftEar.zRot = 0.55f + idle + flick;
				rightEar.zRot = -0.55f - idle + (age % 90f < 6f ? -Mth.sin(age * 2.2f) * 0.35f : 0f);
				leftEar.yRot = Mth.sin(age * 0.05f) * 0.25f;
				rightEar.yRot = -Mth.sin(age * 0.05f + 1.0f) * 0.25f;
			}
		}
	}
}
