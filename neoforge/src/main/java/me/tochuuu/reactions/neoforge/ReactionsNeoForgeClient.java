package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.client.ReactionsConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

final class ReactionsNeoForgeClient {
    private ReactionsNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        ReactionsClient.init();
        ReactionsNeoForgeNetworking.initClient(modEventBus);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ReactionsConfigScreen(parent)));
    }
}
