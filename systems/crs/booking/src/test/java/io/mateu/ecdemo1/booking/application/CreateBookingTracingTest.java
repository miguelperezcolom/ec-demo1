package io.mateu.ecdemo1.booking.application;

import io.mateu.core.infra.valuegenerators.LocatorValueGenerator;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.quote.QuoteBookingUseCase;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import io.mateu.ecdemo1.booking.tracing.Traces;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** A booking is where its trace starts: found in Tempo by its id, its locator and its hotel. */
class CreateBookingTracingTest {

    final List<SpanData> exported = new CopyOnWriteArrayList<>();
    final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(new SpanExporter() {
                @Override
                public CompletableResultCode export(Collection<SpanData> spans) {
                    exported.addAll(spans);
                    return CompletableResultCode.ofSuccess();
                }

                @Override
                public CompletableResultCode flush() {
                    return CompletableResultCode.ofSuccess();
                }

                @Override
                public CompletableResultCode shutdown() {
                    return CompletableResultCode.ofSuccess();
                }
            })).build();
    final OtelTracer tracer = new OtelTracer(provider.get("test"), new OtelCurrentTraceContext(), event -> {
    });
    final Traces traces = traces();

    final CrsCatalog catalog = io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs.standardCatalog();
    final BookingTermsFactory terms = new BookingTermsFactory(catalog, new RoomPricing());
    final WalkInBookingTest.Store store = new WalkInBookingTest.Store();
    final CreateBookingUseCase create = new CreateBookingUseCase(store, terms, catalog, new LocatorValueGenerator(),
            Clock.systemUTC(), traces, RecordingTrail.audit());
    final QuoteBookingUseCase quote = new QuoteBookingUseCase(catalog, terms);

    Traces traces() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("tracer", tracer);
        beans.addBean("propagator", new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()),
                provider.get("test")));
        return new Traces(beans.getBeanProvider(Tracer.class), beans.getBeanProvider(Propagator.class));
    }

    @AfterEach
    void close() {
        provider.close();
    }

    String book() {
        var total = quote.handle("MRU01", WalkInBookingTest.walkIn(null, null, 2)).total();
        return create.handle(new CreateBookingCommand("MRU01",
                WalkInBookingTest.walkIn("FO-T1", WalkInBookingTest.holder(), 2), total));
    }

    @Test
    void withNoSpanAroundItABookingOpensTheRootOfItsTrace() {
        var id = book();

        assertThat(exported).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("booking.create");
            assertThat(span.getParentSpanContext().isValid()).isFalse();
            assertThat(span.getAttributes().get(AttributeKey.stringKey("booking.id"))).isEqualTo(id);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("booking.locator"))).isEqualTo(id);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("hotel.code"))).isEqualTo("MRU01");
        });
    }

    @Test
    void insideARequestItTagsTheRequestsSpanInstead() {
        var request = tracer.nextSpan().name("http post /bookings").start();
        String id;
        try (var ignored = tracer.withSpan(request)) {
            id = book();
        } finally {
            request.end();
        }

        assertThat(exported).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("http post /bookings");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("booking.id"))).isEqualTo(id);
        });
    }

    @Test
    void untracedItBooksAsBefore() {
        var untraced = new CreateBookingUseCase(store, terms, catalog, new LocatorValueGenerator(), Clock.systemUTC(),
                Traces.untraced(), RecordingTrail.audit());
        var total = quote.handle("MRU01", WalkInBookingTest.walkIn(null, null, 2)).total();

        assertThat(untraced.handle(new CreateBookingCommand("MRU01",
                WalkInBookingTest.walkIn("FO-T2", WalkInBookingTest.holder(), 2), total))).isNotBlank();
        assertThat(exported).isEmpty();
    }
}
