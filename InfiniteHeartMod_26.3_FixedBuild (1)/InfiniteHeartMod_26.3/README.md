# Infinite Heart — Minecraft Java 26.3 (Fabric)

A **server-side Fabric mod** intended for a dedicated Minecraft Java 26.3 server. It uses the original uploaded Infinite Heart texture: the heart is a specially named End Crystal, which your texture pack already retextures.

## Features

- **Immortality:** while a player carries the specially named `Infinite Heart` anywhere in their inventory or offhand, all incoming damage is cancelled.
- **Hardcore-style elimination:** dying without the heart records the player's UUID and places them in spectator mode after respawn. The elimination list is saved in the world folder and survives restarts.
- **Revival:** a player can revive an eliminated online player by holding the Infinite Heart in their main hand and running `/infiniteheart revive <player>`. The heart is consumed.
- **Commands:**
  - `/infiniteheart give <player>` — operator-only; gives the named heart.
  - `/infiniteheart revive <player>` — requires a heart in the command user's main hand.
  - `/infiniteheart status` — shows the user's heart/elimination status.

## Requirements

- Minecraft Java Edition **26.3**
- Fabric Loader **0.19.5 or newer**
- Fabric API for 26.3
- Java **25** to build/run the mod

This mod is server-side: players do not need to install the mod itself. They do need the matching resource pack enabled to see the original Infinite Heart texture. The included `resourcepack/InfiniteHeart_26.3.zip` is made from the texture you uploaded.

## Hardcore setup

The mod implements persistent spectator elimination itself. For vanilla hardcore behavior as well, stop the server and set `hardcore=true` in `server.properties`, then start the server again. Back up the world before enabling hardcore.

## Build

Open this folder in IntelliJ IDEA with a Java 25 JDK and let Gradle sync. Run the Gradle task `build`. The output JAR will be in `build/libs/`.

The current execution environment only has Java 21 and no network access to download the Minecraft/Fabric build dependencies, so a compiled JAR could not be produced or launch-tested here. The project source and resource pack are included; build it with the required Java 25/Fabric toolchain before installing it on a live server.

## Install

1. Build the project to produce `infinite-heart-1.0.0.jar`.
2. Put the JAR in the dedicated server's `mods` folder alongside Fabric API.
3. Put `resourcepack/InfiniteHeart_26.3.zip` in the client's `resourcepacks` folder, or host it as your server resource pack.
4. Start the server and use `/infiniteheart give <player>` as an operator.

## Notes

- The heart is identified by its exact custom name (`Infinite Heart`) on an End Crystal. Renaming a regular End Crystal to the exact same name can imitate the heart, so do not allow untrusted players to use an anvil if item forgery is a concern.
- Revival currently targets an **online** eliminated player. They can rejoin in spectator mode and then be revived.
- Test this on a backup world first. This source has not been compiled or run in-game in this environment.


## Build for Minecraft 26.3

This project targets Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.19-SNAPSHOT, Gradle 9.6.0, and Java 25. The project has not been compiled or tested in this environment.

A GitHub Actions workflow is included at `.github/workflows/build.yml`. After putting this folder's contents in a GitHub repository, open **Actions**, run **Build Infinite Heart for Minecraft 26.3**, and download the `infinite-heart-minecraft-26.3` artifact from the completed workflow run. Check server logs and test on a backup world before using it on a live server.
