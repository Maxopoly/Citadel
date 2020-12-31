package vg.civcraft.mc.citadel.model;

import com.google.common.base.Objects;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.meta.ItemMeta;

import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.reinforcementtypes.ReinforcementType;
import vg.civcraft.mc.citadel.reinforcementtypes.ReinforcementTypeManager;
import vg.civcraft.mc.civmodcore.CivModCorePlugin;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;
import vg.civcraft.mc.civmodcore.locations.chunkmeta.XZWCoord;
import vg.civcraft.mc.civmodcore.locations.chunkmeta.block.BlockBasedChunkMeta;
import vg.civcraft.mc.civmodcore.locations.chunkmeta.block.table.TableBasedBlockChunkMeta;
import vg.civcraft.mc.civmodcore.locations.chunkmeta.block.table.TableStorageEngine;
import vg.civcraft.mc.civmodcore.locations.global.WorldIDManager;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.group.Group;

public class CitadelDAO extends TableStorageEngine<Reinforcement> {

	public CitadelDAO(Logger logger, ManagedDatasource db) {
		super(logger, db);
	}

	@Override
	public void registerMigrations() {
		db.registerMigration(15, false,
				"CREATE TABLE IF NOT EXISTS reinforcement (rein_id int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,"
						+ "x int(11) NOT NULL, y int(11) NOT NULL, z int(11) NOT NULL, chunk_x int(11) NOT NULL, chunk_z int(11) NOT NULL,"
						+ "world varchar(255) NOT NULL, material_id int(11) NOT NULL, durability varchar(10) NOT NULL, insecure tinyint(1) NOT NULL,"
						+ "group_id int(11) NOT NULL, maturation_time int(11) NOT NULL, rein_type_id int(11) NOT NULL, lore varchar(255) DEFAULT NULL,"
						+ "acid_time int(11) NOT NULL)");
		db.registerMigration(16, false, new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				try (Connection insertConn = db.getConnection();
						PreparedStatement selectRein = insertConn.prepareStatement(
								"select x,y,z,chunk_x,chunk_z,world,material_id,durability,insecure,group_id,maturation_time,lore from reinforcement order by rein_id asc");
						ResultSet rs = selectRein.executeQuery()) {
					PreparedStatement insertRein = insertConn.prepareStatement(
							"insert into ctdl_reinforcements (chunk_x, chunk_z, world_id, x_offset, y, z_offset, type_id, "
									+ "health, group_id, insecure, creation_time) values(?,?,?, ?,?,?, ?,?,?,?,?);");
					try (PreparedStatement deleteExisting = insertConn
							.prepareStatement("delete from ctdl_reinforcements")) {
						// in case this migration failed before some of the data might already have
						// migrated, which we want to undo
						deleteExisting.execute();
					}

					WorldIDManager worldMan = CivModCorePlugin.getInstance().getWorldIdManager();
					Map<Integer, List<ReinforcementType>> reinTypes = new TreeMap<>();
					for (ReinforcementType type : Citadel.getInstance().getReinforcementTypeManager().getAllTypes()) {
						List<ReinforcementType> withType = reinTypes.computeIfAbsent(type.getLegacyId(),
								s -> new ArrayList<>());
						withType.add(type);
					}
					int batchCounter = 0;
					while (rs.next()) {
						byte x = (byte) BlockBasedChunkMeta.modulo(rs.getInt(1));
						short y = (short) rs.getInt(2);
						byte z = (byte) BlockBasedChunkMeta.modulo(rs.getInt(3));
						int chunkX = rs.getInt(4);
						int chunkZ = rs.getInt(5);
						String worldName = rs.getString(6);
						int materialId = rs.getInt(7);
						String durability = rs.getString(8);
						boolean insecure = rs.getBoolean(9);
						int groupId = rs.getInt(10);
						int maturationTime = rs.getInt(11);
						String lore = rs.getString(12);

						short worldID = worldMan.getInternalWorldIdByName(worldName);
						if (worldID == -1) {
							logger.severe("Failed to find world id for world with name " + worldName);
							return false;
						}
						float healthFloat = Float.parseFloat(durability);
						List<ReinforcementType> withType = reinTypes.get(materialId);
						if (withType == null) {
							logger.severe(
									"Failed to find material mapping for reinforcement with material id " + materialId);
							return false;
						}
						ReinforcementType type = null;
						if (withType.size() == 1) {
							type = withType.get(0);
						} else {
							boolean hasLore = lore != null;
							for (ReinforcementType compType : withType) {
								ItemMeta meta = compType.getItem().getItemMeta();
								if (hasLore == meta.hasLore()) {
									if (!hasLore || meta.getLore().get(0).equals(lore)) {
										type = compType;
										break;
									}
								}
							}
							if (type == null) {
								logger.severe("Failed to find material mapping for reinforcement with material id "
										+ materialId + " and lore " + lore);
								return false;
							}
						}
						// previously we stored the timestamp at which the reinforcement will be mature
						// in minutes since unix epoch
						// No, I do not know why
						long creationTime = maturationTime - (type.getMaturationTime() / 60_000);
						// some rows have a maturation time of 0, no idea why
						creationTime = Math.max(creationTime, 1);
						creationTime *= 60_000;

						insertRein.setInt(1, chunkX);
						insertRein.setInt(2, chunkZ);
						insertRein.setShort(3, worldID);
						insertRein.setByte(4, x);
						insertRein.setShort(5, y);
						insertRein.setByte(6, z);
						insertRein.setShort(7, type.getID());
						insertRein.setFloat(8, healthFloat);
						insertRein.setInt(9, groupId);
						insertRein.setBoolean(10, insecure);
						insertRein.setTimestamp(11, new Timestamp(creationTime));
						insertRein.addBatch();
						if (batchCounter > 10000) {
							batchCounter = 0;
							insertRein.executeBatch();
						}
						batchCounter++;
					}
					insertRein.executeBatch();
				}
				return true;
			}
		}, "create table if not exists  ctdl_reinforcements (chunk_x int not null, chunk_z int not null, world_id smallint unsigned not null, "
				+ "x_offset tinyint unsigned not null, y smallint not null, z_offset tinyint unsigned not null, "
				+ "type_id smallint unsigned not null, health float not null, group_id int not null, insecure boolean not null default false,"
				+ "creation_time timestamp not null default now(), index reinChunkLookUp(chunk_x, chunk_z, world_id), primary key "
				+ "(chunk_x, chunk_z, world_id, x_offset, y ,z_offset))");

		this.db.registerMigration(17, false, () -> {
			try (final Connection connection = db.getConnection();
				 final PreparedStatement selectRein = connection.prepareStatement(
				 		"SELECT DISTINCT chunk_x, chunk_z, world_id, group_id FROM ctdl_reinforcements;");
				 final ResultSet results = selectRein.executeQuery()) {
				// Keep a cache to reduce the number of calls to NameLayer
				final Map<Integer, Long> lastActivityCache = new TreeMap<>();
				final Set<ReinDecayStruct> chunkDecays = new HashSet<>();
				while (results.next()) {
					final int chunkX = results.getInt(1);
					final int chunkZ = results.getInt(2);
					final int worldId = results.getInt(3);
					final int groupId = results.getInt(4);
					final long lastActivity = lastActivityCache.computeIfAbsent(groupId, _id -> {
						final Group group = GroupManager.getGroup(groupId);
						if (group == null) {
							this.logger.warning("Was unable to find group [" + groupId + "] from reinforcement!");
							return 0L;
						}
						return group.getActivityTimeStamp();
					});
					final ReinDecayStruct entry = new ReinDecayStruct(worldId, chunkX, chunkZ, groupId, lastActivity);
					if (!chunkDecays.add(entry)) {
						this.logger.warning("Reinforcement decay collision found! " + entry);
					}
				}
				int decayBatchCounter = 0;
				try (final PreparedStatement insertDecay = connection.prepareStatement(
						"INSERT INTO reinforcement_decays (chunk_x, chunk_z, world_id, group_id, last_active) " +
								"VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE last_active = ?;")) {
					for (final ReinDecayStruct entry : chunkDecays) {
						insertDecay.setInt(1, entry.chunkX);
						insertDecay.setInt(2, entry.chunkZ);
						insertDecay.setInt(3, entry.worldId);
						insertDecay.setInt(4, entry.groupId);
						insertDecay.setLong(5, entry.lastActive);
						insertDecay.setLong(6, entry.lastActive);
						insertDecay.addBatch();
						// Execute the batch now if enough entries have piled up
						if (++decayBatchCounter > 10_000) {
							decayBatchCounter = 0;
							insertDecay.executeBatch();
						}
					}
					if (decayBatchCounter > 0) {
						insertDecay.executeBatch();
					}
				}
			}
			return true;
		},
		"CREATE TABLE IF NOT EXISTS reinforcement_decays (" +
				"chunk_x INT(11) not null," +
				"chunk_z INT(11) not null," +
				"world_id SMALLINT(5) unsigned not null," +
				"group_id INT(11) NOT NULL," +
				"last_active BIGINT(20) NOT NULL DEFAULT '0'," +
				"PRIMARY KEY (chunk_x, chunk_z, world_id, group_id) USING BTREE," +
				"INDEX chunkDecayLookUp (chunk_x, chunk_z, world_id, group_id) USING BTREE" +
				") COLLATE=utf8_general_ci ENGINE=InnoDB;");
	}

	/**
	 * Gets a single reinforcement at the given location without inserting it into
	 * the tracking
	 * 
	 * @return Reinforcement loaded from the database
	 */
	@Override
	public Reinforcement getForLocation(int x, int y, int z, short worldID, short pluginID) {
		int chunkX = BlockBasedChunkMeta.toChunkCoord(x);
		int chunkZ = BlockBasedChunkMeta.toChunkCoord(z);
		ReinforcementTypeManager typeMan = Citadel.getInstance().getReinforcementTypeManager();
		try (Connection insertConn = db.getConnection();
				PreparedStatement selectRein = insertConn
						.prepareStatement("select type_id, group_id, creation_time, health, insecure "
								+ "from ctdl_reinforcements where chunk_x = ? and chunk_z = ? and world_id = ? and x_offset = ? and y = ? and z_offset = ?;");) {
			selectRein.setInt(1, chunkX);
			selectRein.setInt(2, chunkZ);
			selectRein.setShort(3, worldID);
			selectRein.setByte(4, (byte) BlockBasedChunkMeta.modulo(x));
			selectRein.setShort(5, (short) y);
			selectRein.setByte(6, (byte) BlockBasedChunkMeta.modulo(z));
			try (ResultSet rs = selectRein.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				short typeID = rs.getShort(1);
				ReinforcementType type = typeMan.getById(typeID);
				if (type == null) {
					logger.log(Level.SEVERE, "Failed to load reinforcement with type id " + typeID);
					return null;
				}
				int groupID = rs.getInt(2);
				long creationTime = rs.getTimestamp(3).getTime();
				float health = rs.getFloat(4);
				boolean insecure = rs.getBoolean(5);
				World world = CivModCorePlugin.getInstance().getWorldIdManager().getWorldByInternalID(worldID);
				Location loc = new Location(world, x, y, z);
				return new Reinforcement(loc, type, groupID, creationTime, health, insecure, false);
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to load reinforcement from db: ", e);
			return null;
		}
	}

	@Override
	public void insert(Reinforcement data, XZWCoord coord) {
		try (Connection insertConn = db.getConnection();
				PreparedStatement insertRein = insertConn.prepareStatement(
						"insert into ctdl_reinforcements (chunk_x, chunk_z, world_id, x_offset, y, z_offset, type_id, "
								+ "health, group_id, insecure, creation_time) values(?,?,?, ?,?,?, ?,?,?,?,?);");) {
			insertRein.setInt(1, coord.getX());
			insertRein.setInt(2, coord.getZ());
			insertRein.setShort(3, coord.getWorldID());
			insertRein.setByte(4, (byte) BlockBasedChunkMeta.modulo(data.getLocation().getBlockX()));
			insertRein.setShort(5, (short) data.getLocation().getBlockY());
			insertRein.setByte(6, (byte) BlockBasedChunkMeta.modulo(data.getLocation().getBlockZ()));
			insertRein.setShort(7, data.getType().getID());
			insertRein.setFloat(8, data.getHealth());
			insertRein.setInt(9, data.getGroupId());
			insertRein.setBoolean(10, data.isInsecure());
			insertRein.setTimestamp(11, new Timestamp(data.getCreationTime()));
			insertRein.execute();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to insert reinforcement into db: ", e);
		}
	}

	@Override
	public void update(Reinforcement data, XZWCoord coord) {
		try (Connection insertConn = db.getConnection();
				PreparedStatement updateRein = insertConn.prepareStatement(
						"update ctdl_reinforcements set type_id = ?, health = ?, group_id = ?, insecure = ?, creation_time = ? where "
								+ "chunk_x = ? and chunk_z = ? and world_id = ? and x_offset = ? and y = ? and z_offset = ?;");) {
			updateRein.setShort(1, data.getType().getID());
			updateRein.setFloat(2, data.getHealth());
			updateRein.setInt(3, data.getGroupId());
			updateRein.setBoolean(4, data.isInsecure());
			updateRein.setTimestamp(5, new Timestamp(data.getCreationTime()));
			updateRein.setInt(6, coord.getX());
			updateRein.setInt(7, coord.getZ());
			updateRein.setShort(8, coord.getWorldID());
			updateRein.setByte(9, (byte) BlockBasedChunkMeta.modulo(data.getLocation().getBlockX()));
			updateRein.setShort(10, (short) data.getLocation().getBlockY());
			updateRein.setByte(11, (byte) BlockBasedChunkMeta.modulo(data.getLocation().getBlockZ()));
			updateRein.execute();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to update reinforcement in db: ", e);
		}
	}

	@Override
	public void delete(Reinforcement data, XZWCoord coord) {
		try (Connection insertConn = db.getConnection();
				PreparedStatement deleteRein = insertConn.prepareStatement(
						"delete from ctdl_reinforcements where chunk_x = ? and chunk_z = ? and world_id = ? and "
								+ "x_offset = ? and y = ? and z_offset = ?;");) {
			deleteRein.setInt(1, coord.getX());
			deleteRein.setInt(2, coord.getZ());
			deleteRein.setShort(3, coord.getWorldID());
			deleteRein.setByte(4, (byte) BlockBasedChunkMeta.modulo(data.getLocation().getBlockX()));
			deleteRein.setShort(5, (short) data.getLocation().getBlockY());
			deleteRein.setByte(6, (byte) BlockBasedChunkMeta.modulo(data.getLocation().getBlockZ()));
			deleteRein.execute();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to delete reinforcement from db: ", e);
		}
	}

	@Override
	public void fill(TableBasedBlockChunkMeta<Reinforcement> chunkData, Consumer<Reinforcement> insertFunction) {
		int preMultipliedX = chunkData.getChunkCoord().getX() * 16;
		int preMultipliedZ = chunkData.getChunkCoord().getZ() * 16;
		ReinforcementTypeManager typeMan = Citadel.getInstance().getReinforcementTypeManager();
		World world = chunkData.getChunkCoord().getWorld();
		try (Connection insertConn = db.getConnection();
				PreparedStatement selectRein = insertConn.prepareStatement(
						"select x_offset, y, z_offset, type_id, group_id, creation_time, health, insecure "
								+ "from ctdl_reinforcements where chunk_x = ? and chunk_z = ? and world_id = ?;");) {
			selectRein.setInt(1, chunkData.getChunkCoord().getX());
			selectRein.setInt(2, chunkData.getChunkCoord().getZ());
			selectRein.setShort(3, chunkData.getChunkCoord().getWorldID());
			try (ResultSet rs = selectRein.executeQuery()) {
				while (rs.next()) {
					int xOffset = rs.getByte(1);
					int x = xOffset + preMultipliedX;
					int y = rs.getShort(2);
					int zOffset = rs.getByte(3);
					int z = zOffset + preMultipliedZ;
					Location location = new Location(world, x, y, z);
					short typeID = rs.getShort(4);
					ReinforcementType type = typeMan.getById(typeID);
					if (type == null) {
						logger.log(Level.SEVERE, "Failed to load reinforcement with type id " + typeID);
						continue;
					}
					int groupID = rs.getInt(5);
					long creationTime = rs.getTimestamp(6).getTime();
					float health = rs.getFloat(7);
					boolean insecure = rs.getBoolean(8);
					Reinforcement rein = new Reinforcement(location, type, groupID, creationTime, health, insecure,
							false);
					insertFunction.accept(rein);
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to load reinforcement from db: ", e);
		}
	}

	@Override
	public Collection<XZWCoord> getAllDataChunks() {
		List<XZWCoord> result = new ArrayList<>();
		try (Connection insertConn = db.getConnection();
				PreparedStatement selectChunks = insertConn.prepareStatement(
						"select chunk_x, chunk_z, world_id from ctdl_reinforcements group by chunk_x, chunk_z, world_id");
				ResultSet rs = selectChunks.executeQuery()) {
			while (rs.next()) {
				int chunkX = rs.getInt(1);
				int chunkZ = rs.getInt(2);
				short worldID = rs.getShort(3);
				result.add(new XZWCoord(chunkX, chunkZ, worldID));
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to select populated chunks from db: ", e);
		}
		return result;
	}

	@Override
	public boolean stayLoaded() {
		return false;
	}

	// TODO: PLEASE FOR HEAVEN'S SAKE CAN WE HAVE LOMBOK?!?!?!
	private static class ReinDecayStruct {
		public final int worldId;
		public final int chunkX;
		public final int chunkZ;
		public final int groupId;
		public final long lastActive;
		public ReinDecayStruct(final int worldId,
							   final int chunkX,
							   final int chunkZ,
							   final int groupId,
							   final long lastActive) {
			this.worldId = worldId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groupId = groupId;
			this.lastActive = lastActive;
		}
		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			final ReinDecayStruct that = (ReinDecayStruct) o;
			return this.worldId == that.worldId
					&& this.chunkX == that.chunkX
					&& this.chunkZ == that.chunkZ
					&& this.groupId == that.groupId;
		}
		@Override
		public int hashCode() {
			return Objects.hashCode(
					this.worldId,
					this.chunkX,
					this.chunkZ,
					this.groupId);
		}
		@Override
		public String toString() {
			return "ReinDecayStruct{" +
					"worldId=" + this.worldId +
					", chunkX=" + this.chunkX +
					", chunkZ=" + this.chunkZ +
					", groupId=" + this.groupId +
					", lastActive=" + this.lastActive +
					'}';
		}
	}

}
