package github.alecsio.mmceaddons.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client-to-server network message to trigger an immediate snapshot/re-scan on the server.
 * <p>
 * Includes a {@link BlockPos} so the server knows which tile entity to re-scan.
 */
public class AdvancedMERSyncMessage implements IMessage {

    private long posX, posY, posZ;

    public AdvancedMERSyncMessage() {
        // No payload needed — type-based dispatch triggers the action
    }

    /**
     * Creates a sync message for the given tile position.
     */
    public AdvancedMERSyncMessage(BlockPos pos) {
        this.posX = pos.getX();
        this.posY = pos.getY();
        this.posZ = pos.getZ();
    }

    /**
     * Gets the block position as a BlockPos.
     */
    public BlockPos getPosition() {
        return new BlockPos(posX, posY, posZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readLong();
        posY = buf.readLong();
        posZ = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posX);
        buf.writeLong(posY);
        buf.writeLong(posZ);
    }
}
