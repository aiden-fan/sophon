package com.sophon.cli;

import java.util.ArrayList;
import java.util.List;

/** 解析 {@link com.sophon.Sophon#main} 参数。 */
public final class LaunchArgs {

    private final boolean smoke;
    /** 显式 {@code --web}，覆盖默认 CLI 启动。 */
    private final boolean web;
    /** 显式 {@code --cli}（可选，默认就是 CLI）。 */
    private final boolean cli;
    private final boolean batchTests;
    private final String systemPromptFromCli;
    /** 非空时 CLI 恢复该会话并打印历史，而非新建会话。 */
    private final String resumeSessionId;
    private final String[] remaining;

    private LaunchArgs(
            boolean smoke,
            boolean web,
            boolean cli,
            boolean batchTests,
            String systemPromptFromCli,
            String resumeSessionId,
            String[] remaining) {
        this.smoke = smoke;
        this.web = web;
        this.cli = cli;
        this.batchTests = batchTests;
        this.systemPromptFromCli = systemPromptFromCli;
        this.resumeSessionId = resumeSessionId;
        this.remaining = remaining;
    }

    public boolean isSmoke() {
        return smoke;
    }

    public boolean isWeb() {
        return web;
    }

    public boolean isCli() {
        return cli;
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

    /** 恢复已有会话 id；未指定则为 {@code null}。 */
    public String getResumeSessionId() {
        return resumeSessionId;
    }

    public static LaunchArgs parse(String[] argv) {
        boolean smoke = false;
        boolean web = false;
        boolean cli = false;
        boolean batchTests = false;
        String systemPrompt = null;
        String resumeSessionId = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if ("--smoke".equals(a)) {
                smoke = true;
            } else if ("--web".equals(a)) {
                web = true;
            } else if ("--cli".equals(a)) {
                cli = true;
            } else if ("--batch-tests".equals(a)) {
                batchTests = true;
            } else if ("--session".equals(a)) {
                if (i + 1 >= argv.length) {
                    throw new IllegalArgumentException("选项 --session 需要紧跟会话 id");
                }
                resumeSessionId = argv[++i].trim();
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
        return new LaunchArgs(smoke, web, cli, batchTests, systemPrompt, resumeSessionId, rest.toArray(new String[0]));
    }
}
