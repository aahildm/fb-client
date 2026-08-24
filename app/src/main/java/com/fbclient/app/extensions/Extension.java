package com.fbclient.app.extensions;

/** Represents an installed browser extension (WebExtension) */
public class Extension {
    public String id;
    public String name;
    public String version;
    public String description;
    public String iconUrl;
    public boolean enabled;
    public String sourceUrl; // URL or local path to the .xpi

    public Extension() {}

    public Extension(String id, String name, String version, String description,
                     String iconUrl, boolean enabled, String sourceUrl) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.iconUrl = iconUrl;
        this.enabled = enabled;
        this.sourceUrl = sourceUrl;
    }
}
