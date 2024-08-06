package xyz.skyfalls.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.LookingPosArgument;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.skyfalls.mixin.client.IDefaultPosArgument;

public class ClientBlockPosArgument implements ArgumentType<PosArgument> {
	private static final Collection<String> EXAMPLES = Arrays.asList("0 0 0", "~ ~ ~", "~0.5 ~1 ~-5");

	public static BlockPosArgumentType blockPos() {
		return new BlockPosArgumentType();
	}

	public static BlockPos getBlockPos(CommandContext<?> context, String name) {
		var arg = (IDefaultPosArgument) context.getArgument(name, DefaultPosArgument.class);
		var base = MinecraftClient.getInstance().player.getPos();
		return new BlockPos((int) arg.getX().toAbsoluteCoordinate(base.x),
				(int) arg.getY().toAbsoluteCoordinate(base.y),
				(int) arg.getZ().toAbsoluteCoordinate(base.z));
	}

	@Override
	public PosArgument parse(StringReader stringReader) throws CommandSyntaxException {
		return DefaultPosArgument.parse(stringReader);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		if (context.getSource() instanceof CommandSource) {
			String string = builder.getRemaining();
			Collection<CommandSource.RelativePosition> collection = !string.isEmpty() && string.charAt(0) == '^'
					? Collections.singleton(CommandSource.RelativePosition.ZERO_LOCAL)
					: ((CommandSource) context.getSource()).getBlockPositionSuggestions();
			return CommandSource.suggestPositions(string, collection, builder,
					CommandManager.getCommandValidator(this::parse));
		}
		return Suggestions.empty();
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
}
