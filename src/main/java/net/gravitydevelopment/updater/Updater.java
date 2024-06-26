package net.gravitydevelopment.updater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.gravitydevelopment.updater.api.model.Release;

/**
 * Check for updates on BukkitDev for a given plugin, and download the updates if needed.
 * <p>
 * <b>VERY, VERY IMPORTANT</b>: Because there are no standards for adding auto-update toggles in your plugin's config, this system provides
 * NO CHECK WITH YOUR CONFIG to make sure the user has allowed auto-updating. <br>
 * It is a <b>BUKKIT POLICY</b> that you include a boolean value in your config that prevents the auto-updater from running <b>AT ALL</b>.
 * <br>
 * If you fail to include this option in your config, your plugin will be <b>REJECTED</b> when you attempt to submit it to dev.bukkit.org.
 * </p>
 * An example of a good configuration option would be something similar to 'auto-update: true' - if this value is set to false you may NOT
 * run the auto-updater. <br>
 * If you are unsure about these rules, please read the plugin submission guidelines: http://goo.gl/8iU5l
 *
 * @author Gravity
 * @author timbru31
 * @version 4.2.3
 */
@SuppressFBWarnings(value = "CD_CIRCULAR_DEPENDENCY", justification = "False positive")
public class Updater {
    /* Constants */

    // File ending
    private static final String ZIP_ENDING = ".zip";
    // HTTP Connection timeout
    private static final int CONNECTION_TIMEOUT = 15_000;
    // Path to GET
    private static final String QUERY = "/servermods/files?projectIds=";
    // Slugs will be appended to this to get to the project's RSS feed
    private static final String HOST = "https://servermods.forgesvc.net";
    // User-agent when querying Curse
    private static final String USER_AGENT = "Updater (by Gravity/timbru31)";
    // Used for locating version numbers in file names
    private static final String DELIMETER = "^v|[\\s_-]v";
    // If the version number contains one of these, don't update.
    private static final String[] NO_UPDATE_TAG = { "-DEV", "-PRE", "-SNAPSHOT" };
    // Used for downloading files
    private static final int BYTE_SIZE = 1024;
    // Config key for API key
    private static final String API_KEY_CONFIG_KEY = "api-key";
    // Config key for disabling Updater
    private static final String DISABLE_CONFIG_KEY = "disable";
    // Default API key value in config
    private static final String API_KEY_DEFAULT = "PUT_API_KEY_HERE";
    // Default disable value in config
    private static final Boolean DISABLE_DEFAULT = Boolean.FALSE;
    // Timeout for Threads in milliseconds
    private static final long THREAD_TIMEOUT = 10000L;

    /* User-provided variables */

    // Plugin running Updater
    private final Plugin plugin;
    // Type of update check to run
    private final UpdateType type;
    // Whether to announce file downloads
    private final boolean announce;
    // The plugin file (jar)
    private final File file;
    // The folder that downloads will be placed in
    private final File updateFolder;
    // The provided callback (if any)
    private final UpdateCallback callback;
    // Project's Curse ID
    private int id = -1;
    // BukkitDev ServerMods API key
    private String apiKey;
    private final ReleaseType releaseType;

    /* Collected from Curse API */

    private String versionName;
    private String versionLink;
    private String versionType;
    private String versionGameVersion;
    private String versionMD5;

    /* Update process variables */

    // Connection to RSS
    private URL url;
    // Updater thread
    private Thread thread;
    // Used for determining the outcome of the update process
    private Updater.UpdateResult result;

    /**
     * Gives the developer the result of the update process. Can be obtained by called {@link #getResult()}
     */
    public enum UpdateResult {
        /**
         * The updater found an update, and has readied it to be loaded the next time the server restarts/reloads.
         */
        SUCCESS,
        /**
         * The updater did not find an update, and nothing was downloaded.
         */
        NO_UPDATE,
        /**
         * The server administrator has disabled the updating system.
         */
        DISABLED,
        /**
         * The updater found an update, but was unable to download it.
         */
        FAIL_DOWNLOAD,
        /**
         * For some reason, the updater was unable to contact dev.bukkit.org to download the file.
         */
        FAIL_DBO,
        /**
         * When running the version check, the file on DBO did not contain a recognizable version.
         */
        FAIL_NOVERSION,
        /**
         * The id provided by the plugin running the updater was invalid and doesn't exist on DBO.
         */
        FAIL_BADID,
        /**
         * The server administrator has improperly configured their API key in the configuration.
         */
        FAIL_APIKEY,
        /**
         * The updater found an update, but because of the UpdateType being set to NO_DOWNLOAD, it wasn't downloaded.
         */
        UPDATE_AVAILABLE
    }

    /**
     * Allows the developer to specify the type of update that will be run.
     */
    public enum UpdateType {
        /**
         * Run a version check, and then if the file is out of date, download the newest version.
         */
        DEFAULT,
        /**
         * Don't run a version check, just find the latest update and download it.
         */
        NO_VERSION_CHECK,
        /**
         * Get information about the version and the download size, but don't actually download anything.
         */
        NO_DOWNLOAD
    }

    /**
     * Represents the various release types of a file on BukkitDev.
     */
    public enum ReleaseType {
        /**
         * An "alpha" file.
         */
        ALPHA,
        /**
         * A "beta" file.
         */
        BETA,
        /**
         * A "release" file.
         */
        RELEASE
    }

    /**
     * Initialize the updater.
     *
     * @param plugin The plugin that is checking for an update.
     * @param id The dev.bukkit.org id of the project.
     * @param file The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type Specify the type of update this will be. See {@link UpdateType}
     * @param announce True if the program should announce the progress of new updates in console.
     */
    public Updater(final Plugin plugin, final int id, final File file, final UpdateType type, final boolean announce) {
        this(plugin, id, file, type, null, announce);
    }

    /**
     * Initialize the updater with the provided callback.
     *
     * @param plugin The plugin that is checking for an update.
     * @param id The dev.bukkit.org id of the project.
     * @param file The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type Specify the type of update this will be. See {@link UpdateType}
     * @param callback The callback instance to notify when the Updater has finished
     */
    public Updater(final Plugin plugin, final int id, final File file, final UpdateType type, final UpdateCallback callback) {
        this(plugin, id, file, type, callback, false);
    }

    /**
     * Initialize the updater with the provided callback.
     *
     * @param plugin The plugin that is checking for an update.
     * @param id The dev.bukkit.org id of the project.
     * @param file The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type Specify the type of update this will be. See {@link UpdateType}
     * @param callback The callback instance to notify when the Updater has finished
     * @param announce True if the program should announce the progress of new updates in console.
     */
    public Updater(final Plugin plugin, final int id, final File file, final UpdateType type, final UpdateCallback callback,
            final boolean announce) {
        this(plugin, id, file, type, callback, announce, ReleaseType.RELEASE);

    }

    /**
     * Initialize the updater with the provided callback and Release Type.
     *
     * @param plugin The plugin that is checking for an update.
     * @param id The dev.bukkit.org id of the project.
     * @param file The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type Specify the type of update this will be. See {@link UpdateType}
     * @param callback The callback instance to notify when the Updater has finished
     * @param announce True if the program should announce the progress of new updates in console.
     * @param releaseType The desired release type to download (alpha, beta, release)
     */
    @SuppressFBWarnings("PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS")
    public Updater(final Plugin plugin, final int id, final File file, final UpdateType type, final UpdateCallback callback,
            final boolean announce, final ReleaseType releaseType) {
        this.plugin = plugin;
        this.type = type;
        this.announce = announce;
        this.file = file;
        this.id = id;
        this.updateFolder = this.plugin.getServer().getUpdateFolderFile();
        this.callback = callback;
        this.releaseType = releaseType;

        final File pluginFile = this.plugin.getDataFolder().getParentFile();
        final File updaterFile = new File(pluginFile, "Updater");
        final File updaterConfigFile = new File(updaterFile, "config.yml");

        final YamlConfiguration config = new YamlConfiguration();
        config.options().header(
                "This configuration file affects all plugins using the Updater system (version 2+ - http://forums.bukkit.org/threads/96681/ )"
                        + '\n' + "If you wish to use your API key, read http://wiki.bukkit.org/ServerMods_API and place it below." + '\n'
                        + "Some updating systems will not adhere to the disabled value, but these may be turned off in their plugin's configuration.");
        config.addDefault(DISABLE_CONFIG_KEY, DISABLE_DEFAULT);

        if (!updaterFile.exists()) {
            this.fileIOOrError(updaterFile, updaterFile.mkdir(), true);
        }

        final boolean createFile = !updaterConfigFile.exists();
        try {
            if (createFile) {
                this.fileIOOrError(updaterConfigFile, updaterConfigFile.createNewFile(), true);
                config.options().copyDefaults(true);
                config.save(updaterConfigFile);
            } else {
                config.load(updaterConfigFile);
            }
        } catch (final IOException | InvalidConfigurationException e) {
            final String message;
            if (createFile) {
                message = "The updater could not create configuration at " + updaterFile.getAbsolutePath();
            } else {
                message = "The updater could not load configuration at " + updaterFile.getAbsolutePath();
            }
            this.plugin.getLogger().log(Level.SEVERE, message.replaceAll("[\r\n]", ""), e);
        }

        if (config.getBoolean(DISABLE_CONFIG_KEY)) {
            this.result = UpdateResult.DISABLED;
            return;
        }

        String key = config.getString(API_KEY_CONFIG_KEY);
        if (API_KEY_DEFAULT.equalsIgnoreCase(key) || "".equals(key)) {
            key = null;
        }

        this.apiKey = key;

        try {
            this.url = new URL(Updater.HOST + Updater.QUERY + this.id);
        } catch (final MalformedURLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "The project ID provided for updating, " + this.id + " is invalid.", e);
            this.result = UpdateResult.FAIL_BADID;
            return;
        }

        this.thread = new Thread(() -> runUpdater());
        this.thread.start();
    }

    /**
     * Get the result of the update process.
     *
     * @return result of the update process.
     * @see UpdateResult
     */
    @Nullable
    public Updater.UpdateResult getResult() {
        this.waitForThread();
        return this.result;
    }

    /**
     * Get the latest version's release type.
     *
     * @return latest version's release type.
     * @see ReleaseType
     */
    @Nullable
    public ReleaseType getLatestType() {
        this.waitForThread();
        if (this.versionType != null) {
            return getReleaseType(this.versionType);
        }
        return null;
    }

    /**
     * Get the latest version's game version (such as "CB 1.2.5-R1.0").
     *
     * @return latest version's game version.
     */

    @Nullable
    public String getLatestGameVersion() {
        this.waitForThread();
        return this.versionGameVersion;
    }

    /**
     * Get the latest version's name (such as "Project v1.0").
     *
     * @return latest version's name.
     */
    @Nullable
    public String getLatestName() {
        this.waitForThread();
        return this.versionName;
    }

    /**
     * Get the latest version's direct file link.
     *
     * @return latest version's file link.
     */
    @Nullable
    public String getLatestFileLink() {
        this.waitForThread();
        return this.versionLink;
    }

    /**
     * As the result of Updater output depends on the thread's completion, it is necessary to wait for the thread to finish before allowing
     * anyone to check the result.
     */
    private void waitForThread() {
        if (this.thread != null && this.thread.isAlive()) {
            try {
                this.thread.join(THREAD_TIMEOUT);
            } catch (final InterruptedException e) {
                this.plugin.getLogger().log(Level.SEVERE, null, e);
            }
        }
    }

    /**
     * Save an update from dev.bukkit.org into the server's update folder.
     *
     * @param fileToSave the name of the file to save it as.
     */
    private void saveFile(final String fileToSave) {
        final File folder = this.updateFolder;

        if (!folder.exists()) {	
            this.fileIOOrError(folder, folder.mkdir(), true);	
        } else {	
            deleteOldFiles();	
        }

        final boolean downloadSuccess = downloadFile();
        if (downloadSuccess) {
            this.result = UpdateResult.SUCCESS;

            final File dFile = new File(folder.getAbsolutePath(), fileToSave);
            if (dFile.getName().endsWith(ZIP_ENDING)) {
                this.unzip(dFile.getAbsolutePath());
            }
            if (this.announce) {
                this.plugin.getLogger().info("Finished updating.");
            }
        }
    }

    /**
     * Download a file and save it to the specified folder.
     */
    @SuppressFBWarnings(value = { "WEAK_MESSAGE_DIGEST_MD5",
            "UAC_UNNECESSARY_API_CONVERSION_FILE_TO_PATH" }, justification = "CurseForge does not offer a more secure hashing algorithm to compare to and false positive")
    private boolean downloadFile() {
        URL fileUrl = null;
        int fileLength = 0;
        try {
            fileUrl = followRedirects(this.versionLink);
            if (fileUrl == null) {
                return false;
            }
            fileLength = fileUrl.openConnection().getContentLength();
        } catch (final IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, null, e);
            this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
            return false;
        }

        final File updateFile = new File(this.updateFolder, this.file.getName());
        try (BufferedInputStream in = new BufferedInputStream(fileUrl.openStream());
                OutputStream fout = Files.newOutputStream(updateFile.toPath())) {

            final byte[] data = new byte[Updater.BYTE_SIZE];
            int count;
            if (this.announce) {
                this.plugin.getLogger().info("About to download a new update: " + this.versionName.replaceAll("[\r\n]", ""));
            }
            long downloaded = 0;
            final MessageDigest md = MessageDigest.getInstance("MD5");
            while ((count = in.read(data, 0, Updater.BYTE_SIZE)) != -1) {
                downloaded += count;
                fout.write(data, 0, count);
                md.update(data, 0, count);
                final int percent = (int) ((downloaded * 100) / fileLength);
                if (this.announce && percent % 10 == 0) {
                    this.plugin.getLogger().info("Downloading update: " + percent + "% of " + fileLength + " bytes.");
                }
            }
            final byte[] md5bytes = md.digest();
            final StringBuilder stringBuilder = new StringBuilder();
            for (final byte md5byte : md5bytes) {
                stringBuilder.append(Integer.toString((md5byte & 0xff) + 0x100, 16).substring(1));
            }
            final String md5 = stringBuilder.toString();
            if (!md5.equals(this.versionMD5)) {
                this.plugin.getLogger().warning("Downloaded file did not match the remote file!");
                this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
                this.fileIOOrError(updateFile, updateFile.delete(), false);
                return false;
            }
        } catch (final Exception ex) {
            this.plugin.getLogger().log(Level.WARNING, "The auto-updater tried to download a new update, but was unsuccessful.", ex);
            this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
            return false;
        }
        return true;
    }

    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop" })
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private URL followRedirects(final String location) throws IOException {
        final int MAX_ITERATIONS = 5;
        String redirectedLocation = location;
        int iterations = 0;
        URL fileURL = null;

        while (iterations < MAX_ITERATIONS) {
            final URL resourceUrl = new URL(redirectedLocation);
            final HttpURLConnection conn = (HttpURLConnection) resourceUrl.openConnection();
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(CONNECTION_TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", Updater.USER_AGENT);
            fileURL = conn.getURL();
            switch (conn.getResponseCode()) {
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_MOVED_TEMP:
                    final String redLoc = conn.getHeaderField("Location");
                    final URL base = new URL(redirectedLocation);
                    // Deal with relative URLs
                    final URL next = new URL(base, redLoc);
                    redirectedLocation = next.toExternalForm();
                    iterations++;
                    continue;
                default:
                    break;
            }
            break;
        }
        if (iterations == MAX_ITERATIONS) {
            this.plugin.getLogger().log(Level.SEVERE,
                    "The auto-updater tried to download a new update, but was unsuccessful because there were too many redirects");
            this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
            return null;
        }
        return fileURL;
    }

    /**
     * Remove possibly leftover files from the update folder.
     */
    private void deleteOldFiles() {
        // Just a quick check to make sure we didn't leave any files from last time...
        final File[] list = listFilesOrError(this.updateFolder);
        for (final File xFile : list) {
            if (xFile.getName().endsWith(ZIP_ENDING)) {
                this.fileIOOrError(xFile, xFile.mkdir(), true);
            }
        }
    }

    /**
     * Part of Zip-File-Extractor, modified by Gravity for use with Updater.
     *
     * @param location the location of the file to extract.
     */
    @SuppressFBWarnings(value = { "UAC_UNNECESSARY_API_CONVERSION_FILE_TO_PATH",
            "PRMC_POSSIBLY_REDUNDANT_METHOD_CALLS" }, justification = "False positive")
    private void unzip(final String location) {
        final File fSourceZip = new File(location);
        final String zipPath = location.substring(0, location.length() - 4);
        try (ZipFile zipFile = new ZipFile(fSourceZip)) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final File destinationFilePath = new File(zipPath, entry.getName());
                this.fileIOOrError(destinationFilePath.getParentFile(), destinationFilePath.getParentFile().mkdirs(), true);
                if (!entry.isDirectory()) {
                    try (BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                            OutputStream fos = Files.newOutputStream(destinationFilePath.toPath());
                            BufferedOutputStream bos = new BufferedOutputStream(fos, Updater.BYTE_SIZE)) {
                        int b;
                        final byte[] buffer = new byte[Updater.BYTE_SIZE];

                        while ((b = bis.read(buffer, 0, Updater.BYTE_SIZE)) != -1) {
                            bos.write(buffer, 0, b);
                        }
                        bos.flush();
                        final String name = destinationFilePath.getName();
                        if (name.endsWith(".jar") && this.pluginExists(name)) {
                            final File output = new File(this.updateFolder, name);
                            this.fileIOOrError(output, destinationFilePath.renameTo(output), true);
                        }
                    } catch (final IOException innerEx) {
                        throw innerEx;
                    }
                }
            }

            // Move any plugin data folders that were included to the right place, Bukkit won't do this for us.
            moveNewZipFiles(zipPath);

        } catch (final IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "The auto-updater tried to unzip a new update file, but was unsuccessful.", e);
            this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
        } finally {
            this.fileIOOrError(fSourceZip, fSourceZip.delete(), false);
        }
    }

    /**
     * Find any new files extracted from an update into the plugin's data directory.
     *
     * @param zipPath path of extracted files.
     */
    private void moveNewZipFiles(final String zipPath) {
        final File[] list = listFilesOrError(new File(zipPath));
        for (final File dFile : list) {
            if (dFile.isDirectory() && this.pluginExists(dFile.getName())) {
                // Current dir
                final File oFile = new File(this.plugin.getDataFolder().getParent(), dFile.getName());
                // List of existing files in the new dir
                final File[] dList = listFilesOrError(dFile);
                // List of existing files in the current dir
                final File[] oList = listFilesOrError(oFile);
                for (final File cFile : dList) {
                    // Loop through all the files in the new dir
                    boolean found = false;
                    for (final File xFile : oList) {
                        // Loop through all the contents in the current dir to see if it exists
                        if (xFile.getName().equals(cFile.getName())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // Move the new file into the current dir
                        final File output = new File(oFile, cFile.getName());
                        this.fileIOOrError(output, cFile.renameTo(output), true);
                    } else {
                        // This file already exists, so we don't need it anymore.
                        this.fileIOOrError(cFile, cFile.delete(), false);
                    }
                }
            }
            this.fileIOOrError(dFile, dFile.delete(), false);
        }
        final File zip = new File(zipPath);
        this.fileIOOrError(zip, zip.delete(), false);
    }

    /**
     * Check if the name of a jar is one of the plugins currently installed, used for extracting the correct files out of a zip.
     *
     * @param name a name to check for inside the plugins folder.
     * @return true if a file inside the plugins folder is named this.
     */
    private boolean pluginExists(final String name) {
        final File[] plugins = listFilesOrError(new File("plugins"));
        for (final File pluginFile : plugins) {
            if (pluginFile.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check to see if the program should continue by evaluating whether the plugin is already updated, or shouldn't be updated.
     *
     * @return true if the version was located and is not the same as the remote's newest.
     */
    @SuppressFBWarnings("STT_STRING_PARSING_A_FIELD")
    private boolean versionCheck() {
        final String title = this.versionName;
        if (this.type != UpdateType.NO_VERSION_CHECK) {
            final String localVersion = this.plugin.getDescription().getVersion();
            if (title != null && title.split(DELIMETER).length >= 2) {
                // Get the newest file's version number
                final String remoteVersion = title.split(DELIMETER)[title.split(DELIMETER).length - 1].split(" ")[0];

                if (this.hasTag(localVersion) || !this.shouldUpdate(localVersion, remoteVersion)) {
                    // We already have the latest version, or this build is tagged for no-update
                    this.result = Updater.UpdateResult.NO_UPDATE;
                    return false;
                }
            } else {
                // The file's name did not contain the string 'vVersion'
                final String authorInfo = this.plugin.getDescription().getAuthors().isEmpty() ? ""
                        : " (" + this.plugin.getDescription().getAuthors().get(0) + ")";
                this.plugin.getLogger().warning(
                        "The author of this plugin" + authorInfo.replaceAll("[\r\n]", "") + " has misconfigured their Auto Update system");
                this.plugin.getLogger().warning("File versions should follow the format 'PluginName vVERSION'");
                this.plugin.getLogger().warning("Please notify the author of this error.");
                this.result = Updater.UpdateResult.FAIL_NOVERSION;
                return false;
            }
        }
        return true;
    }

    /**
     * <b>If you wish to run mathematical versioning checks, edit this method.</b>
     * <p>
     * With default behavior, Updater will NOT verify that a remote version available on BukkitDev which is not this version is indeed an
     * "update". If a version is present on BukkitDev that is not the version that is currently running, Updater will assume that it is a
     * newer version. This is because there is no standard versioning scheme, and creating a calculation that can determine whether a new
     * update is actually an update is sometimes extremely complicated.
     * </p>
     * <p>
     * Updater will call this method from {@link #versionCheck()} before deciding whether the remote version is actually an update. If you
     * have a specific versioning scheme with which a mathematical determination can be reliably made to decide whether one version is
     * higher than another, you may revise this method, using the local and remote version parameters, to execute the appropriate check.
     * </p>
     * <p>
     * Returning a value of <b>false</b> will tell the update process that this is NOT a new version. Without revision, this method will
     * always consider a remote version at all different from that of the local version a new update.
     * </p>
     *
     * @param localVersion the current version
     * @param remoteVersion the remote version
     * @return true if Updater should consider the remote version an update, false if not.
     */
    @SuppressWarnings("static-method")
    public boolean shouldUpdate(final String localVersion, final String remoteVersion) {
        try {
            final Semver local = new Semver(localVersion, Semver.SemverType.LOOSE);
            final Semver remote = new Semver(remoteVersion, Semver.SemverType.LOOSE);
            return remote.isGreaterThan(local);
        } catch (@SuppressWarnings("unused") final SemverException e) {
            return !localVersion.equalsIgnoreCase(remoteVersion);
        }
    }

    /**
     * Evaluate whether the version number is marked showing that it should not be updated by this program.
     *
     * @param version a version number to check for tags in.
     * @return true if updating should be disabled.
     */
    @SuppressWarnings("static-method")
    private boolean hasTag(final String version) {
        for (final String string : Updater.NO_UPDATE_TAG) {
            if (version.contains(string)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Make a connection to the BukkitDev API and request the newest file's details.
     *
     * @return true if successful.
     */
    @SuppressFBWarnings(value = "UTWR_USE_TRY_WITH_RESOURCES", justification = "False positive")
    private boolean read() {
        BufferedReader reader = null;
        try {
            final URLConnection conn = this.url.openConnection();
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(CONNECTION_TIMEOUT);

            if (this.apiKey != null) {
                conn.addRequestProperty("X-API-Key", this.apiKey);
            }

            conn.addRequestProperty("User-Agent", Updater.USER_AGENT);

            conn.setDoOutput(true);

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            final String response = reader.readLine();

            final Gson gson = new GsonBuilder().create();
            final Release[] releases = gson.fromJson(response, Release[].class);
            final ArrayList<Release> filteredReleases = new ArrayList<>();

            for (final Release release : releases) {
                final String _releaseType = release.getReleaseType();
                if (getReleaseType(_releaseType) == this.releaseType) {
                    filteredReleases.add(release);
                }
            }

            if (filteredReleases.isEmpty()) {
                this.plugin.getLogger().warning("The updater could not find any files for the project id " + this.id);
                this.result = UpdateResult.FAIL_BADID;
                return false;
            }

            final Release latestRelease = filteredReleases.get(filteredReleases.size() - 1);
            this.versionName = latestRelease.getName();
            this.versionLink = latestRelease.getDownloadUrl();
            this.versionType = latestRelease.getReleaseType();
            this.versionGameVersion = latestRelease.getGameVersion();
            this.versionMD5 = latestRelease.getMd5();

            return true;
        } catch (final IOException e) {
            if (e.getMessage().contains("HTTP response code: 403")) {
                this.plugin.getLogger().severe("dev.bukkit.org rejected the API key provided in plugins/Updater/config.yml");
                this.plugin.getLogger().severe("Please double-check your configuration to ensure it is correct.");
                this.result = UpdateResult.FAIL_APIKEY;
            } else {
                this.plugin.getLogger().severe("The updater could not contact dev.bukkit.org for updating.");
                this.plugin.getLogger().severe(
                        "If you have not recently modified your configuration and this is the first time you are seeing this message, the site may be experiencing temporary downtime.");
                this.result = UpdateResult.FAIL_DBO;
            }
            this.plugin.getLogger().log(Level.SEVERE, null, e);
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    this.plugin.getLogger().log(Level.SEVERE, null, e);
                }
            }
        }
    }

    @SuppressWarnings("static-method")
    @Nullable
    private ReleaseType getReleaseType(final String release) {
        for (final ReleaseType releaseTypeValue : ReleaseType.values()) {
            if (releaseTypeValue.name().equalsIgnoreCase(release)) {
                return releaseTypeValue;
            }
        }
        return null;
    }

    /**
     * Perform a file operation and log any errors if it fails.
     *
     * @param fileOperatedOn file operation is performed on.
     * @param operationResult result of file operation.
     * @param create true if a file is being created, false if deleted.
     */
    private void fileIOOrError(final File fileOperatedOn, final boolean operationResult, final boolean create) {
        if (!operationResult) {
            this.plugin.getLogger().severe("The updater could not " + (create ? "create" : "delete") + " file at: "
                    + fileOperatedOn.getAbsolutePath().replaceAll("[\r\n]", ""));
        }
    }

    @SuppressFBWarnings("BL_BURYING_LOGIC")
    private File[] listFilesOrError(final File folder) {
        final File[] contents = folder.listFiles();
        if (contents == null) {
            this.plugin.getLogger()
                    .severe("The updater could not access files at: " + this.updateFolder.getAbsolutePath().replaceAll("[\r\n]", ""));
            return new File[0];
        }
        return contents;
    }

    /**
     * Called on main thread when the Updater has finished working, regardless of result.
     */
    public interface UpdateCallback {
        /**
         * Called when the updater has finished working.
         *
         * @param updater The updater instance
         */
        void onFinish(Updater updater);
    }

    void runCallback() {
        this.callback.onFinish(this);
    }

    @SuppressFBWarnings(value = "STT_STRING_PARSING_A_FIELD")
    final void runUpdater() {
        if (this.url != null && this.read() && this.versionCheck()) {
            // Obtain the results of the project's file feed
            if (this.versionLink != null && this.type != UpdateType.NO_DOWNLOAD) {
                String name = this.file.getName();
                // If it's a zip file, it shouldn't be downloaded as the plugin's name
                if (this.versionLink.endsWith(ZIP_ENDING)) {
                    name = this.versionLink.substring(this.versionLink.lastIndexOf('/') + 1);
                }
                this.saveFile(name);
            } else {
                this.result = UpdateResult.UPDATE_AVAILABLE;
            }
        }

        if (this.callback != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runCallback();
                }
            }.runTask(this.plugin);
        }
    }
}
