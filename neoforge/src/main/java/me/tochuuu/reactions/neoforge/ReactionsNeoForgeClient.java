package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.client.ReactionsConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

final class ReactionsNeoForgeClient {
    private ReactionsNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        ReactionsClient.init();
        ReactionsNeoForgeNetworking.initClient(modEventBus);
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (container, parent) -> new ReactionsConfigScreen(parent));
    }
}
