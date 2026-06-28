package com.pixelmc.pixellogic;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelLogicMod implements ModInitializer {
    public static final String MOD_ID = "pixel-logic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("PixelLogic initialized.");
    }
}
