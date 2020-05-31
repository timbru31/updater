package net.gravitydevelopment.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.github.glytching.junit.extension.folder.TemporaryFolder;
import io.github.glytching.junit.extension.folder.TemporaryFolderExtension;
import net.gravitydevelopment.updater.Updater.UpdateResult;
import net.gravitydevelopment.updater.Updater.UpdateType;

@ExtendWith(TemporaryFolderExtension.class)
@SuppressWarnings("checkstyle:MissingCtor")
class UpdaterDisabledTest {

    @Test
    @DisplayName("should return DISABLED when disabled via config file")
    @SuppressWarnings("static-method")
    public void shouldNotRunIfDisabled(final TemporaryFolder temporaryFolder) throws IOException {
        final File updaterFile = temporaryFolder.createDirectory("Updater");
        final File updaterConfigFile = new File(updaterFile, "config.yml");

        final YamlConfiguration config = new YamlConfiguration();
        config.addDefault("disable", Boolean.TRUE);
        config.options().copyDefaults(true);
        config.save(updaterConfigFile);
        final File pluginFolder = temporaryFolder.createDirectory("ExamplePlugin");

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger("UpdaterTest");
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.DISABLED, updateResult);
    }
}
