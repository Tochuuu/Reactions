package me.tochuuu.reactions.fabric.client;

import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.fabric.ReactionsFabricNetworking;
import net.fabricmc.api.ClientModInitializer;

public final class ReactionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Reactions.init();
        ReactionsFabricNetworking.init();
        ReactionsFabricClientNetworking.init();
        ReactionsClient.init();
    }
}
