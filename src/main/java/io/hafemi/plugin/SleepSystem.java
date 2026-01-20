package io.hafemi.plugin;

import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.CanSleepInWorld;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SleepSystem extends TickingSystem<EntityStore> {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull Store<EntityStore> store
    ) {
        final World world = store.getExternalData().getWorld();
        final List<Player> players = getPlayersSleeping(world);
        if (canSkipNight(world, players)) {
            skipNight(store, world);
        }
    }

    private List<Player> getPlayersSleeping(World world) {
        List<Player> players = new ArrayList<>();

        for (PlayerRef playerRef: world.getPlayerRefs()) {
            final Ref<EntityStore> playerRefEntity = playerRef.getReference();
            if (playerRefEntity == null) continue;

            final Store<EntityStore> playerRefStore = playerRefEntity.getStore();
            final Player player = playerRefStore.getComponent(
                    playerRefEntity,
                    Player.getComponentType()
            );

            if (player != null) {
                final MovementStatesComponent movementStatesComponent =
                        playerRefStore.getComponent(
                        playerRefEntity,
                        MovementStatesComponent.getComponentType()
                );
                if (movementStatesComponent == null) continue;

                final MovementStates movementStates = movementStatesComponent.getMovementStates();
                if (movementStates.sleeping) {
                    players.add(player);
                }
            }
        }

        return players;
    }

    private boolean canSkipNight(World world, List<Player> players) {
        if (players.isEmpty()) return false;
        return !CanSleepInWorld.check(world).isNegative();
    }

    // skipNight, computeWakeupInstant and computeIrlSeconds are functions by Hytale
    private void skipNight(Store<EntityStore> store, World world) {
        WorldSomnolence worldSomnolence = store.getResource(WorldSomnolence.getResourceType());
        WorldSleep worldState = worldSomnolence.getState();
        if (worldState != WorldSleep.Awake.INSTANCE) {
            return;
        }

        WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());

        Instant now = timeResource.getGameTime();
        float wakeUpHour = world.getGameplayConfig().getWorldConfig().getSleepConfig().getWakeUpHour();
        Instant target = computeWakeupInstant(now, wakeUpHour);
        float irlSeconds = computeIrlSeconds(now, target);

        worldSomnolence.setState(new WorldSlumber(now, target, irlSeconds));
    }

    private Instant computeWakeupInstant(Instant now, float wakeUpHour) {
        LocalDateTime ldt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        int hours = (int) wakeUpHour;
        float fractionalHour = wakeUpHour - hours;
        LocalDateTime wakeUpTime = ldt.toLocalDate().atTime(hours, (int)(fractionalHour * 60));

        if (!ldt.isBefore(wakeUpTime)) {
            wakeUpTime = wakeUpTime.plusDays(1);
        }

        return wakeUpTime.toInstant(ZoneOffset.UTC);
    }

    private float computeIrlSeconds(Instant startInstant, Instant targetInstant) {
        long ms = Duration.between(startInstant, targetInstant).toMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        double seconds = Math.max(3.0, hours / 6.0);
        return (float) Math.ceil(seconds);
    }

}