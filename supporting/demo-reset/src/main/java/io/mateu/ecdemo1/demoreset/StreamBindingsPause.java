package io.mateu.ecdemo1.demoreset;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binding.BindingService;

/**
 * The service's Spring Cloud Stream consumers, paused around its reset — every input binding but the
 * engine's ({@code consumeWorkerEvent-in-0}): the reset itself arrives through it, and its reply has
 * to leave.
 */
public class StreamBindingsPause implements ConsumerPause {

    private static final Logger log = LoggerFactory.getLogger(StreamBindingsPause.class);

    public static final Set<String> KEPT = Set.of("consumeWorkerEvent-in-0");

    private final BindingService bindings;
    private final Set<String> kept;
    private final Duration wait;
    private final List<Binding<?>> paused = new ArrayList<>();

    public StreamBindingsPause(BindingService bindings, Set<String> kept, Duration wait) {
        this.bindings = bindings;
        this.kept = kept;
        this.wait = wait;
    }

    @Override
    public synchronized void pause() {
        paused.clear();
        for (var name : bindings.getConsumerBindingNames()) {
            if (kept.contains(name)) {
                continue;
            }
            for (var binding : bindings.getConsumerBindings(name)) {
                if (binding.isRunning() && !binding.isPaused()) {
                    binding.pause();
                    paused.add(binding);
                }
            }
        }
        var until = System.nanoTime() + wait.toNanos();
        while (paused.stream().anyMatch(b -> !b.isPaused()) && System.nanoTime() < until) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Paused for the demo reset: {}", paused.stream().map(Binding::getBindingName).toList());
    }

    @Override
    public synchronized void resume() {
        for (var binding : paused) {
            binding.resume();
        }
        log.info("Resumed after the demo reset: {}", paused.stream().map(Binding::getBindingName).toList());
        paused.clear();
    }

    @Override
    public String describe() {
        return "Spring Cloud Stream input bindings";
    }
}
