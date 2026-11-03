package io.mateu.ecdemo1.users.domain.aggregates.user.vo;

/**
 * Where an employee sits in the organisation and how to reach them: who they report to and their work
 * phone. What the intranet's directory draws (the org chart, a person's card), and nothing an audience
 * is written over — that is {@link EmployeeAttributes}, a closed list of catalogue codes. Kept apart so
 * the two cannot drift into each other: a manager is a person, not a code, and a phone is free text.
 *
 * <p>Both optional: the head of the company reports to nobody, and not everybody has a work phone.
 */
public record EmployeeProfile(String managerId, String phone, String photoUrl) {

    public static final EmployeeProfile NONE = new EmployeeProfile(null, null, null);

    /** Blank strings become null: an unselected field in a form is "no value", not a value "". */
    public EmployeeProfile {
        managerId = blankToNull(managerId);
        phone = blankToNull(phone);
        photoUrl = blankToNull(photoUrl);
    }

    /** Without a say on the photo: whoever saves this keeps the one there is. */
    public EmployeeProfile(String managerId, String phone) {
        this(managerId, phone, null);
    }

    public EmployeeProfile withPhotoUrl(String photoUrl) {
        return new EmployeeProfile(managerId, phone, photoUrl);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
