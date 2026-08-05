package com.configuration;

import java.util.Arrays;
import java.util.List;

public enum VarEnvironments {

    LOCAL("local", ""),
    DEV("dev"),
    TEST("test", "pp", "preprod"),
    PROD("prod", "production");

    private final List<String> aliases;

    VarEnvironments(String... aliases) {
        this.aliases = Arrays.asList(aliases);
    }

    public boolean containsAlias(String alias) {
        return aliases.contains(alias.toLowerCase());
    }

    public static VarEnvironments parse(String alias) {
        if (LOCAL.containsAlias(alias)) return LOCAL;
        if (DEV.containsAlias(alias)) return DEV;
        if (TEST.containsAlias(alias)) return TEST;
        if (PROD.containsAlias(alias)) return PROD;
        throw new IllegalArgumentException("Unknown var environment: " + alias);
    }
}

