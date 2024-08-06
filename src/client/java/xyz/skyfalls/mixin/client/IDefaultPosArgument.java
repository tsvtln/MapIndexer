package xyz.skyfalls.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.command.argument.CoordinateArgument;
import net.minecraft.command.argument.DefaultPosArgument;

@Mixin(DefaultPosArgument.class)
public interface IDefaultPosArgument {
	@Accessor
	public CoordinateArgument getX();
	@Accessor
	public CoordinateArgument getY();
	@Accessor
	public CoordinateArgument getZ();
}
