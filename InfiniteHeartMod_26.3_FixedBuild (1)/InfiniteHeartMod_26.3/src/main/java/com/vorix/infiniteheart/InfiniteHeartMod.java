package com.vorix.infiniteheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.Command;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class InfiniteHeartMod implements ModInitializer {
    private static final String HEART_NAME = "Infinite Heart";
    private static final Set<UUID> ELIMINATED = new HashSet<>();
    private static Path dataFile;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(InfiniteHeartMod::loadEliminated);
        ServerLifecycleEvents.SERVER_STOPPING.register(InfiniteHeartMod::saveEliminated);

        // A player carrying the named Infinite Heart cannot take damage at all.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && hasHeart(player)) {
                return false;
            }
            return true;
        });

        // Covers forced fatal-death paths too: cancelling damage alone is not enough
        // for commands or other mechanics that directly trigger death.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && hasHeart(player)) {
                player.setHealth(player.getMaxHealth());
                return false;
            }
            return true;
        });

        // Any player who dies without the heart is marked eliminated. Their UUID is saved
        // so leaving/rejoining or restarting the server cannot clear the elimination.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && !hasHeart(player)) {
                ELIMINATED.add(player.getUUID());
                saveEliminated(player.level().getServer());
                player.sendSystemMessage(Component.literal("You have been eliminated. You can only return if someone revives you with an Infinite Heart."));
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (ELIMINATED.contains(newPlayer.getUUID())) {
                newPlayer.setGameMode(GameType.SPECTATOR);
                newPlayer.sendSystemMessage(Component.literal("You are eliminated. Someone must use an Infinite Heart to revive you."));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (ELIMINATED.contains(player.getUUID())) {
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Component.literal("You are still eliminated. Ask someone to revive you with an Infinite Heart."));
            }
        });

        registerCommands();
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("infiniteheart")
                .then(Commands.literal("give")
                    .requires(source -> source.getEntity() instanceof ServerPlayer p && source.getServer().getPlayerList().isOp(p.getGameProfile()))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            ItemStack heart = new ItemStack(Items.END_CRYSTAL);
                            heart.set(DataComponents.CUSTOM_NAME, Component.literal(HEART_NAME));
                            boolean added = target.getInventory().add(heart);
                            if (!added) {
                                context.getSource().sendFailure(Component.literal("Your inventory is full; no heart was given."));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Gave an Infinite Heart to " + target.getGameProfile().name() + "."), true);
                            target.sendSystemMessage(Component.literal("You received the Infinite Heart. Keep it in your inventory to become immortal."));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("revive")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer reviver = context.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");

                            if (!isHeart(reviver.getMainHandItem())) {
                                context.getSource().sendFailure(Component.literal("Hold the Infinite Heart in your main hand to revive someone."));
                                return 0;
                            }
                            if (!ELIMINATED.contains(target.getUUID())) {
                                context.getSource().sendFailure(Component.literal(target.getGameProfile().name() + " is not eliminated."));
                                return 0;
                            }

                            reviver.getMainHandItem().shrink(1);
                            ELIMINATED.remove(target.getUUID());
                            target.setGameMode(GameType.SURVIVAL);
                            target.setHealth(target.getMaxHealth());
                            target.getFoodData().setFoodLevel(20);
                            saveEliminated(context.getSource().getServer());

                            target.sendSystemMessage(Component.literal("You have been revived by " + reviver.getGameProfile().name() + "!"));
                            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                Component.literal(target.getGameProfile().name() + " has been brought back to life by " + reviver.getGameProfile().name() + "!"), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("status")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (hasHeart(player)) {
                            context.getSource().sendSuccess(() -> Component.literal("The Infinite Heart protects you from all damage while it is in your inventory."), false);
                        } else if (ELIMINATED.contains(player.getUUID())) {
                            context.getSource().sendSuccess(() -> Component.literal("You are eliminated. You need someone to revive you."), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("You are alive, but you do not have an Infinite Heart."), false);
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
        ));
    }

    private static boolean hasHeart(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isHeart(player.getInventory().getItem(slot))) return true;
        }
        return false;
    }

    private static boolean isHeart(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.END_CRYSTAL || !stack.has(DataComponents.CUSTOM_NAME)) {
            return false;
        }
        return HEART_NAME.equals(stack.get(DataComponents.CUSTOM_NAME).getString());
    }

    private static void loadEliminated(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("infiniteheart_eliminated.txt");
        ELIMINATED.clear();
        if (!Files.exists(dataFile)) return;
        try {
            for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
                try { ELIMINATED.add(UUID.fromString(line.trim())); }
                catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException exception) {
            System.err.println("[Infinite Heart] Could not load eliminated players: " + exception.getMessage());
        }
    }

    private static void saveEliminated(MinecraftServer server) {
        if (server == null) return;
        if (dataFile == null) dataFile = server.getWorldPath(LevelResource.ROOT).resolve("infiniteheart_eliminated.txt");
        try {
            Files.write(dataFile, ELIMINATED.stream().map(UUID::toString).sorted().collect(Collectors.toList()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("[Infinite Heart] Could not save eliminated players: " + exception.getMessage());
        }
    }
}
