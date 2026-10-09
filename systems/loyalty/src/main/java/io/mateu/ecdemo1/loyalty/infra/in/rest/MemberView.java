package io.mateu.ecdemo1.loyalty.infra.in.rest;

import io.mateu.ecdemo1.loyalty.store.Member;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A member as the front office (REST) and the agent (MCP) see it. {@code asOf} is when it last changed:
 * the front office shows the points "as of" then.
 */
public record MemberView(String memberNumber, String customerCode, String tier, long points, LocalDate memberSince,
                         Instant asOf) {

    public static MemberView of(Member m) {
        return new MemberView(m.memberNumber, m.customerCode, m.tier, m.points, m.memberSince, m.updatedAt);
    }
}
