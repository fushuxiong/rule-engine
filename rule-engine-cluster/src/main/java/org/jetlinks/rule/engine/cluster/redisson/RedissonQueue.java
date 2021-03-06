package org.jetlinks.rule.engine.cluster.redisson;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.cluster.Queue;
import org.redisson.api.RQueue;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonQueue<T> implements Queue<T> {

    private RQueue<T> queue;

    private volatile AtomicReference<Consumer<T>> consumer = new AtomicReference<>();

    private volatile boolean accepted;

    public RedissonQueue(RQueue<T> queue) {
        this.queue = queue;
    }

    public void start() {
        accepted = true;
        flush();
    }

    public void flush() {
        if (accepted) {
            for (T data = queue.poll(); data != null; data = queue.poll()) {
                Consumer<T> consumer;
                if ((consumer = this.consumer.get()) != null) {
                    consumer.accept(data);
                } else {
                    queue.add(data);
                    break;
                }
            }
        }

    }

    @Override
    public void accept(Consumer<T> consumer) {
        if (this.consumer.get() == null) {
            this.consumer.set(consumer);
        } else {
            this.consumer.set(this.consumer.get().andThen(consumer));
        }
        if (!accepted) {
            start();
        }
    }

    @Override
    public boolean acceptOnce(Consumer<T> consumer) {
        this.consumer.set(consumer);
        if (!accepted) {
            start();
        }
        return true;
    }

    @Override
    public CompletionStage<Boolean> putAsync(T data) {
        return queue.addAsync(data);
    }

    @Override
    @SneakyThrows
    public void put(T data) {
        if(!queue.add(data)){
            throw new RuntimeException();
        }
    }

    @Override
    public void stop() {
        consumer.set(null);
    }
}
