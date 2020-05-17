package io.github.joaoh1.okzoomer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.joaoh1.okzoomer.OkZoomerMod;
import io.github.joaoh1.okzoomer.config.DoNotCommitBad;
import io.github.joaoh1.okzoomer.config.OkZoomerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;

//This mixin is responsible for managing the fov-changing part of the zoom.
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	private final MinecraftClient client = MinecraftClient.getInstance();

	private float zoomFovMultiplier;
	private float lastZoomFovMultiplier;

	//The equivalent of updateFovMultiplier but for zooming. Used by smooth transitions.
	private void updateZoomFovMultiplier() {
		float zoomMultiplier = 1.0F;
		float multiplierMultiplier = 0.75F;

		if (OkZoomerMod.isZoomKeyPressed) {
			zoomMultiplier /= OkZoomerMod.zoomDivisor;
			if (OkZoomerMod.zoomDivisor > 1.0) {
				multiplierMultiplier = 1.0F - zoomMultiplier;
			}
		}

		this.lastZoomFovMultiplier = this.zoomFovMultiplier;
		this.zoomFovMultiplier += (zoomMultiplier - this.zoomFovMultiplier) * multiplierMultiplier;
	}

	//If smooth transitions are enabled, update the zoom multiplier on each tick.
	@Inject(at = @At("HEAD"), method = "tick()V")
	private void zoomFovMultiplierTick(CallbackInfo info) {
		if (DoNotCommitBad.getZoomTransition().equals("smooth")) {
			this.updateZoomFovMultiplier();
		}
	}
	
	//Handles zooming of both modes (Transitionless and with Smooth Transitions).
	@Inject(at = @At("RETURN"), method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", cancellable = true)
	private double getZoomedFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> info) {
		double fov = info.getReturnValueD();

		if (DoNotCommitBad.getZoomTransition().equals("smooth")) {
			//Handle the zoom with smooth transitions enabled.
			if (this.zoomFovMultiplier != 1.0F) {
				fov *= (double)MathHelper.lerp(tickDelta, this.lastZoomFovMultiplier, this.zoomFovMultiplier);
				info.setReturnValue(fov);
			}
		} else {
			//Handle the zoom without smooth transitions.
			if (OkZoomerMod.isZoomKeyPressed) {
				double zoomedFov = fov / OkZoomerMod.zoomDivisor;
				info.setReturnValue(zoomedFov);
			}
		}

		//Regardless of the mode, if the zoom is over, update the terrain in order to stop terrain glitches.
		if (OkZoomerMod.zoomHasHappened) {
			if (changingFov) {
				this.client.worldRenderer.scheduleTerrainUpdate();
			}
		}

		return fov;
	}
}
