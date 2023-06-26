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
import java.net.HttpURLConnection;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.Gson;

import io.github.glytching.junit.extension.folder.TemporaryFolder;
import io.github.glytching.junit.extension.folder.TemporaryFolderExtension;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.dsl.ResponseBuilder;
import io.specto.hoverfly.junit.dsl.StubServiceBuilder;
import io.specto.hoverfly.junit5.HoverflyExtension;
import net.gravitydevelopment.updater.Updater.UpdateResult;
import net.gravitydevelopment.updater.Updater.UpdateType;
import net.gravitydevelopment.updater.api.model.Release;

/**
 * Test cases to test various error cases (API down, project ID invalid) the updater can encounter.
 *
 * @author timbru31
 */
@ExtendWith(TemporaryFolderExtension.class)
@ExtendWith(HoverflyExtension.class)
@SuppressWarnings("checkstyle:MissingCtor")
class UpdaterFailTest {
    private final Gson gson = new Gson();
    private static final String APPLICATION_JSON = "application/json";
    private static final String LOGGER_NAME = "UpdaterTest";
    private static final String PLUGIN_NAME = "ExamplePlugin";
    private static final String CURSEFORGE_API_PATH = "/servermods/files";
    private static final String CURSEFORGE_API_URL = "https://servermods.forgesvc.net";

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should return FAIL_DBO when the server responds with a 5XX error")
    public void shouldReturnDBOFailOnServerError(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(serverError())));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_DBO, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should return FAIL_DBO when client sends an invalid request")
    public void shouldReturnDBOFailWithInavlidRequest(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(badRequest())));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, -1, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_DBO, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should return FAIL_APIKEY when the client sends an invalid API token")
    public void shouldReturn403WithInvalidApiKey(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) throws IOException {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(forbidden())));

        final File updaterFile = temporaryFolder.createDirectory("Updater");
        final File updaterConfigFile = new File(updaterFile, "config.yml");

        final YamlConfiguration config = new YamlConfiguration();
        config.addDefault("api-key", "123");
        config.options().copyDefaults(true);
        config.save(updaterConfigFile);
        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_APIKEY, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should return FAIL_BADID when the client sends an invalid project id")
    public void shouldReturnBadIdWhenProjectDoesNotExist(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(
                dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(success("[]", APPLICATION_JSON))));

        final File pluginFolder = temporaryFolder.createDirectory(PLUGIN_NAME);

        final Plugin mockedPlugin = mock(Plugin.class);
        final Server mockedServer = mock(Server.class);
        final Logger mockedLogger = Logger.getLogger(LOGGER_NAME);
        when(mockedPlugin.getLogger()).thenReturn(mockedLogger);
        when(mockedPlugin.getServer()).thenReturn(mockedServer);
        when(mockedPlugin.getDataFolder()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, null, UpdateType.NO_DOWNLOAD, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_BADID, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should return FAIL_NOVERSION when the name is invalid")
    public void shouldReturnFailNoVersionWhenTheNameIsInvalid(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success("[{\"name\":\"SilkSpawners\",\"projectId\":35890,\"releaseType\":\"release\"}]", APPLICATION_JSON))));

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

        assertEquals(UpdateResult.FAIL_NOVERSION, updateResult);
    }

    @SuppressWarnings("static-method")
    @Test
    @DisplayName("should return FAIL_BADID when the server responds with an invalid API object")
    public void shouldReturnDBOFailOnServerInvalidAPI(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams().willReturn(success(
                "[{\"dateReleased\": \"/Date(-62135596800000+0000)/\",\"downloadUrl\": null,\"fileName\": \"TreeCapitator-1.0.jar\",\"fileUrl\": null,\"gameVersion\": \"1.12\",\"md5\": null,\"name\": null,\"projectId\": 294976,\"releaseType\": \"release\"}]",
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

        assertEquals(UpdateResult.FAIL_NOVERSION, updateResult);
    }

    @Test
    @DisplayName("should abort redirect loop (FAIL_DOWNLOAD)")
    public void shouldAbortRedirectLoop(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        final String downloadUrl = "https://files.forgecdn.net/files/4559/190/SilkSpawners.jar";
        final Release[] releases = { Release.builder().name("SilkSpawners v1.0").releaseType("release").projectId(35890).build(),
                Release.builder().name("SilkSpawners v2.0").releaseType("release").downloadUrl(downloadUrl)
                        .md5("77963b7a931377ad4ab5ad6a9cd718aa").projectId(35890).build() };

        final StubServiceBuilder curseForgeApi = service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(gson.toJson(releases), APPLICATION_JSON));

        final StubServiceBuilder filesForgeCDN = service("https://files.forgecdn.net").get("/files/4559/190/SilkSpawners.jar")
                .anyQueryParams().willReturn(ResponseBuilder.response().status(HttpURLConnection.HTTP_MOVED_PERM).header("Location",
                        "https://mediafilez.forgecdn.net/files/4559/190/SilkSpawners.jar"));

        final StubServiceBuilder mediaFilesForgeCDN = service("https://mediafilez.forgecdn.net").get("/files/4559/190/SilkSpawners.jar")
                .anyQueryParams().willReturn(ResponseBuilder.response().status(HttpURLConnection.HTTP_MOVED_PERM).header("Location",
                        "https://files.forgecdn.net/files/4559/190/SilkSpawners.jar"));

        hoverfly.simulate(dsl(curseForgeApi, filesForgeCDN, mediaFilesForgeCDN));

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
        when(mockedServer.getUpdateFolderFile()).thenReturn(pluginFolder);

        final Updater updater = new Updater(mockedPlugin, 123, new File("temp"), UpdateType.DEFAULT, false);
        final UpdateResult updateResult = updater.getResult();

        assertEquals(UpdateResult.FAIL_DOWNLOAD, updateResult);
    }
}
