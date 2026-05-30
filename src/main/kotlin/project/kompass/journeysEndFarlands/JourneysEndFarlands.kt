package project.kompass.journeysEndFarlands

import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.java.JavaPlugin

class JourneysEndFarlands : JavaPlugin() {

    override fun onEnable() {
        logger.info("JourneysEndFarlands has been enabled.")
        logger.info("Ensure you have assigned this generator to your world in bukkit.yml.")
    }

    override fun onDisable() {
        logger.info("JourneysEndFarlands has been disabled.")
    }

    override fun getDefaultWorldGenerator(worldName: String, id: String?): ChunkGenerator {
        return FarlandsChunkGenerator()
    }
}