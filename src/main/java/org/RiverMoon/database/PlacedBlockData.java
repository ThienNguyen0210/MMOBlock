package org.RiverMoon.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.UUID;

public class PlacedBlockData {
    private final String worldName;
    private final int x, y, z;

    private final String mmoId;
    private final UUID asUuid;
    private final UUID hitboxUuid;
    private final String groupId;
    
    private final double hbOffX;
    private final double hbOffY;
    private final double hbOffZ;

    public PlacedBlockData(String worldName, int x, int y, int z, String mmoId, String groupId, UUID asUuid, UUID hitboxUuid,
                           double hbOffX, double hbOffY, double hbOffZ) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.mmoId = mmoId;
        this.groupId = groupId; 
        this.asUuid = asUuid;
        this.hitboxUuid = hitboxUuid;
        this.hbOffX = hbOffX;
        this.hbOffY = hbOffY;
        this.hbOffZ = hbOffZ;
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getMmoId() { return mmoId; }
    public UUID getAsUuid() { return asUuid; }
    public UUID getHitboxUuid() { return hitboxUuid; }

    
    public double getHbOffX() { return hbOffX; }
    public double getHbOffY() { return hbOffY; }
    public double getHbOffZ() { return hbOffZ; }
    public String getGroupId() { return groupId; }
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    
    public Location getHitboxLocation() {
        Location base = getLocation();
        if (base == null) return null;
        
        return base.clone().add(0.5 + hbOffX, 0.0 + hbOffY, 0.5 + hbOffZ);
    }
}