package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.Reactions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Reactions.MOD_ID)
public final class ReactionsNeoForge {
    public ReactionsNeoForge(IEventBus modEventBus) {
        // Run our common setup.
        Reactions.init();
        ReactionsNeoForgeNetworking.init(modEventBus);
        if (FMLEnvironment.getDist().isClient()) {
            ReactionsNeoForgeClient.init(modEventBus);
        }
    }
}
