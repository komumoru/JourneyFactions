# JourneyFactions

A client-side companion mod that displays faction territories on JourneyMap.

## Features

- **Real-time faction territory display** on JourneyMap
- **Hole detection** - territories with unclaimed chunks inside show correctly
- **Clean polygon rendering** - no ugly chunk grids
- **Multiple disconnected regions** support

## Requirements

- **Minecraft 1.20.1**
- **Fabric Loader 0.14.21+**
- **Fabric API**
- **JourneyMap 5.10.3+**

## Installation

1. Download the latest release from [Releases](https://github.com/Arona74/JourneyFactions/releases)
2. Place the `.jar` file in your `mods` folder
3. Launch Minecraft with Fabric

## Usage

### What You'll See
- **Faction territories** displayed as colored polygons on the map
- **Holes in territories** shown as unclaimed (gray) areas
- **Clean boundaries** without chunk grid lines
- **Multiple regions** for disconnected faction lands

## How It Works

JourneyFactions is a **client-side only** mod that:

1. **Listens for faction data** from server-side faction mods
2. **Processes territory shapes** with proper hole detection
3. **Creates JourneyMap overlays** using clean polygon rendering
4. **Updates in real-time** as territories change

### Need a specific version of Faction Mod (Fork made by Arona74)
https://github.com/Arona74/factions

## Configuration

The mod works out of the box with no configuration needed.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Issues & Support

Found a bug or have a suggestion? Please open an issue on the [GitHub Issues](https://github.com/Arona74/JourneyFactions/issues) page.

## Credits

- Built with the [JourneyMap API](https://github.com/TeamJM/journeymap-api)
- Inspired by territory visualization needs in faction servers