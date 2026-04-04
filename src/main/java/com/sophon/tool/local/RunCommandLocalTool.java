package com.sophon.tool.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolResult;
import com.sophon.tool.ToolRisk;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 在本机启动子进程执行命令（不经 shell）；用于需要操作本机时的场景。
 * <p>标记为 {@link ToolRisk#HIGH}：CLI 需终端确认，Web 无控制台时需 {@code /trust elevated}。
 */
public final class RunCommandLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final int DEFAULT_TIMEOUT_SEC = 60;
    public static final int MAX_TIMEOUT_SEC = 300;
    public static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private final ToolDefinition definition;

    public RunCommandLocalTool() {
        JsonNode schema;
        try {
            schema =
                    JSON.readTree(
                            """
                            {
                              "type": "object",
                              "properties": {
                                "command": {
                                  "type": "array",
                                  "items": { "type": "string" },
                                  "minItems": 1,
                                  "description": "argv 数组：第 1 项为可执行文件名或路径，后续为参数。勿传入整段 shell 脚本；需要管道时请拆成多次调用或使用脚本文件。"
                                },
                                "working_directory": {
                                  "type": "string",
                                  "description": "可选，相对或绝对路径；解析后必须位于 JVM 当前工作目录之下。"
                                },
                                "timeout_seconds": {
                                  "type": "integer",
                                  "minimum": 1,
                                  "maximum": 300,
                                  "description": "可选，默认 60，最长 300"
                                }
                              },
                              "required": ["command"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "run_command",
                        "在本机执行一条命令（子进程，不经 shell）。command 为 argv 数组。可能读写本机文件或改系统状态，属高危操作。",
                        schema,
                        ToolRisk.HIGH);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws ToolExecutionException {
        JsonNode args;
        try {
            args = JSON.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("参数不是合法 JSON: " + argumentsJson, e);
        }
        JsonNode cmdNode = args.path("command");
        if (!cmdNode.isArray() || cmdNode.isEmpty()) {
            return ToolResult.error("缺少 command 数组或为空");
        }
        List<String> argv = new ArrayList<>();
        for (JsonNode n : cmdNode) {
            if (!n.isTextual()) {
                return ToolResult.error("command 数组元素须为字符串");
            }
            String s = n.asText();
            if (s == null || s.isBlank()) {
                return ToolResult.error("command 数组元素不能为空");
            }
            if (s.indexOf('\0') >= 0) {
                return ToolResult.error("非法参数字符");
            }
            argv.add(s);
        }

        int timeoutSec = args.path("timeout_seconds").asInt(DEFAULT_TIMEOUT_SEC);
        if (timeoutSec < 1) {
            timeoutSec = DEFAULT_TIMEOUT_SEC;
        }
        timeoutSec = Math.min(timeoutSec, MAX_TIMEOUT_SEC);

        Path base = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path workDir = base;
        if (args.hasNonNull("working_directory")) {
            String wd = args.path("working_directory").asText("").trim();
            if (!wd.isEmpty()) {
                Path p = Path.of(wd);
                if (!p.isAbsolute()) {
                    p = base.resolve(p);
                }
                p = p.normalize().toAbsolutePath();
                if (!p.startsWith(base)) {
                    return ToolResult.error("working_directory 必须位于当前工作目录之下: " + base);
                }
                if (!Files.isDirectory(p)) {
                    return ToolResult.error("working_directory 不是目录: " + p);
                }
                workDir = p;
            }
        }

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (Exception e) {
            throw new ToolExecutionException("无法启动进程: " + e.getMessage(), e);
        }

        try {
            boolean finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return ToolResult.error("命令超时（>" + timeoutSec + "s），已终止子进程");
            }
            String out = readLimited(proc.getInputStream(), MAX_OUTPUT_BYTES);
            int code = proc.exitValue();
            return ToolResult.ok("exitCode=" + code + "\n---\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new ToolExecutionException("等待子进程被中断", e);
        } catch (Exception e) {
            proc.destroyForcibly();
            throw new ToolExecutionException("执行失败: " + e.getMessage(), e);
        }
    }

    private static String readLimited(InputStream in, int maxBytes) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.min(maxBytes, 16384));
        byte[] buf = new byte[8192];
        int total = 0;
        while (total < maxBytes) {
            int n = in.read(buf, 0, Math.min(buf.length, maxBytes - total));
            if (n < 0) {
                break;
            }
            bos.write(buf, 0, n);
            total += n;
        }
        String s = bos.toString(StandardCharsets.UTF_8);
        if (total >= maxBytes) {
            return s + "\n…（合并 stdout/stderr 输出超过 " + maxBytes + " 字节，已截断）";
        }
        return s;
    }
}
