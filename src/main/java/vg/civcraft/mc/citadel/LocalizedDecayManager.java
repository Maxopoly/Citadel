package vg.civcraft.mc.citadel;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import vg.civcraft.mc.civmodcore.locations.chunkmeta.XZWCoord;
import vg.civcraft.mc.namelayer.group.Group;

public class LocalizedDecayManager {
	
	public static final int REGION_DECAY_SHIFT_BY_CHUNK = 5;
	public static final int REGION_DECAY_SHIFT_BY_LOCATION = REGION_DECAY_SHIFT_BY_CHUNK + 4;
	
	private Map<XZWCoord, Map<Integer, Long>> timeStamps;
	
	public LocalizedDecayManager() {
		this.timeStamps = new HashMap<>();
	}
	
	public void updateTimestamp(Group group, Location location) {
		
	}

	public void updateTimestamp(Group group, Chunk chunk) {
		int x = chunk.getX() >> REGION_DECAY_SHIFT_BY_CHUNK;
		int z = chunk.getZ() >> REGION_DECAY_SHIFT_BY_CHUNK;
		short worldID = getWorldID(chunk.getWorld());
		XZWCoord coord = new XZWCoord(x, z, worldID);
		
		
	}
	
	private short getWorldID(World world) {
		
	}
	
}
