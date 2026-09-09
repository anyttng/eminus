package com.eminus.session;

import java.nio.file.Path;

public final class StoreFolders {
    public static final String ROOT_FOLDER = "eminus";
    public static final String SERVERS_FOLDER = "servers";
    public static final String UNNAMED_SERVER_FOLDER = "server";

    private static final String DISALLOWED_IN_FOLDER_NAME = "[^a-zA-Z0-9._-]";
    private static final String REPLACEMENT = "_";

    public static Path singleplayerBase(Path saveFolder) {
        return saveFolder.normalize().resolve(ROOT_FOLDER);
    }

    public static Path multiplayerBase(Path gameFolder, String serverAddress) {
        return gameFolder.normalize().resolve(ROOT_FOLDER).resolve(SERVERS_FOLDER)
                .resolve(serverFolderName(serverAddress));
    }

    public static Path dimensionFolder(Path base, WorldIdentity identity) {
        return base.resolve(identity.folderName());
    }

    public static String serverFolderName(String serverAddress) {
        String name = serverAddress.replaceAll(DISALLOWED_IN_FOLDER_NAME, REPLACEMENT);
        return name.isEmpty() ? UNNAMED_SERVER_FOLDER : name;
    }

    private StoreFolders() {
    }
}
