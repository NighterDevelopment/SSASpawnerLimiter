# SSAddon SpawnerLimiter

SSAddon (SmartSpawner Addon) that limits spawner placement per chunk and per player based on spawner stacks, with Folia support!

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4+-green.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Server-Paper%20%7C%20Folia-blue.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)

## Requirements

- **Minecraft**: 1.21.4+
- **Server**: Paper or Folia
- **Java**: 21+
- **SmartSpawner**: 1.5.7.1+

## Installation

1. Install [SmartSpawner](https://modrinth.com/plugin/smart-spawner-plugin) plugin
2. Download SSASpawnerLimiter
3. Place the `.jar` file in your `plugins` folder
4. Restart the server
5. Configure settings in `plugins/SSASpawnerLimiter/config.yml`

## Commands

| Command | Description | Aliases |
|---------|-------------|---------|
| `/ssaspawnerlimiter reload` | Reload plugin configuration | `/ssalimiter reload` |
| `/ssaspawnerlimiter info` | Check spawner count in current chunk | `/ssalimiter info` |
| `/ssaspawnerlimiter check <player>` | Check spawner limit for player's chunk | `/ssalimiter check <player>` |
| `/ssaspawnerlimiter checkplayer <player>` | Check player's global spawner count | `/ssalimiter checkplayer <player>` |
| `/ssaspawnerlimiter stats` | View plugin statistics | `/ssalimiter stats` |

## Permissions

### Bypass Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `ssaspawnerlimiter.bypass` | Bypass spawner chunk limit | `false` |
| `ssaspawnerlimiter.perplayer.bypass` | Bypass per-player spawner limit (unlimited) | `false` |

### Per-Player Limit Tiers
You can create **custom limit tiers** using the permission pattern: `ssaspawnerlimiter.perplayer.<number>`

**Examples:**
- `ssaspawnerlimiter.perplayer.1500` → Allows 1500 spawners globally
- `ssaspawnerlimiter.perplayer.2000` → Allows 2000 spawners globally
- `ssaspawnerlimiter.perplayer.5000` → Allows 5000 spawners globally
- `ssaspawnerlimiter.perplayer.10000` → Allows 10000 spawners globally
- Any number you want!

> **Note:** Players with multiple tier permissions will get the highest value. Default limit is configured in `config.yml` as `max_spawners_per_player`.

### Command Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `ssaspawnerlimiter.command.use` | Base permission for all commands | `op` |
| `ssaspawnerlimiter.command.reload` | Use reload command | `op` |
| `ssaspawnerlimiter.command.info` | Use info command | `op` |
| `ssaspawnerlimiter.command.check` | Use check command | `op` |
| `ssaspawnerlimiter.command.checkplayer` | Use checkplayer command | `op` |
| `ssaspawnerlimiter.command.stats` | Use stats command | `op` |

## How It Works

SSASpawnerLimiter provides **two independent limit systems**:

### Chunk-Based Limit
Limits spawner stacks per chunk to prevent chunk overloading:
- 1 spawner block with 64 stacks = **64 count**
- 1 spawner block with 6 stacks = **6 count**
- **Total in chunk**: 70 count
- Default: 100 spawners per chunk

### Per-Player Limit
Limits total spawner stacks a player can place globally:
- Tracks all spawners placed by each player across the entire server
- Default: 1000 spawners per player
- Can be increased via permission nodes (1500, 2000, 5000, etc.)
- Bypass permission for unlimited spawners

**Both systems can be enabled/disabled independently in `config.yml`:**
```yaml
enable_chunk_limit: true      # Enable chunk-based limiting
enable_player_limit: true     # Enable per-player limiting
```

### Stack Counting Example
- Player places 1 spawner with stack size 10 → **Adds 10** to both chunk and player count
- Player breaks that spawner → **Removes 10** from both counts
- Player stacks spawner from 10 to 20 → **Adds 10** more to both counts
- Counts never go below 0 (zero-floor protection)

This ensures fair limiting based on actual spawner capacity, not just physical blocks.

## Configuration

**config.yml:**
```yaml
# Chunk limit settings
enable_chunk_limit: true
max_spawners_per_chunk: 1000
verify_chunk_count_on_check: true  # Verify actual count from SmartSpawner API

# Per-player limit settings  
enable_player_limit: true
max_spawners_per_player: 500      # Default limit for all players
```

## Database

The plugin uses SQLite to store spawner data in two tables:
- `spawner_chunks` - Chunk spawner counts
- `player_spawners` - Per-player spawner counts

**[SQLite Viewer](https://sqliteviewer.app/)** - Free online tool to view and edit SQLite databases

Database location: `plugins/SSASpawnerLimiter/spawner_limits.db`

## License

This project is licensed under the CC-BY-NC-SA-4.0 License - see the [LICENSE](LICENSE) file for details.
