package net.gravitydevelopment.updater;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.logging.Logger;

import org.bukkit.Server;
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
 * Test cases of a successful update check.
 *
 * @author timbru31
 */
@ExtendWith(TemporaryFolderExtension.class)
@ExtendWith(HoverflyExtension.class)
@SuppressWarnings("checkstyle:MissingCtor")
class UpdaterSucessTest {
    private final Gson gson = new Gson();
    private static final Release[] CURSEFORGE_EXAMPLE_RELEASES = {
            Release.builder().name("SilkSpawners v1.0").releaseType("release").projectId(35890).build() };
    private static final String APPLICATION_JSON = "application/json";
    private static final String LOGGER_NAME = "UpdaterTest";
    private static final String PLUGIN_NAME = "ExamplePlugin";
    private static final String CURSEFORGE_API_PATH = "/servermods/files";
    private static final String CURSEFORGE_API_URL = "https://servermods.forgesvc.net";

    @Test
    @DisplayName("should not download the same version (NO_UPDATE)")
    public void shouldIgnoreTheSameVersion(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(gson.toJson(CURSEFORGE_EXAMPLE_RELEASES), APPLICATION_JSON))));

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

    @Test
    @DisplayName("should not download a lower version (NO_UPDATE)")
    public void shouldIgnoreLowerVersion(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(gson.toJson(CURSEFORGE_EXAMPLE_RELEASES), APPLICATION_JSON))));

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

    @Test
    @DisplayName("should not download the a wrong release type (FAIL_BADID)")
    public void shouldNotDownloadAWrongReleaseType(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        final Release[] alphaReleases = { Release.builder().name("SilkSpawners v1.0").releaseType("alpha").projectId(35890).build() };

        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(gson.toJson(alphaReleases), APPLICATION_JSON))));

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

    @Test
    @DisplayName("should download the latest release (UPDATE_AVAILABLE)")
    public void shouldDownloadTheLatestRelease(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
        final Release[] releases = { Release.builder().name("SilkSpawners v1.0").releaseType("release").projectId(35890).build(),
                Release.builder().name("SilkSpawners v2.0").releaseType("release").projectId(35890).build() };

        hoverfly.simulate(dsl(service(CURSEFORGE_API_URL).get(CURSEFORGE_API_PATH).anyQueryParams()
                .willReturn(success(gson.toJson(releases), APPLICATION_JSON))));

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

    @Test
    @DisplayName("should handle redirects (SUCCESS)")
    public void shouldDownloadHandleRedirects(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
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
                .anyQueryParams().willReturn(success("ddd", "application/java-archive"));

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

        assertEquals(UpdateResult.SUCCESS, updateResult);
    }

    @Test
    @DisplayName("should handle false MD5 sum (FAIL_DOWNLOAD)")
    public void shouldHandleFalseMD5Sum(final TemporaryFolder temporaryFolder, final Hoverfly hoverfly) {
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
                .anyQueryParams().willReturn(success("eee", "application/java-archive"));

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
