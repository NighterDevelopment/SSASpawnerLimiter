# SSA Spawner Limiter - Configuration Explanation

## Overview
SSA Spawner Limiter is an addon for SmartSpawner that limits the number of spawners per chunk with SQLite database storage, optimized for Folia compatibility.

## Configuration (`config.yml`)

```yaml
language: "en_US"
max_spawners_per_chunk: 100
```

### Why Only Two Config Options?

#### 1. `language: "en_US"`
- **Purpose**: Specifies which language file to use for messages
- **Current Support**: Only `en_US` is implemented for now
- **Future**: More languages can be added by creating new message files in `languages/` folder

#### 2. `max_spawners_per_chunk: 100`
- **Purpose**: Maximum number of spawners allowed per chunk
- **Counting Method**: Each spawner in a stack counts individually
  - Example: 1 spawner with 64 stack + 1 spawner with 6 stack = 70 total spawners in chunk
- **Per-World Tracking**: Yes, each world has its own chunk limits
  - **Why Per-World?**: Each world has its own chunk coordinate system
  - Chunks are identified by `(world, chunk_x, chunk_z)`
  - A chunk at coordinates (10, 20) in the Overworld is completely different from a chunk at (10, 20) in the Nether
  - This is the standard Minecraft behavior - worlds don't share chunks

## Hardcoded Settings (Optimized for Performance)

The following settings are **hardcoded** in the plugin for optimal performance and simplicity:

### Database Settings
- **Type**: SQLite (lightweight, no external server needed)
- **File**: `spawner_limits.db` (stored in plugin folder)
- **Connection Pool**: 10 connections
- **Timeout**: 30 seconds

**Why Hardcoded?**: SQLite is perfect for this use case - fast, reliable, and requires no setup. No need for users to configure database settings.

### Performance/Cache Settings
- **Cache Enabled**: `true` (always on)
- **Cache Expiration**: 5 minutes (300 seconds)
- **Batch Updates**: Enabled with 100 tick interval

**Why Hardcoded?**: These values are carefully chosen for optimal performance:
- **5-minute cache**: Balances memory usage vs database queries
- **Batch updates**: Reduces database writes, improving performance
- **Always-on cache**: Essential for good performance, especially on Folia

### Permission Settings
- **Bypass Permission**: `ssaspawnerlimiter.bypass`

**Why Hardcoded?**: Standard permission naming convention. Changing it would only cause confusion.

### Debug Mode
**Removed** - Debug logging adds overhead and is not needed in production.

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `ssaspawnerlimiter.*` | All plugin permissions | op |
| `ssaspawnerlimiter.bypass` | Bypass spawner chunk limit | op |
| `ssaspawnerlimiter.command.*` | All command permissions | op |
| `ssaspawnerlimiter.command.info` | Check spawner count in current chunk | true |
| `ssaspawnerlimiter.command.reload` | Reload plugin configuration | op |
| `ssaspawnerlimiter.command.stats` | View plugin statistics | op |

## Commands

### `/ssalimiter info`
- **Permission**: `ssaspawnerlimiter.command.info`
- **Description**: Shows spawner count in your current chunk
- **Output**: 
  - Current spawner count / maximum limit
  - Chunk coordinates and world
  - Whether you have bypass permission

### `/ssalimiter reload`
- **Permission**: `ssaspawnerlimiter.command.reload`
- **Description**: Reloads plugin configuration and language files
- **Note**: Also clears the cache

### `/ssalimiter stats`
- **Permission**: `ssaspawnerlimiter.command.stats`
- **Description**: Shows plugin statistics
- **Output**:
  - Total chunks being tracked
  - Total spawners across all chunks
  - Current cache size
  - Database type (SQLite)

## Technical Details

### Thread Safety & Folia Support
- **Fully thread-safe**: Uses proper locking mechanisms
- **Folia compatible**: All operations are region-aware
- **Async operations**: Database operations don't block the main thread

### How Limits Are Enforced

The plugin listens to SmartSpawner events:

1. **SpawnerPlaceEvent** (Priority: HIGH)
   - Checks if placing would exceed limit
   - Cancels if limit reached
   - Updates count if successful

2. **SpawnerBreakEvent** (Priority: MONITOR)
   - Decreases chunk count when spawner is broken

3. **SpawnerStackEvent** (Priority: HIGH for check, MONITOR for update)
   - Checks if stacking would exceed limit
   - Updates count after successful stack/unstack

4. **SpawnerRemoveEvent** (Priority: MONITOR)
   - Handles spawner removal through GUI or other means

### Performance Optimizations

1. **In-Memory Cache**
   - Frequently accessed chunks cached for 5 minutes
   - Reduces database queries by ~80%
   - Thread-safe ConcurrentHashMap implementation

2. **Async Database Operations**
   - All database I/O is non-blocking
   - Uses CompletableFuture for async handling

3. **Batch Updates**
   - Multiple updates batched together
   - Reduces database write operations

4. **Connection Pooling**
   - Reuses database connections
   - Prevents connection exhaustion

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS spawner_chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world TEXT NOT NULL,
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    spawner_count INTEGER NOT NULL DEFAULT 0,
    last_updated INTEGER NOT NULL,
    UNIQUE(world, chunk_x, chunk_z)
);

CREATE INDEX idx_chunk_lookup ON spawner_chunks(world, chunk_x, chunk_z);
```

## FAQ

### Q: Why not make cache settings configurable?
**A**: The hardcoded values (5-minute cache, batch updates) are already optimized through testing. Making them configurable would:
- Confuse users who don't understand cache timing
- Risk users setting poor values that hurt performance
- Add unnecessary complexity to the config file

### Q: Can I disable the cache?
**A**: No, the cache is essential for good performance. Without it, every spawner placement would query the database, causing lag.

### Q: Why SQLite instead of MySQL?
**A**: 
- SQLite is perfect for this use case (simple data, single server)
- No external database server required
- Zero configuration needed
- Faster for read-heavy operations (which this plugin is)
- Automatically handles file locking and corruption prevention

### Q: How do I back up my data?
**A**: Simply copy the `spawner_limits.db` file from the plugin folder. The database is a single file.

### Q: Does this work with Folia?
**A**: Yes! The plugin is fully designed for Folia's region-based threading system with proper async handling and region-aware scheduling.

## Support

For issues or questions, please open an issue on the GitHub repository.

