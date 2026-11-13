package com.eminus.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class IrisAlphaTestRule {
    private static final Pattern VERSION = Pattern.compile("#\\s*version\\s+(\\d+)(?:[ \\t]+(\\w+))?");
    private static final Pattern FIRST_OUTPUT = Pattern.compile("\\bgl_FragColor\\b|\\bgl_FragData\\s*\\[\\s*0\\s*\\]");
    private static final String CORE_PROFILE = "core";
    private static final int FIRST_CORE_VERSION = 150;

    private IrisAlphaTestRule() {
    }

    static boolean applies(String fragmentSource) {
        Matcher version = VERSION.matcher(fragmentSource);
        if (!version.find()) {
            return false;
        }
        String profile = version.group(2);
        boolean patchedAsCompatibility = !CORE_PROFILE.equals(profile)
                && (Integer.parseInt(version.group(1)) < FIRST_CORE_VERSION || profile != null);
        return patchedAsCompatibility && FIRST_OUTPUT.matcher(fragmentSource).find();
    }
}
