package de.jpx3.common.config;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.*;

public final class ConfigurationService {

  private final ConfigurationNode configuration;

  private ConfigurationService(ConfigurationNode configuration) {
    this.configuration = configuration;
  }

  public ConfigurationNode configuration() {
    return configuration;
  }

  private static final String DATAFOLDER_CREATION_ERROR = "Unable to create data folder";
  private static final String CONFIGURATION_CREATION_ERROR = "Unable to create configuration file";

  public static ConfigurationService createFrom(File dataFolder, String fileName) {
    Preconditions.checkNotNull(dataFolder);
    Preconditions.checkNotNull(fileName);

    File configurationFile = new File(dataFolder, fileName);

    ensureConfigurationExistence(dataFolder, configurationFile, fileName);

    try {
      ConfigurationNode configuration;

      if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .file(configurationFile)
                .build();
        configuration = loader.load();

      } else {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .file(configurationFile)
                .build();
        configuration = loader.load();
      }

      return new ConfigurationService(configuration);

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void ensureConfigurationExistence(File dataFolder, File configurationFile, String resourceName) {
    if (!dataFolder.exists()) {
      if (!dataFolder.mkdirs()) {
        System.out.println(DATAFOLDER_CREATION_ERROR);
      }
    }

    if (!configurationFile.exists()) {
      try {
        configurationFile.createNewFile();

        moveResourceToFile(resourceName, configurationFile);

      } catch (IOException e) {
        throw new IllegalStateException(CONFIGURATION_CREATION_ERROR, e);
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