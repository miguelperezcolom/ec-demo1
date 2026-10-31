package io.mateu.ecdemo1.users.domain.aggregates.user.vo;

/**
 * Who an employee is, for the purpose of deciding what content they see: the closed list of
 * attributes the content service's audiences are written over. Every one is optional — a person in
 * headquarters has no hotel, a contractor may have no department — and every value is a CODE from
 * the attribute catalogue ({@code AttributeValue}), never free text, so an audience written as
 * {@code region IN (CANARIAS)} cannot miss someone whose region was typed "Canarias".
 *
 * <p>Country and region are not chosen when there is a hotel: they are where the hotel is, and
 * {@link #locatedAt} is how the caller makes them so. Keeping them as fields anyway, rather than
 * computing them on read, is what lets the employee-directory message carry them flat — a
 * subscriber filters on {@code region} without knowing which hotel lies in which region.
 */
public record EmployeeAttributes(String company,
                                 String country,
                                 String region,
                                 String hotel,
                                 String department,
                                 String jobTitle,
                                 String language) {

    public static final EmployeeAttributes NONE =
            new EmployeeAttributes(null, null, null, null, null, null, null);

    /** Blank strings become null: an unselected field in a form is "no value", not a value "". */
    public EmployeeAttributes {
        company = blankToNull(company);
        country = blankToNull(country);
        region = blankToNull(region);
        hotel = blankToNull(hotel);
        department = blankToNull(department);
        jobTitle = blankToNull(jobTitle);
        language = blankToNull(language);
    }

    /** The same attributes with the country and region of the hotel, whatever was typed for them. */
    public EmployeeAttributes locatedAt(String hotelCountry, String hotelRegion) {
        return new EmployeeAttributes(company, hotelCountry, hotelRegion, hotel, department, jobTitle, language);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
