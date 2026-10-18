package dev.yamod.redio.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Декодирует mp3 отдельным процессом Java. Внутри игры JLayer не доходит до первого кадра.
 */
public final class ExternalMp3Decoder implements AutoCloseable {
    private final Process process;
    private final InputStream output;
    private final Thread errors;
    private volatile String stderr = "";

    private ExternalMp3Decoder(Process process) {
        this.process = process;
        this.output = process.getInputStream();
        this.errors = new Thread(this::collectErrors, "redio-mp3-err");
        this.errors.setDaemon(true);
        this.errors.start();
    }

    public static ExternalMp3Decoder start(byte[] mp3) throws IOException {
        if (mp3 == null || mp3.length == 0) {
            throw new IOException("Пустой mp3.");
        }
        Path executable = javaBinary();
        Path classes = codeDirectory();
        Path jlayer = jlayerJar();
        String classpath = classes + java.io.File.pathSeparator + jlayer;
        Process process = new ProcessBuilder(
                executable.toString(),
                "-cp",
                classpath,
                Mp3ToPcm.class.getName()
        ).start();
        Thread writer = new Thread(() -> writeInput(process, mp3), "redio-mp3-in");
        writer.setDaemon(true);
        writer.start();
        return new ExternalMp3Decoder(process);
    }

    public AudioFormat readFormat() throws IOException {
        byte[] header = output.readNBytes(8);
        if (header.length < 8) {
            close();
            throw new IOException(failure("Внешний декодер закрылся, не отдав формат звука."));
        }
        int sampleRate = readInt(header, 0);
        int channels = readInt(header, 4);
        if (sampleRate < 8000 || sampleRate > 96000 || channels < 1 || channels > 2) {
            close();
            throw new IOException("Внешний декодер вернул странный формат: " + sampleRate + " Гц, каналов " + channels + ".");
        }
        return new AudioFormat(sampleRate, 16, channels, true, false);
    }

    public int read(byte[] buffer) throws IOException {
        return output.read(buffer);
    }

    @Override
    public void close() {
        process.destroyForcibly();
        try {
            errors.join(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void collectErrors() {
        try {
            byte[] bytes = process.getErrorStream().readAllBytes();
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (text.length() > 300) {
                text = text.substring(0, 300);
            }
            stderr = text;
        } catch (IOException ignored) {
            stderr = "";
        }
    }

    private String failure(String fallback) {
        try {
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            errors.join(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (stderr.isBlank()) {
            return fallback;
        }
        return fallback + " " + stderr;
    }

    private static void writeInput(Process process, byte[] mp3) {
        try (OutputStream stream = process.getOutputStream()) {
            stream.write(mp3);
        } catch (IOException ignored) {
            process.destroyForcibly();
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static Path javaBinary() throws IOException {
        Path home = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (Files.isRegularFile(home)) {
            return home;
        }
        Path plain = Path.of(System.getProperty("java.home"), "bin", "java");
        if (Files.isRegularFile(plain)) {
            return plain;
        }
        throw new IOException("Не найден исполняемый файл Java для внешнего декодера.");
    }

    private static Path codeDirectory() throws IOException {
        String relative = "dev/yamod/redio/audio/Mp3ToPcm.class";
        Path fromResource = directoryContaining(Mp3ToPcm.class.getResource("Mp3ToPcm.class"), relative);
        if (fromResource != null) {
            return fromResource;
        }
        Path cwd = Path.of(System.getProperty("user.dir", "."));
        Path[] candidates = {
                cwd.resolve("build/classes/java/main"),
                cwd.resolve("../build/classes/java/main").normalize(),
                cwd.resolve("../../build/classes/java/main").normalize()
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve(relative))) {
                return candidate;
            }
        }
        throw new IOException("Не найден Mp3ToPcm.class. Клиент нужно запускать через ./client.sh из каталога проекта.");
    }

    private static Path directoryContaining(URL url, String relative) {
        if (url == null) {
            return null;
        }
        String text = url.toExternalForm().replace('\\', '/');
        String marker = "/" + relative;
        int index = text.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String root = text.substring(0, index);
        int drive = -1;
        for (int i = 0; i + 2 < root.length(); i++) {
            char letter = root.charAt(i);
            if (Character.isLetter(letter) && root.charAt(i + 1) == ':' && root.charAt(i + 2) == '/') {
                drive = i;
            }
        }
        if (drive >= 0) {
            root = root.substring(drive);
        }
        Path path = Path.of(root);
        return Files.isRegularFile(path.resolve(relative)) ? path : null;
    }

    private static Path jlayerJar() throws IOException {
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            String name = Path.of(entry).getFileName().toString();
            if (name.startsWith("jlayer") && name.endsWith(".jar")) {
                Path path = Path.of(entry);
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        }
        Path cache = Path.of(
                System.getProperty("user.home"),
                ".gradle", "caches", "modules-2", "files-2.1", "javazoom", "jlayer", "1.0.1"
        );
        if (!Files.isDirectory(cache)) {
            throw new IOException("Не найден jlayer-1.0.1.jar для внешнего декодера.");
        }
        try (Stream<Path> files = Files.walk(cache, 2)) {
            return files.filter(path -> path.getFileName().toString().equals("jlayer-1.0.1.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Не найден jlayer-1.0.1.jar для внешнего декодера."));
        }
    }
}
