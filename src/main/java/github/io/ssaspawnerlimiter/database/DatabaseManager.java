package github.io.ssaspawnerlimiter.database;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import lombok.Getter;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/**
 * Thread-safe SQLite database manager for storing spawner chunk data.
 * Uses connection pooling and proper locking for Folia compatibility.
 */
public class DatabaseManager {
    private final SSASpawnerLimiter plugin;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    @Getter
    private Connection connection;
    private final String databasePath;

    public DatabaseManager(SSASpawnerLimiter plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String filename = plugin.getConfig().getString("database.filename", "spawner_limits.db");
        this.databasePath = new File(dataFolder, filename).getAbsolutePath();
    }

    /**
     * Initialize database connection and create tables
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                connection.setAutoCommit(true);

                createTables();
                plugin.getMessageService().sendMessage(Bukkit.getConsoleSender(), "database_connected");
                return true;
            } catch (ClassNotFoundException | SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("error", e.getMessage());
                plugin.getMessageService().sendMessage(Bukkit.getConsoleSender(), "database_connection_failed", placeholders);
                return false;
            }
        });
    }

    /**
     * Create necessary database tables
     */
    private void createTables() throws SQLException {
        lock.writeLock().lock();
        try (Statement stmt = connection.createStatement()) {
            // Main table for chunk spawner counts
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS spawner_chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    world TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    spawner_count INTEGER NOT NULL DEFAULT 0,
                    last_updated INTEGER NOT NULL,
                    UNIQUE(world, chunk_x, chunk_z)
                );
                """;
            stmt.execute(createTableSQL);

            // Create index for faster lookups
            String createIndexSQL = """
                CREATE INDEX IF NOT EXISTS idx_chunk_location 
                ON spawner_chunks(world, chunk_x, chunk_z);
                """;
            stmt.execute(createIndexSQL);

            // Metadata table for future use
            String createMetaTableSQL = """
                CREATE TABLE IF NOT EXISTS limiter_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                """;
            stmt.execute(createMetaTableSQL);

            plugin.getLogger().info("Database tables created/verified successfully");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get spawner count for a specific chunk
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return CompletableFuture with spawner count
     */
    public CompletableFuture<Integer> getSpawnerCount(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                String sql = "SELECT spawner_count FROM spawner_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, world);
                    stmt.setInt(2, chunkX);
                    stmt.setInt(3, chunkZ);

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getInt("spawner_count");
                    }
                    return 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error getting spawner count", e);
                return 0;
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    /**
     * Set spawner count for a specific chunk
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param count New spawner count
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> setSpawnerCount(String world, int chunkX, int chunkZ, int count) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                String sql = """
                    INSERT INTO spawner_chunks (world, chunk_x, chunk_z, spawner_count, last_updated)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(world, chunk_x, chunk_z) 
                    DO UPDATE SET spawner_count = ?, last_updated = ?
                    """;

                long timestamp = System.currentTimeMillis();
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, world);
                    stmt.setInt(2, chunkX);
                    stmt.setInt(3, chunkZ);
                    stmt.setInt(4, count);
                    stmt.setLong(5, timestamp);
                    stmt.setInt(6, count);
                    stmt.setLong(7, timestamp);

                    stmt.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error setting spawner count", e);
                return false;
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Increment spawner count for a specific chunk
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param amount Amount to increment by
     * @return CompletableFuture with new count
     */
    public CompletableFuture<Integer> incrementSpawnerCount(String world, int chunkX, int chunkZ, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                // First, get current count
                int currentCount = 0;
                String selectSQL = "SELECT spawner_count FROM spawner_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, world);
                    stmt.setInt(2, chunkX);
                    stmt.setInt(3, chunkZ);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        currentCount = rs.getInt("spawner_count");
                    }
                }

                // Calculate new count
                int newCount = Math.max(0, currentCount + amount);

                // Update with new count
                String updateSQL = """
                    INSERT INTO spawner_chunks (world, chunk_x, chunk_z, spawner_count, last_updated)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(world, chunk_x, chunk_z) 
                    DO UPDATE SET spawner_count = ?, last_updated = ?
                    """;

                long timestamp = System.currentTimeMillis();
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, world);
                    stmt.setInt(2, chunkX);
                    stmt.setInt(3, chunkZ);
                    stmt.setInt(4, newCount);
                    stmt.setLong(5, timestamp);
                    stmt.setInt(6, newCount);
                    stmt.setLong(7, timestamp);
                    stmt.executeUpdate();
                }

                return newCount;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error incrementing spawner count", e);
                return -1;
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Delete chunk data
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> deleteChunkData(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                String sql = "DELETE FROM spawner_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, world);
                    stmt.setInt(2, chunkX);
                    stmt.setInt(3, chunkZ);
                    stmt.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error deleting chunk data", e);
                return false;
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Get total number of tracked chunks
     */
    public CompletableFuture<Integer> getTotalChunks() {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                String sql = "SELECT COUNT(*) as count FROM spawner_chunks";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        return rs.getInt("count");
                    }
                    return 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error getting total chunks", e);
                return 0;
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    /**
     * Get total number of spawners across all chunks
     */
    public CompletableFuture<Integer> getTotalSpawners() {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                String sql = "SELECT SUM(spawner_count) as total FROM spawner_chunks";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        return rs.getInt("total");
                    }
                    return 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error getting total spawners", e);
                return 0;
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    /**
     * Close database connection
     */
    public void close() {
        lock.writeLock().lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}

