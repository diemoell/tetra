package se.mickelus.tetra.blocks.hammer;

import java.util.Collections;
import java.util.LinkedList;
import javax.annotation.Nullable;
import org.apache.commons.lang3.EnumUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ObjectHolder;
import se.mickelus.tetra.TetraMod;
import se.mickelus.tetra.items.cell.ItemCellMagmatic;

public class HammerBaseTile extends TileEntity {

    @ObjectHolder(TetraMod.MOD_ID + ":" + HammerBaseBlock.unlocalizedName)
    public static TileEntityType<HammerBaseTile> type;

    private static final String slotsKey = "slots";
    private static final String indexKey = "slot";
    private ItemStack[] slots;

    private boolean hasPlateWest = true;
    private boolean hasPlateEast = true;

    private EnumHammerConfig configEast = EnumHammerConfig.A;
    private EnumHammerConfig configWest = EnumHammerConfig.A;


    public HammerBaseTile() {
        super(type);
        slots = new ItemStack[2];
    }

    public boolean hasEffect(EnumHammerEffect effect) {
        if (effect.requiresBoth) {
            return effect.equals(EnumHammerEffect.fromConfig(configEast, getWorld().getSeed()))
                    && effect.equals(EnumHammerEffect.fromConfig(configWest, getWorld().getSeed()));
        }
        return effect.equals(EnumHammerEffect.fromConfig(configEast, getWorld().getSeed()))
                || effect.equals(EnumHammerEffect.fromConfig(configWest, getWorld().getSeed()));
    }

    public int getHammerLevel() {
        return hasEffect(EnumHammerEffect.OVERCHARGED) ? 5 : 4;
    }

    public boolean isFueled() {
        for (int i = 0; i < slots.length; i++) {
            if (getCellFuel(i) <= 0) {
                return false;
            }
        }
        return true;
    }

    public void consumeFuel() {
        int fuelUsage = fuelUsage();

        for (int i = 0; i < slots.length; i++) {
            consumeFuel(i, fuelUsage);
        }

        applyConsumeEffect();
    }

    public void consumeFuel(int index, int amount) {
        if (index >= 0 && index < slots.length && slots[index] != null && slots[index].getItem() instanceof ItemCellMagmatic) {
            ItemCellMagmatic item = (ItemCellMagmatic) slots[index].getItem();
            item.drainCharge(slots[index], amount);
        }
    }

    private void applyConsumeEffect() {
        Direction facing = getWorld().getBlockState(getPos()).get(HammerBaseBlock.propFacing);
        Vec3d pos = new Vec3d(getPos());
        pos = pos.add(0.5, 0.5, 0.5);

        if (!world.isRemote && hasEffect(EnumHammerEffect.LEAKY)) {
            int countCell0 = world.rand.nextInt(Math.min(16, getCellFuel(0)));
            int countCell1 = world.rand.nextInt(Math.min(16, getCellFuel(1)));
            consumeFuel(0, countCell0);
            consumeFuel(1, countCell1);

            if (countCell0 > 0 || countCell1 > 0) {

                // particles cell 1
                Vec3d posCell0 = pos.add(new Vec3d(facing.getDirectionVec()).scale(0.55));
                spawnParticle(ParticleTypes.LAVA, posCell0, countCell0 * 2, 0.06f);
                spawnParticle(ParticleTypes.LARGE_SMOKE, posCell0, 2, 0f);

                // particles cell 2
                Vec3d posCell1 = pos.add(new Vec3d(facing.getOpposite().getDirectionVec()).scale(0.55));
                spawnParticle(ParticleTypes.LAVA, posCell1, countCell1 * 2, 0.06f);
                spawnParticle(ParticleTypes.LARGE_SMOKE, posCell1, 2, 0f);

                // gather flammable blocks
                LinkedList<BlockPos> flammableBlocks = new LinkedList<>();
                for (int x = -3; x < 3; x++) {
                    for (int y = -3; y < 2; y++) {
                        for (int z = -3; z < 3; z++) {
                            BlockPos firePos = getPos().add(x, y, z);
                            if (world.isAirBlock(firePos)) {
                                flammableBlocks.add(firePos);
                            }
                        }
                    }
                }

                // set blocks on fire
                Collections.shuffle(flammableBlocks);
                flammableBlocks.stream()
                        .limit(countCell0 + countCell1)
                        .forEach(blockPos -> world.setBlockState(blockPos, Blocks.FIRE.getDefaultState(), 11));
            }
        }
    }

    private int fuelUsage() {
        int usage = 5;
        if (!hasPlateEast) {
            usage += 2;
        }
        if (!hasPlateWest) {
            usage += 2;
        }

        if (hasEffect(EnumHammerEffect.OVERCHARGED)) {
            usage += 4;
        }

        if (hasEffect(EnumHammerEffect.EFFICIENT)) {
            usage -= 3;
        }

        return Math.max(usage, 1);
    }

    public boolean hasCellInSlot(int index) {
        return index >= 0 && index < slots.length && slots[index] != null;
    }

    public int getCellFuel(int index) {
        if (index >= 0 && index < slots.length && slots[index] != null) {
            if (slots[index].getItem() instanceof ItemCellMagmatic) {
                ItemCellMagmatic item = (ItemCellMagmatic) slots[index].getItem();
                return item.getCharge(slots[index]);
            }
        }
        return -1;
    }

    public ItemStack removeCellFromSlot(int index) {
        if (index >= 0 && index < slots.length && slots[index] != null) {
            ItemStack itemStack = slots[index];
            slots[index] = null;
            return itemStack;
        }
        return ItemStack.EMPTY;
    }

    public boolean putCellInSlot(ItemStack itemStack, int index) {
        if (itemStack.getItem() instanceof ItemCellMagmatic
                && index >= 0 && index < slots.length && slots[index] == null) {
            slots[index] = itemStack;
            return true;
        }
        return false;
    }

    public void removePlate(EnumHammerPlate plate) {
        switch (plate) {
            case EAST:
                hasPlateEast = false;
                break;
            case WEST:
                hasPlateWest = false;
                break;
        }
        markDirty();
    }

    public void attachPlate(EnumHammerPlate plate) {
        switch (plate) {
            case EAST:
                hasPlateEast = true;
                break;
            case WEST:
                hasPlateWest = true;
                break;
        }
        markDirty();
    }

    public boolean hasPlate(EnumHammerPlate plate) {
        switch (plate) {
            case EAST:
                return hasPlateEast;
            case WEST:
                return hasPlateWest;
        }
        return false;
    }

    public void reconfigure(Direction side) {
        if (Direction.EAST.equals(side)) {
            configEast = EnumHammerConfig.getNextConfiguration(configEast);
            applyReconfigurationEffect(EnumHammerEffect.fromConfig(configEast, world.getSeed()));
        } else if (Direction.WEST.equals(side)) {
            configWest = EnumHammerConfig.getNextConfiguration(configWest);
            applyReconfigurationEffect(EnumHammerEffect.fromConfig(configWest, world.getSeed()));
        }
        markDirty();
    }

    private void applyReconfigurationEffect(EnumHammerEffect effect) {
        Direction facing = getWorld().getBlockState(getPos()).get(HammerBaseBlock.propFacing);
        Vec3d pos = new Vec3d(getPos());
        pos = pos.add(0.5, 0.5, 0.5);

        if (EnumHammerEffect.OVERCHARGED.equals(effect)) {
            if (!hasCellInSlot(0)) {
                Vec3d rotPos = pos.add(new Vec3d(facing.getDirectionVec()).scale(0.55));
                spawnParticle(ParticleTypes.SMOKE, rotPos, 15, 0.02f);
            }

            if (!hasCellInSlot(1)) {
                Vec3d rotPos = pos.add(new Vec3d(facing.getOpposite().getDirectionVec()).scale(0.55));
                spawnParticle(ParticleTypes.SMOKE, rotPos, 15, 0.02f);
            }
        }

        if (EnumHammerEffect.LEAKY.equals(effect)) {
            if (getCellFuel(0) > 0) {
                Vec3d rotPos = pos.add(new Vec3d(facing.getDirectionVec()).scale(0.55));
                spawnParticle(ParticleTypes.LAVA, rotPos, 3, 0.06f);
                spawnParticle(ParticleTypes.LARGE_SMOKE, rotPos, 3, 0f);
            }

            if (getCellFuel(1) > 0) {
                Vec3d rotPos = pos.add(new Vec3d(facing.getOpposite().getDirectionVec()).scale(0.55));
                spawnParticle(ParticleTypes.LAVA, rotPos, 3, 0.06f);
                spawnParticle(ParticleTypes.LARGE_SMOKE, rotPos, 3, 0f);
            }
        }
    }

    public EnumHammerConfig getConfiguration(Direction side) {
        if (Direction.EAST.equals(side)) {
            return configEast;
        } else if (Direction.WEST.equals(side)) {
            return configWest;
        }

        return EnumHammerConfig.A;
    }

    /**
     * Utility for spawning particles from the server
     */
    private void spawnParticle(IParticleData particle, Vec3d pos, int count, float speed) {
        if (world instanceof ServerWorld) {
            ((ServerWorld) world).spawnParticle(particle, pos.x, pos.y, pos.z, count,  0, 0, 0, speed);
        }
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.pos, 0, this.getUpdateTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return write(new CompoundNBT());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        this.read(pkt.getNbtCompound());
    }

    @Override
    public void read(CompoundNBT compound) {
        super.read(compound);
        if (compound.contains(slotsKey)) {
            ListNBT tagList = compound.getList(slotsKey, 10);

            for (int i = 0; i < tagList.size(); i++) {
                CompoundNBT itemCompound = tagList.getCompound(i);
                int slot = itemCompound.getByte(indexKey) & 255;

                if (slot < this.slots.length) {
                    this.slots[slot] = ItemStack.read(itemCompound);
                }
            }
        }

        hasPlateEast = compound.getBoolean(EnumHammerPlate.EAST.key);
        hasPlateWest = compound.getBoolean(EnumHammerPlate.WEST.key);

        if (compound.contains(EnumHammerConfig.propE.getName())) {
            String enumName = compound.getString(EnumHammerConfig.propE.getName());
            if (EnumUtils.isValidEnum(EnumHammerConfig.class, enumName)) {
                configEast = EnumHammerConfig.valueOf(enumName);
            }
        }

        if (compound.contains(EnumHammerConfig.propW.getName())) {
            String enumName = compound.getString(EnumHammerConfig.propW.getName());
            if (EnumUtils.isValidEnum(EnumHammerConfig.class, enumName)) {
                configWest = EnumHammerConfig.valueOf(enumName);
            }
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        super.write(compound);

        writeCells(compound, slots);

        writePlate(compound, EnumHammerPlate.EAST, hasPlateEast);
        writePlate(compound, EnumHammerPlate.WEST, hasPlateWest);

        writeConfig(compound, configEast, configWest);

        return compound;
    }

    public static void writeCells(CompoundNBT compound, ItemStack... cells) {
        ListNBT nbttaglist = new ListNBT();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                CompoundNBT nbttagcompound = new CompoundNBT();

                nbttagcompound.putByte(indexKey, (byte) i);
                cells[i].write(nbttagcompound);

                nbttaglist.add(nbttagcompound);
            }
        }
        compound.put(slotsKey, nbttaglist);
    }

    public static void writePlate(CompoundNBT compound, EnumHammerPlate plate, boolean hasPlate) {
        compound.putBoolean(plate.key, hasPlate);
    }

    public static void writeConfig(CompoundNBT compound, EnumHammerConfig configEast, EnumHammerConfig configWest) {
        compound.putString(EnumHammerConfig.propE.getName(), configEast.toString());
        compound.putString(EnumHammerConfig.propW.getName(), configWest.toString());
    }

}