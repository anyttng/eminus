package com.eminus.compat.iris.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.eminus.Eminus;
import com.eminus.compat.iris.PackContract;

import it.unimi.dsi.fastutil.ints.IntSet;

import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;

public final class MovedBlockFile {
    public static IntSet read(Path root, ShaderPackOptions options, Iterable<StringPair> defines) {
        String source;
        try {
            source = Files.readString(root.resolve(PackContract.MOVED_FILE), StandardCharsets.ISO_8859_1);
        } catch (NoSuchFileException absent) {
            return IntSet.of();
        } catch (IOException unreadable) {
            Eminus.LOGGER.error("Shader pack's {} could not be read: {}", PackContract.MOVED_FILE,
                    unreadable.toString());
            return IntSet.of();
        }

        IntSet moved = MovedBlocks.declared(PropertiesPreprocessor.preprocessSource(source, options, defines));
        Eminus.LOGGER.info("Shader pack declares moved blocks {} in {}", moved, PackContract.MOVED_FILE);
        return moved;
    }

    private MovedBlockFile() {
    }
}
