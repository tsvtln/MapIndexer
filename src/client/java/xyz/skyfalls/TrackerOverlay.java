package xyz.skyfalls;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypeRegistrar;
import xyz.skyfalls.shared.utils.RegistryUtils;

public class TrackerOverlay {
	private static boolean enabled = false;
	private static RegistryKey<World> dimension;
	private static BlockPos pos;

	public static void clear() {
		enabled = false;
		dimension = null;
		pos = null;
		MapIndexerClient.sendChatMessage(Text.literal("§aTracker removed"));
	}

	public static void clear(BlockPos clearAt, ClientWorld world) {
		if (clearAt.equals(pos) && world.getRegistryKey().equals(dimension)) {
			clear();
		}
	}

	public static void track(String newDim, BlockPos newPos) {
		dimension = switch (newDim) {
			case "nether" -> World.NETHER;
			case "the_nether" -> World.NETHER;
			case "end" -> World.END;
			case "the_end" -> World.END;
			case "overworld" -> World.OVERWORLD;
			default -> throw new RuntimeException("Unexpected dimension name: " + newDim);
		};
		pos = newPos;
		enabled = true;
		MapIndexerClient.sendChatMessage(
				Text.literal("Now tracking [%d %d %d] in %s".formatted(
						pos.getX(), pos.getY(), pos.getZ(), RegistryUtils.toString(dimension))));
	}

	public static Map<BlockPos, BoxOverlayRenderer.Style> getOverlay() {
		if (!enabled) {
			return Map.of();
		}
		var curDim = MinecraftClient.getInstance().world.getRegistryKey();
		if (curDim.equals(dimension)) {
			return Map.of(pos, BoxOverlayRenderer.Style.TRACKER);
		} else if (curDim.equals(World.OVERWORLD) && dimension.equals(World.NETHER)) {
			var converted = new BlockPos(pos.getX() * 8, pos.getY(), pos.getZ() * 8);
			return Map.of(converted, BoxOverlayRenderer.Style.TRACKER);
		} else if (curDim.equals(World.NETHER) && dimension.equals(World.OVERWORLD)) {
			var converted = new BlockPos(pos.getX() / 8, pos.getY(), pos.getZ() / 8);
			return Map.of(converted, BoxOverlayRenderer.Style.TRACKER);
		}
		return Map.of();
	}
}
