package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

/**
 * Who the booking is for and who is contacted about it — the "titular" of a CRS booking. Not
 * necessarily one of the guests: a parent can book a room for their children.
 */
public record Holder(String firstName, String lastName, String email, String phone, String nationality) {

    public Holder {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("The holder needs a first name and a last name");
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
