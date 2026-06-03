package github.alecsio.mmceaddons.common.network.handler;

import github.alecsio.mmceaddons.ModularMachineryAddons;
import github.alecsio.mmceaddons.common.hatch.appeng.itembus.AdvancedMEItemInputBus;
import github.alecsio.mmceaddons.common.network.AdvancedMERSyncMessage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server-side handler for {@link AdvancedMERSyncMessage}.
 * <p>
 * Triggers an immediate snapshot and drain on the tile entity at the given position.
 */
public class AdvancedMERSyncMessageHandler implements IMessageHandler<AdvancedMERSyncMessage, IMessage> {

    @Override
    public IMessage onMessage(AdvancedMERSyncMessage message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;

        player.getServerWorld().addScheduledTask(() -> {
            TileEntity tile = player.getServerWorld().getTileEntity(message.getPosition());
            if (tile instanceof AdvancedMEItemInputBus) {
                ((AdvancedMEItemInputBus) tile).forceRescan();
            }
        });

        return null;
    }
}
