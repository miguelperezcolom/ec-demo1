package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import static io.mateu.core.domain.act.crudfieldhandlers.AddActionHandler.handleAdd;
import static io.mateu.core.domain.act.crudfieldhandlers.CancelActionHandler.handleCancel;
import static io.mateu.core.domain.act.crudfieldhandlers.CreateActionHandler.handleCreate;
import static io.mateu.core.domain.act.crudfieldhandlers.MoveDownActionHandler.handleMoveDown;
import static io.mateu.core.domain.act.crudfieldhandlers.MoveUpActionHandler.handleMoveUp;
import static io.mateu.core.domain.act.crudfieldhandlers.NavigateActionHandler.handleNext;
import static io.mateu.core.domain.act.crudfieldhandlers.NavigateActionHandler.handlePrev;
import static io.mateu.core.domain.act.crudfieldhandlers.RemoveActionHandler.handleRemove;
import static io.mateu.core.domain.act.crudfieldhandlers.SaveActionHandler.handleSave;
import static io.mateu.core.domain.act.crudfieldhandlers.SelectActionHandler.handleSelect;
import static io.mateu.core.domain.act.crudfieldhandlers.SelectedActionHandler.handleSelected;

import io.mateu.uidl.interfaces.HttpRequest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The actions of a list inside a wizard step — "+", save, remove, move — routed to Mateu's own
 * handlers, which a wizard does not reach by itself.
 *
 * <p>Mateu (3.0-alpha.364) looks a list action's field up in the wizard's class, where the step is,
 * not the list; finding no list there it hands the action to the wizard, which only knows next, back
 * and finishing, so "+" re-rendered the step and did nothing. The handlers themselves need only the
 * list's field — the step's — and the component state, so pointing them at it is all a wizard lacks.
 * Goes when Mateu resolves a list action against the current step.
 */
final class WizardStepLists {

    static final List<String> ACTIONS = List.of("_create-and-stay", "_create", "_add", "_selected", "_select",
            "_prev", "_next", "_save", "_remove", "_cancel", "_move-up", "_move-down");

    /** The list field of the step that the action is about, or null if it is not a list action. */
    static Field listField(Class<?> stepType, String actionId) {
        if (actionId == null || !actionId.contains("_")) {
            return null;
        }
        var fieldId = actionId.substring(0, actionId.indexOf('_'));
        if (ACTIONS.stream().noneMatch(actionId::endsWith)) {
            return null;
        }
        return Arrays.stream(stepType.getDeclaredFields())
                .filter(f -> f.getName().equals(fieldId) && List.class.isAssignableFrom(f.getType()))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    static Object dispatch(Object wizard, String actionId, Field field, HttpRequest httpRequest) {
        var state = httpRequest.runActionRq().componentState();
        var _state = (String) state.get("_state");
        var showDetail = state.get("_show_detail") instanceof Map<?, ?> m
                ? new HashMap<>((Map<String, Object>) m) : new HashMap<String, Object>();
        var editing = state.get("_editing") instanceof Map<?, ?> m
                ? new HashMap<>((Map<String, Object>) m) : new HashMap<String, Object>();
        var fieldId = field.getName();
        if (actionId.endsWith("_create") || actionId.endsWith("_create-and-stay")) {
            return handleCreate(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_add")) {
            return handleAdd(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_selected")) {
            return handleSelected(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_select")) {
            return handleSelect(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_prev")) {
            return handlePrev(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_next")) {
            return handleNext(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_save")) {
            return handleSave(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_remove")) {
            return handleRemove(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_move-up")) {
            return handleMoveUp(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        if (actionId.endsWith("_move-down")) {
            return handleMoveDown(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
        }
        return handleCancel(wizard, actionId, httpRequest, _state, showDetail, editing, field, fieldId);
    }

    private WizardStepLists() {
    }
}
