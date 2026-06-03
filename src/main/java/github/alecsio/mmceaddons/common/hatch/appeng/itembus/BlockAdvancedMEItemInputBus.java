package github.alecsio.mmceaddons.common.hatch.appeng.itembus;

import github.kasuminova.mmce.common.block.appeng.BlockMEMachineComponent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import github.alecsio.mmceaddons.ModularMachineryAddons;
import github.alecsio.mmceaddons.CommonProxy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block for the Advanced ME Item Input Bus.
 * <p>
 * Extends MMCE's BlockMEMachineComponent and creates an {@link AdvancedMEItemInputBus} tile entity.
 */
public class BlockAdvancedMEItemInputBus extends BlockMEMachineComponent {

    @Override
    public boolean onBlockActivated(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            return true;
        }
        playerIn.openGui(ModularMachineryAddons.MODID,
                CommonProxy.GuiType.ADVANCED_ME_INPUT_BUS.ordinal(),
                worldIn, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new AdvancedMEItemInputBus();
    }
}
