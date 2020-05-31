package net.gravitydevelopment.updater.api.model;

import lombok.Data;

/**
 * Representation of CurseForge's API for a release entry of a server mod.
 *
 * @author timbru31
 */
@Data
@SuppressWarnings("checkstyle:MissingCtor")
public class Release {
    // Remote file's title
    private String name;
    // Remote file's download link
    private String downloadUrl;
    // Remote file's release type
    private String releaseType;
    // Remote file's build version
    private String gameVersion;
    // Remote file's md5 sum
    private String md5;
}
