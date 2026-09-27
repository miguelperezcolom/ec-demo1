package io.mateu.ecdemo1.integrations.rest;

import io.mateu.ecdemo1.integrations.frontoffice.FoPolling;
import io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations;
import io.mateu.ecdemo1.integrations.store.FoBackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/**
 * The pms-fo integrations — a hotel's front office fed from its PMS — over REST, addressed by the PMS
 * property. For the services of the cluster and the demo's scripts, as {@code /integrations} is.
 */
@RestController
@RequestMapping("/integrations/front-office")
@RequiredArgsConstructor
public class FrontOfficeIntegrationController {

    final FrontOfficeIntegrations lifecycle;
    final FoPolling polling;
    final FrontOfficeIntegrationRepository integrations;
    final FoBackfillRunRepository runs;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a front office fed from a PMS property, and start its onboarding")
    public FrontOfficeIntegrationDto register(@RequestBody FrontOfficeIntegrations.Registration registration,
                                              @RequestParam(defaultValue = "api") String by) {
        return dto(lifecycle.register(registration, by));
    }

    @GetMapping
    @Operation(summary = "Every front office integration, by PMS property")
    public List<FrontOfficeIntegrationDto> list() {
        return integrations.findAllByOrderByPmsHotelCodeAsc().stream().map(this::dto).toList();
    }

    @GetMapping("/{pmsHotelCode}")
    public FrontOfficeIntegrationDto get(@PathVariable String pmsHotelCode) {
        return dto(lifecycle.byProperty(pmsHotelCode));
    }

    @PostMapping("/{pmsHotelCode}/verify")
    public FrontOfficeIntegrationDto verify(@PathVariable String pmsHotelCode, @RequestParam(defaultValue = "api") String by) {
        return act(pmsHotelCode, by, lifecycle::verifyNow);
    }

    @PostMapping("/{pmsHotelCode}/recheck")
    public FrontOfficeIntegrationDto recheck(@PathVariable String pmsHotelCode, @RequestParam(defaultValue = "api") String by) {
        return act(pmsHotelCode, by, lifecycle::recheck);
    }

    @PostMapping("/{pmsHotelCode}/catalogue")
    @Operation(summary = "Read the PMS's catalogue again and send it to the front office")
    public FrontOfficeIntegrationDto resyncCatalogue(@PathVariable String pmsHotelCode, @RequestParam String by) {
        return act(pmsHotelCode, by, lifecycle::resyncCatalogue);
    }

    @PostMapping("/{pmsHotelCode}/activate")
    public FrontOfficeIntegrationDto activate(@PathVariable String pmsHotelCode, @RequestParam String by) {
        return act(pmsHotelCode, by, lifecycle::activate);
    }

    @PostMapping("/{pmsHotelCode}/pause")
    public FrontOfficeIntegrationDto pause(@PathVariable String pmsHotelCode, @RequestParam String by) {
        return act(pmsHotelCode, by, lifecycle::pause);
    }

    @PostMapping("/{pmsHotelCode}/resume")
    public FrontOfficeIntegrationDto resume(@PathVariable String pmsHotelCode, @RequestParam String by) {
        return act(pmsHotelCode, by, lifecycle::resume);
    }

    @PostMapping("/{pmsHotelCode}/decommission")
    public FrontOfficeIntegrationDto decommission(@PathVariable String pmsHotelCode, @RequestParam String by) {
        return act(pmsHotelCode, by, lifecycle::decommission);
    }

    @PostMapping("/{pmsHotelCode}/backfills")
    @Operation(summary = "Project the property's reservations of the horizon into the front office again")
    public FrontOfficeIntegrationDto relaunchBackfill(@PathVariable String pmsHotelCode, @RequestParam String by) {
        var i = lifecycle.byProperty(pmsHotelCode);
        lifecycle.relaunchBackfill(i.id, by);
        return dto(lifecycle.find(i.id));
    }

    @PostMapping("/{pmsHotelCode}/poll")
    @Operation(summary = "Ask the PMS now for what changed since the last poll")
    public FrontOfficeIntegrationDto poll(@PathVariable String pmsHotelCode, @RequestParam(defaultValue = "api") String by) {
        return act(pmsHotelCode, by, polling::pollNow);
    }

    FrontOfficeIntegrationDto act(String pmsHotelCode, String by, BiFunction<String, String, FrontOfficeIntegration> action) {
        return dto(action.apply(lifecycle.byProperty(pmsHotelCode).id, by));
    }

    FrontOfficeIntegrationDto dto(FrontOfficeIntegration i) {
        return FrontOfficeIntegrationDto.of(i, runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id).orElse(null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
