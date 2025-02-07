package io.github.andrew6rant.autoslabs.mixin;

import io.github.andrew6rant.autoslabs.AutoSlabs;
import io.github.andrew6rant.autoslabs.PlacementUtil;
import io.github.andrew6rant.autoslabs.SlabLockEnum;
import io.github.andrew6rant.autoslabs.VerticalType;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.github.andrew6rant.autoslabs.Util.TYPE;
import static io.github.andrew6rant.autoslabs.Util.VERTICAL_TYPE;
import static io.github.andrew6rant.autoslabs.VerticalType.*;
import static net.minecraft.block.enums.SlabType.BOTTOM;
import static net.minecraft.block.enums.SlabType.TOP;

@Mixin(SlabBlock.class)
public class SlabBlockMixin extends Block implements Waterloggable {

	private SlabBlockMixin(Settings settings) {
		super(settings);
	}

	@Inject(at = @At("HEAD"), method = "canReplace(Lnet/minecraft/block/BlockState;Lnet/minecraft/item/ItemPlacementContext;)Z", cancellable = true)
	private void autoslabs$canSlabReplace(BlockState state, ItemPlacementContext ctx, CallbackInfoReturnable<Boolean> cir) {
		if (ctx.getPlayer() == null) return;
		if (!AutoSlabs.slabLockPosition.getOrDefault(ctx.getPlayer(), SlabLockEnum.DEFAULT_AUTOSLABS).equals(SlabLockEnum.VANILLA_PLACEMENT)) {
			cir.setReturnValue(PlacementUtil.canReplace(state, ctx));
		}
	}

	@Inject(at = @At("HEAD"), method = "getPlacementState(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/block/BlockState;", cancellable = true)
	private void autoslabs$getSlabPlacementState(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
		if (ctx.getPlayer() == null) return;
		if (!AutoSlabs.slabLockPosition.getOrDefault(ctx.getPlayer(), SlabLockEnum.DEFAULT_AUTOSLABS).equals(SlabLockEnum.VANILLA_PLACEMENT)) {
			cir.setReturnValue(PlacementUtil.calcPlacementState(ctx, this.getDefaultState()));
		}
	}

	// Massive thanks to Oliver-makes-code for some of the code behind this mixin
	// https://github.com/Oliver-makes-code/autoslab/blob/1.19/src/main/java/olivermakesco/de/autoslab/mixin/Mixin_SlabBlock.java
	@Inject(at = @At("RETURN"), method = "getOutlineShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",cancellable = true)
	private void autoslabs$getBetterSlabOutline(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityShapeContext entityContext)) return;
		SlabType slabType = state.get(TYPE);
		if (slabType != SlabType.DOUBLE) return;
		VerticalType verticalType = state.get(VERTICAL_TYPE);
		if (verticalType == null) return;
		Entity entity = entityContext.getEntity();
		if (entity == null) return;
		if (entity.isSneaking()) return;

		BlockHitResult cast = PlacementUtil.calcRaycast(entity);
		Direction side = cast.getSide();
		cir.setReturnValue(PlacementUtil.getDynamicOutlineShape(verticalType, side, cast));
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return PlacementUtil.getOutlineShape(state);
	}

	@Override
	public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack) {
		if (player.isSneaking()) {
			//Should ensure that if the player mines a single slab, it drops the correct amount
			super.afterBreak(world, player, pos, state.with(TYPE, state.get(SlabBlock.TYPE)), blockEntity, stack);
		} else {
			super.afterBreak(world, player, pos, state.with(TYPE, TOP), blockEntity, stack);
		}
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		SlabType slabType = state.get(TYPE);
		if (slabType == null) return super.rotate(state, rotation);
		if (slabType == SlabType.DOUBLE) return state;

		VerticalType verticalType = state.get(VERTICAL_TYPE);
		if (verticalType == null) return super.rotate(state, rotation);
		if (verticalType == FALSE || rotation == BlockRotation.NONE) return state;

		return switch (rotation) {
            case CLOCKWISE_90 -> state
                    .with(VERTICAL_TYPE, verticalType == NORTH_SOUTH ? EAST_WEST : NORTH_SOUTH)
                    .with(TYPE, verticalType == EAST_WEST ? (slabType == TOP ? BOTTOM : TOP) : slabType);
			case CLOCKWISE_180 -> state
					.with(TYPE, slabType == TOP ? BOTTOM : TOP);
			case COUNTERCLOCKWISE_90 -> state
					.with(VERTICAL_TYPE, verticalType == NORTH_SOUTH ? EAST_WEST : NORTH_SOUTH)
					.with(TYPE, verticalType == NORTH_SOUTH ? (slabType == TOP ? BOTTOM : TOP) : slabType);
            default -> state;
        };
    }

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		SlabType slabType = state.get(TYPE);
		if (slabType == null) return super.mirror(state, mirror);
		if (slabType == SlabType.DOUBLE) return state;

		VerticalType verticalType = state.get(VERTICAL_TYPE);
		if (verticalType == null) return super.mirror(state, mirror);
		if (verticalType == FALSE || mirror == BlockMirror.NONE) return state;

		return switch (mirror) {
			case LEFT_RIGHT -> switch (verticalType) {
				case EAST_WEST -> state;
				default -> state.with(TYPE, state.get(TYPE) == TOP ? BOTTOM : TOP);
			};
			case FRONT_BACK -> switch (verticalType) {
				case NORTH_SOUTH -> state;
				default -> state.with(TYPE, state.get(TYPE) == TOP ? BOTTOM : TOP);
			};
			default -> state;
		};
	}

}