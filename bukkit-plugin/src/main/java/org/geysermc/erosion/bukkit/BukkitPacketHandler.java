package org.geysermc.erosion.bukkit;

import com.nukkitx.math.vector.Vector3i;
import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.erosion.bukkit.world.WorldAccessor;
import org.geysermc.erosion.packet.backendbound.BackendboundBatchBlockRequestPacket;
import org.geysermc.erosion.packet.backendbound.BackendboundBlockRequestPacket;
import org.geysermc.erosion.packet.backendbound.BackendboundInitializePacket;
import org.geysermc.erosion.packet.backendbound.BackendboundPacketHandler;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBatchBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockLookupFailPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPacket;
import org.geysermc.erosion.util.BlockPositionIterator;

import java.util.logging.Logger;

public final class BukkitPacketHandler implements BackendboundPacketHandler {
    private final Logger logger;
    private final WorldAccessor worldAccessor;
    private Channel channel;
    private Player player;

    public BukkitPacketHandler(Logger logger, WorldAccessor worldAccessor) {
        this.logger = logger;
        this.worldAccessor = worldAccessor;
    }

    @Override
    public void handleInitialization(BackendboundInitializePacket packet) {
        player = Bukkit.getPlayer(packet.getUuid());
        if (player == null) {
            this.logger.warning("Player with UUID " + packet.getUuid() + " not found.");
        }
        ErosionBukkit.ACTIVE_PLAYERS.put(player, this);
    }

    @Override
    public void handleBatchBlockRequest(BackendboundBatchBlockRequestPacket packet) {
        try {
            BlockPositionIterator iter = packet.getIter();
            int[] blocks = new int[iter.getMaxIterations()];
            for (; iter.hasNext(); iter.next()) {
                int networkId = worldAccessor.getBlockAt(player, iter.getX(), iter.getY(), iter.getZ());
                blocks[iter.getIteration()] = networkId;
            }
            sendPacket(new GeyserboundBatchBlockIdPacket(packet.getId(), blocks));
        } catch (Throwable e) {
            e.printStackTrace();
            sendPacket(new GeyserboundBlockLookupFailPacket(packet.getId()));
        }
    }

    @Override
    public void handleBlockRequest(BackendboundBlockRequestPacket packet) {
        try {
            Vector3i pos = packet.getPos();
            int networkId = worldAccessor.getBlockAt(player, pos.getX(), pos.getY(), pos.getZ());
            sendPacket(new GeyserboundBlockIdPacket(packet.getId(), networkId));
        } catch (Throwable e) {
            e.printStackTrace();
            sendPacket(new GeyserboundBlockLookupFailPacket(packet.getId()));
        }
    }

    @Override
    public void onDisconnect() {
        ErosionBukkit.ACTIVE_PLAYERS.remove(player);
    }

    public void sendPacket(GeyserboundPacket packet) {
        this.channel.writeAndFlush(packet);
    }

    @Override
    public BukkitPacketHandler setChannel(Channel channel) {
        this.channel = channel;
        return this;
    }
}
