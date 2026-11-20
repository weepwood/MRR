package com.zjcxph.imgapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MRR 本机 Nginx 网关的只读运维适配器。
 * 不接受来自 HTTP 的文件路径、命令或可执行程序名；不负责服务启停/重载。
 */
@Service
public class NginxManagementService {
    private static final int MAX_LOG_BYTES = 128 * 1024;
    private static final int MAX_LOG_LINES = 200;
    private static final int MAX_COMMAND_BYTES = 4096;
    private static final Pattern VERSION = Pattern.compile("nginx/[0-9]+(?:\\.[0-9]+)+");
    private static final Pattern SERVICE_STATE = Pattern.compile("(?m)^\\s*STATE\\s*:\\s*(\\d+)");
    private static final Pattern URL_QUERY = Pattern.compile("\\?[^\\s\\\"']+");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+)[^\\s\\\"']+");
    private static final Pattern ARCHIVE_IDENTIFIER = Pattern.compile(
            "(?i)(/(?:idcard|archive/external)/)[^\\s/\\?\"']+");
    private static final List<String> MANAGED_CONFIG_FILES = List.of(
            "nginx.conf", "proxy.inc", "maintenance.inc", "frontend-mode.inc", "security-headers.inc");

    private final boolean enabled;
    private final Path root;
    private final boolean windows;
    private final Semaphore testSemaphore = new Semaphore(1);

    public NginxManagementService(
            @Value("${app.operations.nginx.enabled:false}") boolean enabled,
            @Value("${app.operations.nginx.root:C:/MRR}") String root
    ) {
        this.enabled = enabled;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public Map<String, Object> status() {
        boolean installed = Files.isRegularFile(binary()) && Files.isRegularFile(config());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", enabled);
        response.put("supported", windows);
        response.put("installed", installed);
        response.put("serviceName", "MRR-Gateway");
        response.put("serviceState", "UNAVAILABLE");
        response.put("version", "");
        response.put("configurationPresent", Files.isRegularFile(config()));
        response.put("lastCheckedAt", Instant.now().toString());
        if (!enabled || !windows || !installed) {
            return response;
        }
        try {
            RunResult state = run(List.of(serviceController(), "query", "MRR-Gateway"), null, 3);
            if (state.exitCode() == 0) {
                Matcher matcher = SERVICE_STATE.matcher(state.output());
                if (matcher.find()) {
                    response.put("serviceState", switch (matcher.group(1)) {
                        case "4" -> "RUNNING";
                        case "1" -> "STOPPED";
                        default -> "OTHER";
                    });
                }
            }
            RunResult version = run(List.of(binary().toString(), "-v"), binary().getParent(), 3);
            Matcher matcher = VERSION.matcher(version.output());
            if (version.exitCode() == 0 && matcher.find()) {
                response.put("version", matcher.group());
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 状态接口不能因服务查询失败而返回敏感路径或内部异常。
        }
        return response;
    }

    public List<Map<String, Object>> configFiles() {
        ensureEnabled();
        List<Map<String, Object>> files = new ArrayList<>();
        for (String name : MANAGED_CONFIG_FILES) {
            Path file = root.resolve("config/nginx").resolve(name);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("present", Files.isRegularFile(file));
            try {
                item.put("bytes", Files.isRegularFile(file) ? Files.size(file) : null);
                item.put("modifiedAt", Files.isRegularFile(file)
                        ? Files.getLastModifiedTime(file).toInstant().toString() : null);
            } catch (IOException ignored) {
                item.put("bytes", null);
                item.put("modifiedAt", null);
            }
            files.add(item);
        }
        return files;
    }

    /**
     * 只允许操作固定安装包内的 nginx.exe；没有 shell，也没有来自请求的参数。
     */
    public Map<String, Object> testConfig() {
        ensureReady();
        if (!testSemaphore.tryAcquire()) {
            throw new IllegalStateException("已有 Nginx 配置检测正在执行");
        }
        try {
            long started = System.nanoTime();
            RunResult result = run(List.of(binary().toString(),
                    "-p", binary().getParent().toString(), "-c", config().toString(), "-t"),
                    binary().getParent(), 10);
            return Map.of(
                    "valid", result.exitCode() == 0,
                    "message", result.exitCode() == 0 ? "配置校验通过" : "配置校验失败，请检查服务器侧 Nginx 错误日志",
                    "durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("配置检查被中断");
        } catch (IOException exception) {
            throw new IllegalStateException("无法运行配置检查，请核实 Nginx 文件与服务账号权限");
        } finally {
            testSemaphore.release();
        }
    }

    public Map<String, Object> tailLog(String type, int lines) {
        ensureEnabled();
        if (lines < 1 || lines > MAX_LOG_LINES) {
            throw new IllegalArgumentException("日志行数必须在 1 到 200 之间");
        }
        String fileName = switch (type) {
            case "access" -> "access.log";
            case "error" -> "error.log";
            default -> throw new IllegalArgumentException("不支持的日志类型");
        };
        Path file = root.resolve("logs/nginx").resolve(fileName);
        try {
            if (!Files.isRegularFile(file)) {
                return Map.of("type", type, "lines", List.of(), "truncated", false);
            }
            // 不允许日志路径通过符号链接或 Windows junction 越出安装根目录。
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalStateException("日志路径不属于受管目录");
            }
            long length = Files.size(file);
            int bytes = (int) Math.min(length, MAX_LOG_BYTES);
            byte[] buffer = new byte[bytes];
            try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
                input.seek(length - bytes);
                input.readFully(buffer);
            }
            String text = new String(buffer, StandardCharsets.UTF_8);
            if (length > bytes) {
                int firstBreak = text.indexOf('\n');
                text = firstBreak < 0 ? "" : text.substring(firstBreak + 1);
            }
            String[] fragments = text.split("\\R");
            int begin = Math.max(0, fragments.length - lines);
            List<String> result = new ArrayList<>();
            for (int i = begin; i < fragments.length; i++) {
                if (!fragments[i].isBlank()) {
                    result.add(sanitizeLogLine(fragments[i]));
                }
            }
            return Map.of("type", type, "lines", result, "truncated", length > bytes || begin > 0);
        } catch (IOException exception) {
            throw new IllegalStateException("日志暂时不可读取，请核实 Nginx 日志访问权限");
        }
    }

    static String sanitizeLogLine(String line) {
        String result = CREDENTIAL.matcher(line).replaceAll("$1[REDACTED]");
        result = URL_QUERY.matcher(result).replaceAll("?[REDACTED]");
        result = ARCHIVE_IDENTIFIER.matcher(result).replaceAll("$1[REDACTED]");
        return result.length() > 2048 ? result.substring(0, 2048) + "…" : result;
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Nginx Web 运维功能未启用");
        }
    }

    private void ensureReady() {
        ensureEnabled();
        if (!windows) {
            throw new IllegalStateException("配置检查仅支持受管 Windows Server 环境");
        }
        if (!Files.isRegularFile(binary()) || !Files.isRegularFile(config())) {
            throw new IllegalStateException("未找到 MRR 受管 Nginx 安装");
        }
    }

    private Path binary() {
        return root.resolve("runtime/nginx/nginx.exe");
    }

    private Path config() {
        return root.resolve("config/nginx/nginx.conf");
    }

    private String serviceController() {
        String systemRoot = System.getenv("SystemRoot");
        return Path.of(systemRoot == null || systemRoot.isBlank() ? "C:/Windows" : systemRoot,
                "System32", "sc.exe").toString();
    }

    private static RunResult run(List<String> command, Path workingDirectory, int timeoutSeconds)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        Process process = builder.start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thread collector = Thread.ofVirtual().start(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] chunk = new byte[1024];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    synchronized (buffer) {
                        if (buffer.size() < MAX_COMMAND_BYTES) {
                            buffer.write(chunk, 0, Math.min(read, MAX_COMMAND_BYTES - buffer.size()));
                        }
                    }
                }
            } catch (IOException ignored) {
                // 超时杀进程时流可能被关闭。
            }
        });
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                throw new IOException("Nginx 命令执行超时");
            }
            collector.join(1000);
            synchronized (buffer) {
                return new RunResult(process.exitValue(), buffer.toString(StandardCharsets.UTF_8));
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private record RunResult(int exitCode, String output) { }
}
