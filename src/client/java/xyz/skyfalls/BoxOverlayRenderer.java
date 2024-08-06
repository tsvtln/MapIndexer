package xyz.skyfalls;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;

import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL40;

import java.util.HashMap;
import java.util.Map;

public class BoxOverlayRenderer {
	private static VertexBuffer unitBox = null;

	public static void initialize() {
		unitBox = new VertexBuffer(VertexBuffer.Usage.STATIC);
		var tess = Tessellator.getInstance();
		var buffer = tess.getBuffer();
		buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		buffer.fixedColor(255, 255, 255, 255);

		// X-Y
		buffer.vertex(0, 1, 0).next();
		buffer.vertex(1, 1, 0).next();
		buffer.vertex(1, 0, 0).next();
		buffer.vertex(0, 0, 0).next();

		buffer.vertex(0, 1, 1).next();
		buffer.vertex(0, 0, 1).next();
		buffer.vertex(1, 0, 1).next();
		buffer.vertex(1, 1, 1).next();
		// X-Z
		buffer.vertex(0, 0, 0).next();
		buffer.vertex(1, 0, 0).next();
		buffer.vertex(1, 0, 1).next();
		buffer.vertex(0, 0, 1).next();

		buffer.vertex(0, 1, 1).next();
		buffer.vertex(1, 1, 1).next();
		buffer.vertex(1, 1, 0).next();
		buffer.vertex(0, 1, 0).next();
		// Y-Z
		buffer.vertex(0, 0, 1).next();
		buffer.vertex(0, 1, 1).next();
		buffer.vertex(0, 1, 0).next();
		buffer.vertex(0, 0, 0).next();

		buffer.vertex(1, 0, 0).next();
		buffer.vertex(1, 1, 0).next();
		buffer.vertex(1, 1, 1).next();
		buffer.vertex(1, 0, 1).next();
		buffer.unfixColor();

		unitBox.bind();
		unitBox.upload(buffer.end());
		VertexBuffer.unbind();
	}

	public static void onLast(WorldRenderContext ctx) {
		if (!MapIndexerClient.isOnS26()) {
			return;
		}
		if (unitBox == null) {
			// fingers crossed this only gets called on the render thread
			initialize();
		}

		Map<BlockPos, BoxOverlayRenderer.Style> overlays = new HashMap<>();
		overlays.putAll(ShopSignOverlay.getOverlays());
		overlays.putAll(TrackerOverlay.getOverlay());

		var matrixStack = ctx.matrixStack();
		var camera = ctx.camera().getPos();

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		GL40.glEnable(GL40.GL_DEPTH_CLAMP);

		matrixStack.push();
		matrixStack.translate(-camera.getX(), -camera.getY(), -camera.getZ());

		unitBox.bind();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		overlays.forEach((pos, style) -> {
			RenderSystem.setShaderColor(style.red, style.green, style.blue, style.alpha);
			matrixStack.push();
			matrixStack.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (style.noscale()) {
				var factor = (float) (Math.max(0, camera.distanceTo(pos.toCenterPos()) - 10) * 0.1);
				factor = Math.max(factor, 1);
				matrixStack.scale(factor, factor, factor);
			}
			matrixStack.translate(-0.5, -0.5, -0.5);
			unitBox.draw(matrixStack.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(),
					RenderSystem.getShader());
			matrixStack.pop();
		});
		VertexBuffer.unbind();

		// reset
		matrixStack.pop();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL40.glDisable(GL40.GL_DEPTH_CLAMP);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_LINE_SMOOTH);
	}

	record Style(float red, float green, float blue, float alpha, boolean noscale) {
		static Style RED = new Style(1, 0, 0, 0.3f, false);
		static Style PURPLE = new Style(1, 0, 1, 0.3f, false);
		static Style GREEN = new Style(0, 1, 0, 0.15f, false);
		static Style YELLOW = new Style(1, 1, 0, 0.3f, false);
		static Style TRACKER = new Style(1, 0, 0.5f, 0.7f, true);
	}
}
