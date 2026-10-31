package com.eminus.compat.iris.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.eminus.Eminus;
import com.eminus.compat.iris.PackContract;

import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;

public final class ContractFile {
    public static ContractDeclarations read(Path root, ShaderPackOptions options, Iterable<StringPair> defines) {
        String source;
        try {
            source = Files.readString(root.resolve(PackContract.PROPERTIES_FILE), StandardCharsets.ISO_8859_1);
        } catch (NoSuchFileException absent) {
            return ContractDeclarations.NONE;
        } catch (IOException unreadable) {
            Eminus.LOGGER.error("Shader pack's {} could not be read: {}", PackContract.PROPERTIES_FILE,
                    unreadable.toString());
            return ContractDeclarations.NONE;
        }

        ContractDeclarations declared =
                ContractDeclarations.of(PropertiesPreprocessor.preprocessSource(source, options, defines));
        Eminus.LOGGER.info("Shader pack declares moved blocks {} in {}", declared.moved(),
                PackContract.PROPERTIES_FILE);
        if (declared.packCuts()) {
            Eminus.LOGGER.info("Shader pack cuts its own LOD fragments in {}", PackContract.PROPERTIES_FILE);
        }
        return declared;
    }

    private ContractFile() {
    }
}
