package com.tricrotism.mixin.impl.screen;

import com.tricrotism.SageFang;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.mixin.accessors.misc.ServerResourcePackManager$PackEntryAccessor;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Mixin(ServerPackManager.class)
public class ServerPackManagerMixin {

    @Inject(
        method = "onDownload",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/server/PackLoadFeedback;reportUpdate(Ljava/util/UUID;Lnet/minecraft/client/resources/server/PackLoadFeedback$Update;)V"
        )
    )
    public void onAdd(
        Collection<ServerPackManager.ServerPackData> collection,
        DownloadQueue.BatchResult batchResult,
        CallbackInfo ci
    ) {
        if (SageFangConfig.isDisablePackUnobf()) return;

        for (ServerPackManager.ServerPackData serverPackData : collection) {
            ServerResourcePackManager$PackEntryAccessor accessor = (ServerResourcePackManager$PackEntryAccessor) serverPackData;
            Path path = accessor.getPath();

            new Thread(() -> {
                try (ZipFile zipFile = new ZipFile(new File(path.toUri()))) {
                    File PATH = FabricLoader.getInstance().getGameDir().toFile();

                    File sageFangDir = new File(PATH, SageFang.MOD_ID);
                    File packsDir = new File(sageFangDir, "packs");

                    if (!packsDir.exists() && !packsDir.mkdirs()) {
                        log.error("Failed to create directory structure at {}", packsDir.getAbsolutePath());
                        return;
                    }

                    String originalName = path.getFileName().toString();
                    File outZip = new File(packsDir, originalName + ".out.zip");

                    int counter = 1;
                    while (outZip.exists()) {
                        String versionedName = path.getFileName().toString() + ".out.zip (" + counter + ")";
                        outZip = new File(packsDir, versionedName);
                        counter++;
                    }

                    try (FileOutputStream out = new FileOutputStream(outZip)) {
                        ZipOutputStream zipOut = new ZipOutputStream(out);
                        zipFile.entries().asIterator().forEachRemaining(entry -> {
                            try {
                                ZipEntry newEntry = new ZipEntry(entry.getName());
                                byte[] bytes = zipFile.getInputStream(newEntry).readAllBytes();
                                zipOut.putNextEntry(newEntry);
                                zipOut.write(bytes, 0, bytes.length);
                                zipOut.closeEntry();
                            } catch (IOException exception) {
                                log.error("Failed to process entry {} in pack", entry.getName(), exception);
                            }
                        });
                        log.info("Pack has been saved and deobfuscated. See {}", outZip.getAbsolutePath());

                    }
                } catch (IOException exception) {
                    log.error("Failed to save pack", exception);
                }
            }).start();
        }

    }

}
