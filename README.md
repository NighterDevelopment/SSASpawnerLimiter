# 🎯 SSAddon SpawnerLimiter

SSAddon (SmartSpawner Addon) that limits spawner placement per chunk based on spawner stacks, with Folia support!

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4+-green.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Server-Paper%20%7C%20Folia-blue.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)

## 📋 Requirements

- **Minecraft**: 1.21.4+
- **Server**: Paper or Folia
- **Java**: 21+
- **SmartSpawner**: 1.5.7.1+

## 📦 Installation

1. Install [SmartSpawner](https://modrinth.com/plugin/smart-spawner-plugin) plugin
2. Download SSASpawnerLimiter
3. Place the `.jar` file in your `plugins` folder
4. Restart the server
5. Configure settings in `plugins/SSASpawnerLimiter/config.yml`

## 🎮 Commands

| Command | Description | Aliases |
|---------|-------------|---------|
| `/ssaspawnerlimiter reload` | Reload plugin configuration | `/ssalimiter reload` |
| `/ssaspawnerlimiter info` | Check spawner count in current chunk | `/ssalimiter info` |
| `/ssaspawnerlimiter check <player>` | Check spawner limit for a specific player | `/ssalimiter check <player>` |
| `/ssaspawnerlimiter stats` | View plugin statistics | `/ssalimiter stats` |

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `ssaspawnerlimiter.bypass` | Bypass spawner chunk limit | `false` |
| `ssaspawnerlimiter.command.use` | Base permission for all commands | `op` |
| `ssaspawnerlimiter.command.reload` | Use reload command | `op` |
| `ssaspawnerlimiter.command.info` | Use info command | `op` |
| `ssaspawnerlimiter.command.check` | Use check command | `op` |
| `ssaspawnerlimiter.command.stats` | Use stats command | `op` |

## 📖 How It Works

SSASpawnerLimiter counts **spawner stacks** instead of just spawner blocks:
- 1 spawner block with 64 stacks = **64 count**
- 1 spawner block with 6 stacks = **6 count**
- **Total in chunk**: 70 count

This ensures fair limiting based on actual spawner capacity, not just physical blocks.

## 🗄️ Database

The plugin uses SQLite to store spawner chunk data. You can view and manage the database using:

**[SQLite Viewer](https://sqliteviewer.app/)** - A free online tool to view and edit SQLite databases

Database location: `plugins/SSASpawnerLimiter/spawner_limits.db`

## 📄 License

This project is licensed under the CC-BY-NC-SA-4.0 License - see the [LICENSE](LICENSE) file for details.
