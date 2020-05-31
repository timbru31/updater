package net.gravitydevelopment.updater;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.github.glytching.junit.extension.folder.TemporaryFolder;
import io.github.glytching.junit.extension.folder.TemporaryFolderExtension;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit5.HoverflyExtension;
import net.gravitydevelopment.updater.Updater.UpdateResult;
import net.gravitydevelopment.updater.Updater.UpdateType;

@ExtendWith(TemporaryFolderExtension.class)
@ExtendWith(HoverflyExtension.class)
@SuppressWarnings("checkstyle:MissingCtor")
class UpdaterSucessTest {
    private static final String APPLICATION_JSON = "application/json";
    private static final String LOGGER_NAME = "UpdaterTest";
    private static final String PLUGIN_NAME = "ExamplePlugin";
    private static final String CURSEFORGE_EXAMPLE_PROJECT_RESPONSE = "[{\"name\":\"SilkSpawners v1.0\",\"projectId\":35890,\"releaseType\":\"release\"}]";
    private static final String CURSEFORGE_API_PATH = "/servermods/files";
    private static final String CURSEFORGE_API_URL = "https://servermods.forgesvc.net";

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should not download the same version (NO_UPDATE)")
    public void shouldIgnoreTheSameVersion(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(CURSEFORGE_EXAMPLE_PROJECT_RESPONSE, APPLICATION_JSON))));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        final PluginDescriptionFile mockedDescription = mock(PluginDescriptionFile.class);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);
        when(mockedDescription.getVersion()).thenReturn("1.0");
        when(mockedPlugin.getDescription()).thenReturn(mockedDescription);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.NO_UPDATE, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should not download a lower version (NO_UPDATE)")
    public void shouldIgnoreLowerVersion(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(CURSEFORGE_EXAMPLE_PROJECT_RESPONSE, APPLICATION_JSON))));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        final PluginDescriptionFile mockedDescription = mock(PluginDescriptionFile.class);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);
        when(mockedDescription.getVersion()).thenReturn("2.0");
        when(mockedPlugin.getDescription()).thenReturn(mockedDescription);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.NO_UPDATE, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should not download the a wrong release type (FAIL_BADID)")
    public void shouldNotDownloadAWrongReleaseType(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(
                success("[{\"name\":\"SilkSpawners v1.0\",\"projectId\":35890,\"releaseType\":\"alpha\"}]", APPLICATION_JSON))));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        final PluginDescriptionFile mockedDescription = mock(PluginDescriptionFile.class);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);
        when(mockedDescription.getVersion()).thenReturn("1.0");
        when(mockedPlugin.getDescription()).thenReturn(mockedDescription);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_BADID, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should download the latest release (UPDATE_AVAILABLE)")
    public void shouldDownloadTheLatestRelease(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(success(
                "[{\"name\":\"SilkSpawners v1.0\",\"projectId\":35890,\"releaseType\":\"release\"}, {\"name\":\"SilkSpawners v2.0\",\"projectId\":35890,\"releaseType\":\"release\"}]",
                APPLICATION_JSON))));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        final PluginDescriptionFile mockedDescription = mock(PluginDescriptionFile.class);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);
        when(mockedDescription.getVersion()).thenReturn("1.0");
        when(mockedPlugin.getDescription()).thenReturn(mockedDescription);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.UPDATE_AVAILABLE, updateResult);
    }
}
