package se.mickelus.tetra.effect;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import se.mickelus.tetra.ToolTypes;
import se.mickelus.tetra.items.modular.ItemModularHandheld;
import se.mickelus.tetra.util.CastOptional;
import se.mickelus.tetra.util.RotationHelper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StrikingEffect {
    private static final Cache<UUID, Integer> strikeCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();


    private static final BlockPos[] sweep1 = new BlockPos[] {
            new BlockPos(-2, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(2, 0, 0),
            new BlockPos(-1, 0, 1),
            new BlockPos(0, 0, 1),
            new BlockPos(1, 0, 1),
            new BlockPos(-3, 0, -1),
            new BlockPos(-2, 0, -1),
            new BlockPos(-1, 0, -1),
            new BlockPos(0, 0, -1),
            new BlockPos(3, 0, -1),
            new BlockPos(-3, 0, -2),
            new BlockPos(-2, 0, -2),
            new BlockPos(-1, 0, -2),
            new BlockPos(-1, 1, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(-2, 1, -1),
            new BlockPos(-1, 1, -1),
            new BlockPos(-1, -1, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(-2, -1, -1),
            new BlockPos(-1, -1, -1),
    };

    private static final BlockPos[] sweep2 = new BlockPos[] {
            new BlockPos(-2, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(2, 0, 0),
            new BlockPos(3, 0, 0),
            new BlockPos(-1, 0, 1),
            new BlockPos(0, 0, 1),
            new BlockPos(1, 0, 1),
            new BlockPos(2, 0, 1),
            new BlockPos(-2, 0, -1),
            new BlockPos(-1, 0, -1),
            new BlockPos(0, 0, -1),
            new BlockPos(4, 0, -1),
            new BlockPos(-1, 1, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(1, 1, 0),
            new BlockPos(-1, -1, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(1, -1, 0),
    };

    public static boolean causeEffect(PlayerEntity breakingPlayer, ItemStack itemStack, ItemModularHandheld item, World world, BlockPos pos, BlockState blockState) {
        int strikingLevel = 0;
        ToolType tool = null;

        // essentially checks if the item is effective in for each tool type, and checks if it can strike for that type
        if (ItemModularHandheld.isToolEffective(ToolType.AXE, blockState)) {
            strikingLevel = EffectHelper.getEffectLevel(itemStack, ItemEffect.strikingAxe);
            if (strikingLevel > 0) {
                tool = ToolType.AXE;
            }
        }

        if (strikingLevel <= 0 && ItemModularHandheld.isToolEffective(ToolType.PICKAXE, blockState)) {
            strikingLevel = EffectHelper.getEffectLevel(itemStack, ItemEffect.strikingPickaxe);
            if (strikingLevel > 0) {
                tool = ToolType.PICKAXE;
            }
        }

        if (strikingLevel <= 0 && ItemModularHandheld.isToolEffective(ToolTypes.cut, blockState)) {
            strikingLevel = EffectHelper.getEffectLevel(itemStack, ItemEffect.strikingCut);
            if (strikingLevel > 0) {
                tool = ToolTypes.cut;
            }
        }

        if (strikingLevel <= 0 && ItemModularHandheld.isToolEffective(ToolType.SHOVEL, blockState)) {
            strikingLevel = EffectHelper.getEffectLevel(itemStack, ItemEffect.strikingShovel);
            if (strikingLevel > 0) {
                tool = ToolType.SHOVEL;
            }
        }

        if (strikingLevel <= 0 && ItemModularHandheld.isToolEffective(ToolType.HOE, blockState)) {
            strikingLevel = EffectHelper.getEffectLevel(itemStack, ItemEffect.strikingHoe);
            if (strikingLevel > 0) {
                tool = ToolType.HOE;
            }
        }

        if (strikingLevel > 0) {
            int sweepingLevel = EffectHelper.getEffectLevel(itemStack, ItemEffect.sweepingStrike);
            if (breakingPlayer.getCooledAttackStrength(0) > 0.9) {
                if (sweepingLevel > 0) {
                    breakBlocksAround(world, breakingPlayer, itemStack, pos, tool, sweepingLevel);
                } else {
                    int toolLevel = itemStack.getItem().getHarvestLevel(itemStack, tool, breakingPlayer, blockState);
                    if ((toolLevel >= 0 && toolLevel >= blockState.getBlock().getHarvestLevel(blockState))
                            || itemStack.canHarvestBlock(blockState)) {
                        EffectHelper.breakBlock(world, breakingPlayer, itemStack, pos, blockState, true);
                    }
                }

                item.applyUsageEffects(breakingPlayer, itemStack, 1);
                item.applyDamage(item.getBlockDestroyDamage(), itemStack, breakingPlayer);
            }
            breakingPlayer.resetCooldown();
            return true;
        }

        return false;
    }

    /**
     * Gets and increments counter for recurrent strike made by the given entity. Expires after a minute.
     * @param entityId The ID of the responsible entity
     * @return The number of recurrent strikes
     */
    private static int getStrikeCounter(UUID entityId) {
        int counter = 0;
        try {
            counter = strikeCache.get(entityId, () -> 0);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        strikeCache.put(entityId, counter + 1);

        return counter;
    }

    /**
     * Breaks several blocks around the given blockpos.
     * @param world the world in which to break blocks
     * @param breakingPlayer the player which is breaking the blocks
     * @param toolStack the itemstack used to break the blocks
     * @param originPos the position which to break blocks around
     * @param tool the type of tool used to break the center block, the tool required to break nearby blocks has to
     *             match this
     * @param sweepingLevel the level of the sweeping effect on the toolStack
     */
    private static void breakBlocksAround(World world, PlayerEntity breakingPlayer, ItemStack toolStack, BlockPos originPos, ToolType tool,
            int sweepingLevel) {
        if (world.isRemote) {
            return;
        }

        Direction facing = breakingPlayer.getHorizontalFacing();
        final int strikeCounter = getStrikeCounter(breakingPlayer.getUniqueID());
        final boolean alternate = strikeCounter % 2 == 0;

        float efficiency = CastOptional.cast(toolStack.getItem(), ItemModularHandheld.class)
                .map(item -> item.getToolEfficiency(toolStack, tool))
                .orElse(0f);

        breakingPlayer.spawnSweepParticles();

        List<BlockPos> positions = Arrays.stream((strikeCounter / 2) % 2 == 0 ? sweep1 : sweep2)
                .map(pos -> (alternate ? new BlockPos(-pos.getX(), pos.getY(), pos.getZ()) : pos))
                .map(pos -> RotationHelper.rotatePitch(pos, breakingPlayer.rotationPitch))
                .map(pos -> RotationHelper.rotateCardinal(pos, facing))
                .map(originPos::add)
                .collect(Collectors.toList());

        for (BlockPos pos : positions) {
            BlockState blockState = world.getBlockState(pos);

            // make sure that only blocks which require the same tool are broken
            if (ItemModularHandheld.isToolEffective(tool, blockState)) {

                // check that the tool level is high enough and break the block
                int toolLevel = toolStack.getItem().getHarvestLevel(toolStack, tool, breakingPlayer, blockState);
                if ((toolLevel >= 0 && toolLevel >= blockState.getBlock().getHarvestLevel(blockState))
                        || toolStack.canHarvestBlock(blockState)) {

                    // the break event has to be sent the player separately as it's sent to the others inside Block.onBlockHarvested
                    if (breakingPlayer instanceof ServerPlayerEntity) {
                        EffectHelper.sendEventToPlayer((ServerPlayerEntity) breakingPlayer, 2001, pos, Block.getStateId(blockState));
                    }

                    // adds a fixed amount to make blocks like grass still "consume" some efficiency
                    efficiency -= blockState.getBlockHardness(world, pos) + 0.5;

                    EffectHelper.breakBlock(world, breakingPlayer, toolStack, pos, blockState, true);
                } else {
                    break;
                }
            } else if (blockState.isSolid()) {
                efficiency -= blockState.getBlockHardness(world, pos);
            }

            if (efficiency <= 0) {
                break;
            }
        }
    }

}