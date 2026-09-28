package io.mateu.ecdemo1.contracts.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The tasks a worker service serves, as its registrations say — {@code <id>@<version> <topic>} a
 * line — kept in {@code contracts/workers/<service>.tasks}, where deploy/demo/check-contracts.sh
 * matches them against every task a definition in ec-definitions references. Written with
 * {@code -Dcontracts.write=true}; otherwise a registration the file does not list (or the reverse)
 * fails the build.
 *
 * <p>Read from the (instance) {@code @Bean} methods of the service's task configuration that return a
 * {@code TaskRegistration} — each called with a mock of every parameter, since only the
 * registration's contract reference and topic are read, never its handler.
 */
public final class ServedTasks {

    private ServedTasks() {
    }

    /** The {@code <id>@<version> <topic>} lines of every registration the configurations declare. */
    public static List<String> of(Function<Class<?>, Object> mocks, Object... configurations) {
        var lines = new ArrayList<String>();
        for (var configuration : configurations) {
            var methods = Arrays.stream(configuration.getClass().getDeclaredMethods())
                    .filter(m -> !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                    .filter(m -> m.getReturnType().getSimpleName().equals("TaskRegistration"))
                    .sorted(Comparator.comparing(Method::getName))
                    .toList();
            for (var method : methods) {
                try {
                    method.setAccessible(true);
                    var args = Arrays.stream(method.getParameterTypes()).map(mocks).toArray();
                    var registration = method.invoke(configuration, args);
                    var ref = registration.getClass().getMethod("ref").invoke(registration);
                    var topic = registration.getClass().getMethod("topic").invoke(registration);
                    lines.add(ref + " " + topic);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read the registration of " + method, e);
                }
            }
        }
        lines.sort(null);
        return lines;
    }

    public static Path file(String service) {
        return Contracts.schemas().getParent().resolve("workers").resolve(service + ".tasks");
    }

    /** Writes the service's list, or fails when the committed one is not what it registers. */
    public static void publish(String service, List<String> lines) {
        var file = file(service);
        var text = "# " + service + ": the tasks it serves, <id>@<version> <topic> — generated from its "
                + "TaskRegistrations (-Dcontracts.write=true)\n" + String.join("\n", lines) + "\n";
        try {
            var committed = Files.exists(file) ? Files.readString(file) : null;
            if (text.equals(committed)) {
                return;
            }
            if (SchemaFiles.writing()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text);
                return;
            }
            throw new AssertionError(("%s serves other tasks than %s lists — run the build with "
                    + "-Dcontracts.write=true and commit it:\n%s").formatted(service, file, text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
