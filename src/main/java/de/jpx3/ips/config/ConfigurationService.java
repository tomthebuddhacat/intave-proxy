package de.jpx3.ips.config;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import de.jpx3.ips.IntaveProxySupportPlugin;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.*;

public final class ConfigurationService {

  private final ConfigurationNode configuration;

  private ConfigurationService(ConfigurationNode configuration) {
    this.configuration = configuration;
  }

  public ConfigurationNode configuration() {
    return configuration;
  }

  private static final String CONFIGURATION_NAME = "config.conf";
  private static final String DATAFOLDER_CREATION_ERROR = "Unable to create data folder";
  private static final String CONFIGURATION_CREATION_ERROR = "Unable to create configuration file";

  public static ConfigurationService createFrom(IntaveProxySupportPlugin plugin) {
    Preconditions.checkNotNull(plugin);

    File dataFolder = new File("plugins/intave");
    File configurationFile = new File(dataFolder, CONFIGURATION_NAME);

    ensureConfigurationExistence(dataFolder, configurationFile);

    try {
      HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
              .file(configurationFile)
              .build();

      ConfigurationNode configuration = loader.load();
      return new ConfigurationService(configuration);

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void ensureConfigurationExistence(File dataFolder, File configurationFile) {
    if (!dataFolder.exists()) {
      if (!dataFolder.mkdir()) {
        System.out.println(CONFIGURATION_CREATION_ERROR);
      }
    }

    if (!configurationFile.exists()) {
      try {
        configurationFile.createNewFile();

        moveResourceToFile(CONFIGURATION_NAME, configurationFile);

      } catch (IOException e) {
        throw new IllegalStateException(DATAFOLDER_CREATION_ERROR, e);
      }
    }
  }

  private static final String RESOURCE_MOVE_TO_FILE_ERROR_LAYOUT =  "Unable to move resource %s to %s";

  private static void moveResourceToFile(String resource, File outputFile) {
    try {
      ClassLoader classLoader = ConfigurationService.class.getClassLoader();

      try (InputStream inputStream = classLoader.getResourceAsStream(resource);
           OutputStream outputStream = new FileOutputStream(outputFile)) {

        if (inputStream == null) {
          throw new IllegalStateException("Resource not found: " + resource);
        }

        ByteStreams.copy(inputStream, outputStream);
      }

    } catch (IOException exception) {
      String errorMessage = String.format(RESOURCE_MOVE_TO_FILE_ERROR_LAYOUT, resource, outputFile.getAbsolutePath());
      throw new IllegalStateException(errorMessage, exception);
    }
  }
}