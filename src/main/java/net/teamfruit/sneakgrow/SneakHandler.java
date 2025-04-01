package net.teamfruit.sneakgrow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class SneakHandler implements Listener {
    private final Map<String, PlayerState> states = new HashMap<>();

    private static final ItemStack boneMeal = new ItemStack(Material.BONE_MEAL);
    private static final Object nmsBoneMeal = ReflectionUtil.itemStackAsNmsCopy(boneMeal);

    private final Random rnd = new Random();

    public static class PlayerState {
        public boolean isSneaking;
        public boolean isSneaked;
        public int ticksLastCheck;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        onAction(event.getPlayer());
    }

    private void onAction(Player player) {
        PlayerState state = states.computeIfAbsent(player.getName(), e -> new PlayerState());

        int ticksNow = Bukkit.getCurrentTick();
        int ticksSinceLastCheck = ticksNow - state.ticksLastCheck;
        if (ticksSinceLastCheck >= SneakGrow.cooldown) {
            state.ticksLastCheck = ticksNow;

            World world = player.getWorld();
            Object nmsWorld = ReflectionUtil.craftWorldGetHandle(world);
            Location location = player.getLocation();

            if (state.isSneaked) {
                // 作物成長
                {
                    List<Block> ageables = getAgeableBlockInRange(location, SneakGrow.blockRadius);

                    ageables.forEach(block -> {
                        if (rnd.nextFloat() < SneakGrow.blockPercentage) {
                            Material type = block.getType();
                            if (SneakGrow.enableExtraCrops && (type == Material.SUGAR_CANE || type == Material.CACTUS || type == Material.CHORUS_FLOWER || type == Material.NETHER_WART)) {
                                for (int i = 0; i < SneakGrow.extraRandomTicks; i++) {
                                    block.randomTick();
                                }
                            } else {
                                Object nmsBlockPosition = ReflectionUtil.constructBlockPosition(block.getX(), block.getY(), block.getZ());
                                ReflectionUtil.applyBoneMeal(nmsBoneMeal, nmsWorld, nmsBlockPosition);
                            }

                            if (SneakGrow.showParticles || SneakGrow.playSound)
                                sendPacketGrowBlock(block.getLocation());
                        }
                    });
                }

                // Mob成長
                {
                    List<Ageable> ageables = getAgeableEntityInRange(location, SneakGrow.mobRadius);

                    ageables.forEach(entity -> {
                        if (rnd.nextFloat() < SneakGrow.mobPercentage) {
                            entity.setAge(entity.getAge() + 1);
                            entity.setBreed(true);

                            if (SneakGrow.showParticles)
                                sendPacketGrowEntity(entity.getLocation());
                        }
                    });
                }

                state.isSneaked = false;
            }
        }

        boolean isSneaking = player.isSneaking();
        if (state.isSneaking != isSneaking) {
            state.isSneaking = isSneaking;
            state.isSneaked = true;
        }
    }

    private void sendPacketGrowBlock(Location location) {
        Collection<Player> players = location.getNearbyPlayers(48);
        for (Player player : players) {
            if (SneakGrow.showParticles)
                player.playEffect(location, Effect.BEE_GROWTH, Integer.valueOf(10));
            if (SneakGrow.playSound)
                player.playSound(location, Sound.ITEM_BONE_MEAL_USE, 1, 1);
        }
    }

    private void sendPacketGrowEntity(Location location) {
        Collection<Player> players = location.getNearbyPlayers(48);
        for (Player player : players)
            player.spawnParticle(Particle.HAPPY_VILLAGER, location, 5, .2, .2, .2);
    }

    private List<Block> getAgeableBlockInRange(Location location, int radius) {
        World world = location.getWorld();
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        List<Block> list = new ArrayList<>();
        for (int x = -radius; x <= radius; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(x + centerX, y + centerY, z + centerZ);
                    BlockData blockData = block.getBlockData();
                    if (SneakGrow.enableSaplings && blockData instanceof Sapling) {
                        list.add(block);
                    } else if (SneakGrow.enableCrops && blockData instanceof org.bukkit.block.data.Ageable) {
                        org.bukkit.block.data.Ageable data = (org.bukkit.block.data.Ageable) blockData;
                        if (data.getAge() < data.getMaximumAge())
                            list.add(block);
                    }
                }
        return list;
    }

    private List<org.bukkit.entity.Ageable> getAgeableEntityInRange(Location location, int radius) {
        return location.getNearbyLivingEntities(radius).stream()
                .filter(e -> e instanceof org.bukkit.entity.Ageable)
                .map(org.bukkit.entity.Ageable.class::cast)
                .filter(e -> !e.isAdult() || !e.canBreed())
                .collect(Collectors.toList());
    }
}
