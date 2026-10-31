package com.eminus.compat.iris.contract;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.regex.Pattern;

import com.eminus.compat.iris.PackContract;

import it.unimi.dsi.fastutil.ints.IntSet;

public record ContractDeclarations(IntSet moved, boolean packCuts) {
    public static final ContractDeclarations NONE = new ContractDeclarations(IntSet.of(), false);

    private static final Pattern CONTINUED_OVER_BLANK = Pattern.compile("\\\\\\n\\s*\\n");
    private static final String JOINED = " ";

    public static ContractDeclarations of(String preprocessed) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(CONTINUED_OVER_BLANK.matcher(preprocessed).replaceAll(JOINED)));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }

        return new ContractDeclarations(MovedBlocks.parse(properties.getProperty(PackContract.MOVED_KEY)),
                DeclaredCutout.packCuts(properties.getProperty(PackContract.CUTOUT_KEY)));
    }
}
