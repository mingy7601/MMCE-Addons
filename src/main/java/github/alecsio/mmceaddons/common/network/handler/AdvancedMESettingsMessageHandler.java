package github.alecsio.mmceaddons.common.network.handler;

import github.alecsio.mmceaddons.ModularMachineryAddons;
import github.alecsio.mmceaddons.common.hatch.appeng.itembus.AdvancedMEItemInputBus;
import github.alecsio.mmceaddons.common.network.AdvancedMESettingsMessage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server-side handler for {@link AdvancedMESettingsMessage}.
 * <p>
 * Receives polling interval changes from the GUI and applies them to the tile entity at the given position.
 */
public class AdvancedMESettingsMessageHandler implements IMessageHandler<AdvancedMESettingsMessage, IMessage> {

    @Override
    public IMessage onMessage(AdvancedMESettingsMessage message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        BlockPos pos = message.getPosition();

        player.getServerWorld().addScheduledTask(() -> {
            TileEntity tile = player.getServerWorld().getTileEntity(pos);
            if (tile instanceof AdvancedMEItemInputBus) {
                AdvancedMEItemInputBus bus = (AdvancedMEItemInputBus) tile;
                bus.setPollingIntervalTicks(message.getPollingInterval());
            }
        });

        return null;
    }
}
