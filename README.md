# SSA Spawner Limiter

An advanced addon for SmartSpawner that implements chunk-based spawner limits with SQLite database storage and Folia compatibility.

## Features

- **Chunk-based Spawner Limits**: Limit the number of spawners per chunk
- **Stack-aware Counting**: Each spawner in a stack counts toward the limit
- **SQLite Database**: Persistent storage with automatic connection pooling
- **Thread-safe**: Fully compatible with Folia's region-based threading
- **Performance Optimized**: 
  - In-memory caching with configurable expiration
  - Async database operations
  - No memory leaks or race conditions
- **Brigadier Commands**: Modern command system with tab completion
- **Language System**: Multi-language support (currently en_US)
- **Bypass Permission**: Allow certain players to bypass limits

## Requirements

- **Minecraft**: 1.21.4+
- **Server**: Paper or Folia
- **Java**: 21+
- **SmartSpawner**: 1.5.7+

## Installation

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Ensure SmartSpawner is installed and enabled
4. Restart your server
5. Configure the plugin in `plugins/SSASpawnerLimiter/config.yml`

## Configuration

```yaml
# Language (currently only en_US supported)
language: "en_US"

# Database settings
database:
  type: "sqlite"
  filename: "spawner_limits.db"
  pool-size: 10
  timeout: 30

# Chunk limit settings
chunk-limit:
  enabled: true
  # Maximum spawners per chunk (including stacks)
  max-spawners-per-chunk: 100
  per-world: true
  bypass-permission: "ssaspawnerlimiter.bypass"

# Performance settings
performance:
  cache-enabled: true
  cache-expiration: 300  # seconds
  batch-updates: true
  batch-update-interval: 100  # ticks

# Debug mode
debug: false
```

## How It Works

### Stack Counting
Each spawner in a stack counts individually toward the chunk limit:
- **Example 1**: 1 spawner with 64 stack + 1 spawner with 6 stack = **70 total** in chunk
- **Example 2**: 10 spawners with 10 stack each = **100 total** in chunk

### Event Handling
The addon monitors these SmartSpawner events:
- **SpawnerPlaceEvent**: Checks limit before allowing placement
- **SpawnerBreakEvent**: Decreases chunk count when broken
- **SpawnerStackEvent**: Validates limit when stacking spawners
- **SpawnerRemoveEvent**: Updates count when removed via GUI

### Database
- SQLite database stores chunk spawner counts persistently
- Thread-safe with ReadWriteLock for Folia compatibility
- Automatic table creation and migration
- Efficient indexing for fast lookups

### Caching
- In-memory cache reduces database queries
- Configurable expiration time
- Automatic cleanup of expired entries
- Thread-safe ConcurrentHashMap implementation

## Commands

All commands support both full and alias formats:
- `/ssaspawnerlimiter` or `/ssalimiter`

### Subcommands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/ssalimiter reload` | `ssaspawnerlimiter.command.reload` | Reload configuration |
| `/ssalimiter info` | `ssaspawnerlimiter.command.info` | Check current chunk spawner count |
| `/ssalimiter check <player>` | `ssaspawnerlimiter.command.check` | Check another player's chunk |
| `/ssalimiter stats` | `ssaspawnerlimiter.command.stats` | View database statistics |

## Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `ssaspawnerlimiter.command.use` | Access to main command | op |
| `ssaspawnerlimiter.command.reload` | Reload configuration | op |
| `ssaspawnerlimiter.command.info` | Check own chunk info | op |
| `ssaspawnerlimiter.command.check` | Check other chunks | op |
| `ssaspawnerlimiter.command.stats` | View statistics | op |
| `ssaspawnerlimiter.bypass` | Bypass chunk limits | op |

## Messages

All messages are customizable in `plugins/SSASpawnerLimiter/languages/en_US/messages.yml`

Key messages:
- `limit_reached`: Shown when trying to exceed the limit
- `limit_warning`: Warning about current chunk usage
- `limit_info`: Information about chunk status

## Performance Considerations

### Async Operations
- All database operations are async via CompletableFuture
- No blocking on main thread
- Proper error handling and fallbacks

### Thread Safety
- ReadWriteLock for database operations
- ConcurrentHashMap for cache
- Folia region scheduler for location-based tasks

### Memory Management
- Cache with automatic expiration
- Periodic cleanup task
- No memory leaks with proper resource cleanup

### Database Optimization
- Indexed queries for fast lookups
- Connection pooling (planned)
- Batch updates (optional)

## Folia Compatibility

The addon is fully compatible with Folia's region-based threading:
- Uses region schedulers for location-based operations
- Thread-safe data structures throughout
- No global state that could cause issues
- Falls back gracefully on standard Paper servers

## Building from Source

```bash
# Clone the repository
git clone https://github.com/NighterDevelopment/SmartSpawner.git
cd SmartSpawner/SSASpawnerLimiter

# Build with Gradle
./gradlew shadowJar

# Output: build/libs/SSAddon-SpawnerLimiter-1.0.0.jar
```

## API Usage

Other plugins can interact with the addon:

```java
SSASpawnerLimiter plugin = (SSASpawnerLimiter) Bukkit.getPluginManager().getPlugin("SSASpawnerLimiter");
ChunkLimitService service = plugin.getChunkLimitService();

// Check if player can place spawner
Location location = player.getLocation();
service.canPlaceSpawner(player, location, quantity).thenAccept(canPlace -> {
    if (canPlace) {
        // Allow placement
    } else {
        // Deny placement
    }
});

// Get current spawner count
ChunkKey key = new ChunkKey(chunk);
service.getSpawnerCount(key).thenAccept(count -> {
    // Use count
});
```

## Troubleshooting

### Database Issues
- Check `plugins/SSASpawnerLimiter/spawner_limits.db` exists
- Enable debug mode in config
- Check console for SQL errors

### Cache Issues
- Try disabling cache temporarily
- Clear cache with `/ssalimiter reload`
- Check cache expiration time

### Folia Issues
- Ensure you're using Folia-compatible schedulers
- Check for region threading errors in console
- Verify no blocking operations on wrong threads

## Support

- **Issues**: [GitHub Issues](https://github.com/NighterDevelopment/SmartSpawner/issues)
- **Discord**: Join our support server

## License

This project is licensed under the same license as SmartSpawner.

## Credits

- **Author**: Nighter
- **SmartSpawner**: Base plugin by Nighter
- **Libraries**: 
  - SQLite JDBC
  - PluginLangCore
  - PluginUpdateCore
  - Lombok

## Changelog

### Version 1.0.0
- Initial release
- Chunk-based spawner limiting
- SQLite database storage
- Folia compatibility
- Caching system
- Brigadier commands
- Multi-language support

