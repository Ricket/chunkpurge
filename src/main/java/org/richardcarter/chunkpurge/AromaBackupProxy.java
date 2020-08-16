package org.richardcarter.chunkpurge;

import java.lang.reflect.Method;

public class AromaBackupProxy {
    private static boolean failed = false;

    public static boolean isBackupRunning() {
        if (failed) {
            return false;
        }

        try {
            Class<?> classThreadBackup = AromaBackupProxy.class.getClassLoader().loadClass("aroma1997.backup.mc.ThreadBackup");
            Method methodIsBackupRunning = classThreadBackup.getDeclaredMethod("isBackupRunning");
            return (Boolean) methodIsBackupRunning.invoke(null);
        } catch (Exception e) {
            failed = true;
            ChunkPurgeMod.log.error("Could not get AromaBackup running status, will ignore it. This may result in logspam while backups are running.", e);
        }

        return false;
    }
}
