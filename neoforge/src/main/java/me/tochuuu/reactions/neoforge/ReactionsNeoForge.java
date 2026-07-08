package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.Reactions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Reactions.MOD_ID)
public final class ReactionsNeoForge {
    public ReactionsNeoForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Run our common setup.
        Reactions.init();
        ReactionsNeoForgeNetworking.init(modEventBus);
        ReactionsNeoForgeClient.init(modEventBus);
    }
}
