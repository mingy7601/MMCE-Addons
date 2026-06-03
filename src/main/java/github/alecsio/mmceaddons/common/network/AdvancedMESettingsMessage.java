package github.alecsio.mmceaddons.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client-to-server network message for syncing polling interval changes from the GUI.
 * <p>
 * Serializes/deserializes an {@code int} polling interval and a {@link BlockPos} via ByteBuf
 * with full round-trip fidelity. The server applies the new polling interval to the tile entity at the given position.
 */
public class AdvancedMESettingsMessage implements IMessage {

    private int pollingInterval;
    private long posX, posY, posZ;

    public AdvancedMESettingsMessage() {
        // Required no-arg constructor for message deserialization
    }

    /**
     * Creates a settings message with the given polling interval and tile position.
     *
     * @param pollingIntervalTicks the polling interval in game ticks (1–600)
     * @param pos                  the tile entity's block position
     */
    public AdvancedMESettingsMessage(int pollingIntervalTicks, BlockPos pos) {
        this.pollingInterval = pollingIntervalTicks;
        this.posX = pos.getX();
        this.posY = pos.getY();
        this.posZ = pos.getZ();
    }

    /**
     * Creates a settings message with the given polling interval and tile position.
     */
    public AdvancedMESettingsMessage(int pollingIntervalTicks, long x, long y, long z) {
        this.pollingInterval = pollingIntervalTicks;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    /**
     * Gets the polling interval in ticks.
     */
    public int getPollingInterval() {
        return pollingInterval;
    }

    /**
     * Gets the block position as a BlockPos.
     */
    public BlockPos getPosition() {
        return new BlockPos(posX, posY, posZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pollingInterval = buf.readInt();
        posX = buf.readLong();
        posY = buf.readLong();
        posZ = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pollingInterval);
        buf.writeLong(posX);
        buf.writeLong(posY);
        buf.writeLong(posZ);
    }
}
