package net.gravitydevelopment.updater;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.badRequest;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.forbidden;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.serverError;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
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
class UpdaterFailTest {

    @Test
    @DisplayName("should return FAIL_DBO when the server responds with a 5XX error")
    public void shouldReturnDBOFailOnServerError(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service("https://servermods.forgesvc.net").get("/servermods/files").anyQueryParams().willReturn(serverError())));

        final File pluginFolder = temporaryFolder.createDirectory("ExamplePlugin");

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger("UpdaterTest");
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_DBO, updateResult);
    }

    @Test
    @DisplayName("should return FAIL_DBO when client sends an invalid request")
    public void shouldReturnDBOFailWithInavlidRequest(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service("https://servermods.forgesvc.net").get("/servermods/files").anyQueryParams().willReturn(badRequest())));

        final File pluginFolder = temporaryFolder.createDirectory("ExamplePlugin");

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger("UpdaterTest");
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, -1, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_DBO, updateResult);
    }

    @Test
    @DisplayName("should return FAIL_APIKEY when the client sends an invalid API token")
    public void shouldReturn403WithInvalidApiKey(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) throws IOException {
        hoverfly.simulate(dsl(service("https://servermods.forgesvc.net").get("/servermods/files").anyQueryParams().willReturn(forbidden())));

        final File updaterFile = temporaryFolder.createDirectory("Updater");
        final File updaterConfigFile = new File(updaterFile, "config.yml");

        final YamlConfiguration config = new YamlConfiguration();
        config.addDefault("api-key", "123");
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

        assertEquals(UpdateResult.FAIL_APIKEY, updateResult);
    }

    @Test
    @DisplayName("should return FAIL_BADID when the client sends an invalid project id")
    public void shouldReturnBadIdWhenProjectDoesNotExist(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(
                dsl(service("https://servermods.forgesvc.net").get("/servermods/files").anyQueryParams().willReturn(success("[]", "application/json"))));

        final File pluginFolder = temporaryFolder.createDirectory("ExamplePlugin");

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger("UpdaterTest");
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_BADID, updateResult);
    }

    @Test
    @DisplayName("should return FAIL_NOVERSION when the name is invalid")
    public void shouldReturnFailNoVersionWhenTheNameIsInvalid(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service("https://servermods.forgesvc.net").get("/servermods/files").anyQueryParams()
                .willReturn(success("[{\"name\":\"SilkSpawners\",\"projectId\":35890,\"releaseType\":\"release\"}]", "application/json"))));

        final File pluginFolder = temporaryFolder.createDirectory("ExamplePlugin");

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger("UpdaterTest");
        final PluginDescriptionFile mockedDescription = mock(PluginDescriptionFile.class);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);
        when(mockedDescription.getVersion()).thenReturn("1.0");
        when(mockedPlugin.getDescription()).thenReturn(mockedDescription);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_NOVERSION, updateResult);
    }

    @Test
    @DisplayName("should return FAIL_BADID when the server responds with an invalid API object")
    public void shouldReturnDBOFailOnServerInvalidAPI(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service("https://servermods.forgesvc.net").get("/servermods/files").anyQueryParams().willReturn(success(
                "[{\"dateReleased\": \"/Date(-62135596800000+0000)/\",\"downloadUrl\": null,\"fileName\": \"TreeCapitator-1.0.jar\",\"fileUrl\": null,\"gameVersion\": \"1.12\",\"md5\": null,\"name\": null,\"projectId\": 294976,\"releaseType\": \"release\"}]",
                "application/json"))));

        final File pluginFolder = temporaryFolder.createDirectory("ExamplePlugin");

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger("UpdaterTest");
        final PluginDescriptionFile mockedDescription = mock(PluginDescriptionFile.class);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);
        when(mockedDescription.getVersion()).thenReturn("1.0");
        when(mockedPlugin.getDescription()).thenReturn(mockedDescription);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_NOVERSION, updateResult);
    }
}