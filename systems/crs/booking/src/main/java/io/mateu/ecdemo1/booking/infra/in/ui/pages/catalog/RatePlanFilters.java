package io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog;

import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;

/** The hotel whose rate plans are listed; its options are the CRS catalog's hotels ({@link RatePlansCrud}). */
public class RatePlanFilters {

    @Stereotype(FieldStereotype.select)
    String hotel;
}
