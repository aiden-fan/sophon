package com.sophon.cli;

import java.util.ArrayList;
import java.util.List;

/** 解析 {@link com.sophon.Sophon#main} 参数。 */
public final class LaunchArgs {

    private final boolean smoke;
    private final boolean web;
    private final boolean batchTests;
    private final String systemPromptFromCli;
    private final String[] remaining;

    private LaunchArgs(boolean smoke, boolean web, boolean batchTests, String systemPromptFromCli, String[] remaining) {
        this.smoke = smoke;
        this.web = web;
        this.batchTests = batchTests;
        this.systemPromptFromCli = systemPromptFromCli;
        this.remaining = remaining;
    }

    public boolean isSmoke() {
        return smoke;
    }

    public boolean isWeb() {
        return web;
    }

    public boolean isBatchTests() {
        return batchTests;
    }

    /** 启动时 {@code --system-prompt} / {@code -s}，已 trim；未指定则为 {@code null}。 */
    public String getSystemPromptFromCli() {
        return systemPromptFromCli;
    }

    public String[] getRemaining() {
        return remaining;
    }

    public static LaunchArgs parse(String[] argv) {
        boolean smoke = false;
        boolean web = false;
        boolean batchTests = false;
        String systemPrompt = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if ("--smoke".equals(a)) {
                smoke = true;
            } else if ("--web".equals(a)) {
                web = true;
            } else if ("--batch-tests".equals(a)) {
                batchTests = true;
            } else if ("--system-prompt".equals(a) || "-s".equals(a)) {
                if (i + 1 >= argv.length) {
                    throw new IllegalArgumentException("选项 " + a + " 需要紧跟一段提示词文本");
                }
                systemPrompt = argv[++i].trim();
            } else if (a.startsWith("--system-prompt=")) {
                systemPrompt = a.substring("--system-prompt=".length()).trim();
            } else {
                rest.add(a);
            }
        }
        return new LaunchArgs(smoke, web, batchTests, systemPrompt, rest.toArray(new String[0]));
    }
}
