package io.mateu.ecdemo1.contenthub.domain.shared;

/**
 * What readers do with the content besides reading it — comments, likes, surveys — as events others
 * may care about (a notification to an item's author, analytics): out through the outbox on
 * {@code content-events} like the rest. None carries who answered a survey: an anonymous answer must
 * not be traceable from the topic either.
 */
public final class CollaborationEvents {

    private CollaborationEvents() {
    }

    /** One family, permitted as a whole by {@link DomainEvent}. */
    public sealed interface Event extends DomainEvent permits CommentPosted, CommentModerated, ItemLiked,
            SurveyPublished, SurveyUpdated, SurveyClosed, SurveyAnswered {
    }

    /** A reader commented on an item they can see. */
    public record CommentPosted(String itemId, String commentId, String authorId) implements Event {
        public String subject() {
            return itemId;
        }
    }

    /** An editor hid a comment, or showed it again. */
    public record CommentModerated(String itemId, String commentId, boolean hidden, String by) implements Event {
        public String subject() {
            return itemId;
        }
    }

    /** A reader liked an item; {@code likes} is the count after it. Unliking is not announced. */
    public record ItemLiked(String itemId, int likes) implements Event {
        public String subject() {
            return itemId;
        }
    }

    /** A survey opened to its audience: its projection is (re)computed. */
    public record SurveyPublished(String surveyId) implements Event {
        public String subject() {
            return surveyId;
        }
    }

    /** A published survey changed (its audience, its dates, its texts): projected again. */
    public record SurveyUpdated(String surveyId) implements Event {
        public String subject() {
            return surveyId;
        }
    }

    public record SurveyClosed(String surveyId) implements Event {
        public String subject() {
            return surveyId;
        }
    }

    /** Someone answered — never who: anonymous surveys must stay so outside the service too. */
    public record SurveyAnswered(String surveyId) implements Event {
        public String subject() {
            return surveyId;
        }
    }
}
