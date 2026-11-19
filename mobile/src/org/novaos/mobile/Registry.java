package org.novaos.mobile;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Where the NovaOS software update comes from: a public package in GitHub's container
 * registry (ghcr.io), published by the "Build NovaOS Mobile app" workflow. The package
 * holds two files, update.properties and novaos-mobile.apk (an OCI artifact); anyone
 * may read it without a login once the package is public - the source code stays private.
 * Android-independent, tested.
 */
public final class Registry {
    public static final String MANIFEST_TYPE = "application/vnd.oci.image.manifest.v1+json";
    public static final String TITLE = "org.opencontainers.image.title";
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern HOST = Pattern.compile("[a-z0-9.-]+(:[0-9]{1,5})?");
    private static final Pattern NAME = Pattern.compile("[a-z0-9._-]+(/[a-z0-9._-]+)+");

    public final String host, name;

    /** host e.g. "ghcr.io", name e.g. "superali99-tech/novaos-mobile". */
    public Registry(String host, String name) {
        if (host == null || !HOST.matcher(host).matches()) throw new IllegalArgumentException("host " + host);
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("name " + name);
        this.host = host;
        this.name = name;
    }

    /** Anonymous read token (works only for public packages). */
    public String tokenUrl() {
        return "https://" + host + "/token?scope=repository:" + name + ":pull&service=" + host.replaceAll(":.*", "");
    }

    public String manifestUrl(String tag) {
        return "https://" + host + "/v2/" + name + "/manifests/" + tag;
    }

    public String blobUrl(String digest) {
        return "https://" + host + "/v2/" + name + "/blobs/" + digest;
    }

    /** The token of a token answer, or null. */
    public static String token(String json) {
        try {
            Object t = MiniJson.get(MiniJson.parse(json), "token");
            if (!(t instanceof String) || ((String) t).isEmpty()) {
                t = MiniJson.get(MiniJson.parse(json), "access_token");
            }
            return t instanceof String && !((String) t).isEmpty() ? (String) t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Digest ("sha256:...") of the file with this name in the artifact manifest, or null. */
    public static String digest(String manifestJson, String title) {
        Object layers;
        try {
            layers = MiniJson.get(MiniJson.parse(manifestJson), "layers");
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(layers instanceof List)) return null;
        for (Object l : (List<?>) layers) {
            if (!(l instanceof Map)) continue;
            Object t = MiniJson.get(l, "annotations", TITLE);
            Object d = ((Map<?, ?>) l).get("digest");
            if (title.equals(t) && d instanceof String && DIGEST.matcher((String) d).matches()) return (String) d;
        }
        return null;
    }

    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * Second channel: a file of the GitHub release with this tag (readable without a login
     * when the repository is public), e.g. .../releases/download/mobile-update/update.properties
     */
    public static String releaseAsset(String repo, String tag, String file) {
        if (repo == null || !REPO.matcher(repo).matches() || tag == null || !FILE.matcher(tag).matches()
                || file == null || !FILE.matcher(file).matches() || file.startsWith(".") || tag.startsWith(".")) {
            throw new IllegalArgumentException("release " + repo + " " + tag + " " + file);
        }
        return "https://github.com/" + repo + "/releases/download/" + tag + "/" + file;
    }

    /** "sha256:abc..." -> "abc..." */
    public static String hex(String digest) {
        return digest.substring("sha256:".length());
    }
}
