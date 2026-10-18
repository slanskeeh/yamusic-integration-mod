package dev.yamod.redio.client.audio;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Отдаёт уже декодированный PCM звуковому движку Minecraft.
 * Пустой ответ означает конец трека. Пока данные ещё качаются, в канал уходит тишина,
 * чтобы движок не закрыл источник.
 */
public final class PcmAudioStream implements AudioStream {
    private final AudioFormat format;
    private final BlockingQueue<byte[]> chunks;
    private final AtomicInteger bufferedBytes;
    private final AtomicInteger consumedBytes;
    private final AtomicBoolean finished;
    private final AtomicInteger generation;
    private final AtomicBoolean ended;
    private final AtomicBoolean closed = new AtomicBoolean();
    private byte[] pending = new byte[0];
    private int pendingOffset;

    public PcmAudioStream(
            AudioFormat format,
            BlockingQueue<byte[]> chunks,
            AtomicInteger bufferedBytes,
            AtomicInteger consumedBytes,
            AtomicBoolean finished,
            AtomicInteger generation,
            AtomicBoolean ended
    ) {
        this.format = format;
        this.chunks = chunks;
        this.bufferedBytes = bufferedBytes;
        this.consumedBytes = consumedBytes;
        this.finished = finished;
        this.generation = generation;
        this.ended = ended;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (closed.get()) {
            return null;
        }
        int frameSize = Math.max(1, format.getFrameSize());
        int wanted = Math.max(frameSize, size - (size % frameSize));
        ByteBuffer buffer = ByteBuffer.allocateDirect(wanted);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(40);
        while (buffer.hasRemaining() && !closed.get()) {
            dropIfSeeked();
            if (!fillPending()) {
                if (finished.get() && chunks.isEmpty()) {
                    break;
                }
                if (System.nanoTime() >= deadline) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            int count = Math.min(buffer.remaining(), pending.length - pendingOffset);
            if (count <= 0) {
                continue;
            }
            buffer.put(pending, pendingOffset, count);
            pendingOffset += count;
            bufferedBytes.addAndGet(-count);
            consumedBytes.addAndGet(count);
        }
        if (buffer.position() == 0) {
            if (closed.get()) {
                return null;
            }
            if (finished.get() && chunks.isEmpty() && pendingOffset >= pending.length) {
                ended.set(true);
                return null;
            }
            buffer.put(new byte[Math.min(wanted, frameSize * 64)]);
        }
        buffer.flip();
        return buffer;
    }

    private int appliedGeneration;

    private void dropIfSeeked() {
        int current = generation.get();
        if (current == appliedGeneration) {
            return;
        }
        pending = new byte[0];
        pendingOffset = 0;
        appliedGeneration = current;
    }

    private boolean fillPending() {
        if (pendingOffset < pending.length) {
            return true;
        }
        byte[] next = chunks.poll();
        if (next == null) {
            pending = new byte[0];
            pendingOffset = 0;
            return false;
        }
        pending = next;
        pendingOffset = 0;
        return true;
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        chunks.clear();
    }
}
