package dev.liamtolkkinen.god;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class DuelManager {
    private static final Duration INVITATION_LIFETIME = Duration.ofSeconds(60);
    private static final Duration COMBAT_LIFETIME = Duration.ofMinutes(2);
    private final Map<UUID, Invitation> invitationsByTarget = new HashMap<>();
    private final Map<String, ActiveDuel> activeDuels = new HashMap<>();
    private final Map<String, CombatIncident> incidents = new HashMap<>();

    synchronized ChallengeResult challenge(UUID challenger, UUID target) {
        cleanup();
        if (challenger.equals(target)) throw new IllegalArgumentException("You cannot duel yourself.");
        Invitation reciprocal = invitationsByTarget.get(challenger);
        if (reciprocal != null && reciprocal.challenger().equals(target)) {
            invitationsByTarget.remove(challenger);
            activeDuels.put(pair(challenger, target), new ActiveDuel(challenger, target, Instant.now()));
            incidents.remove(pair(challenger, target));
            return ChallengeResult.ACCEPTED;
        }
        invitationsByTarget.put(target, new Invitation(challenger, target, Instant.now()));
        return ChallengeResult.CHALLENGED;
    }

    synchronized boolean accept(UUID target, UUID challenger) {
        cleanup();
        Invitation invitation = invitationsByTarget.get(target);
        if (invitation == null || !invitation.challenger().equals(challenger)) return false;
        invitationsByTarget.remove(target);
        activeDuels.put(pair(target, challenger), new ActiveDuel(target, challenger, Instant.now()));
        incidents.remove(pair(target, challenger));
        return true;
    }

    synchronized boolean decline(UUID target, UUID challenger) {
        cleanup();
        Invitation invitation = invitationsByTarget.get(target);
        if (invitation == null || !invitation.challenger().equals(challenger)) return false;
        invitationsByTarget.remove(target);
        return true;
    }

    synchronized void cancel(UUID player) {
        invitationsByTarget.entrySet().removeIf(entry -> entry.getKey().equals(player) || entry.getValue().challenger().equals(player));
        activeDuels.entrySet().removeIf(entry -> entry.getValue().first().equals(player) || entry.getValue().second().equals(player));
    }

    synchronized boolean isActive(UUID first, UUID second) {
        cleanup();
        return activeDuels.containsKey(pair(first, second));
    }

    synchronized AttackResult recordAttack(UUID attacker, UUID victim) {
        cleanup();
        if (attacker.equals(victim)) return AttackResult.SELF;
        String pair = pair(attacker, victim);
        ActiveDuel duel = activeDuels.get(pair);
        if (duel != null) {
            activeDuels.put(pair, new ActiveDuel(duel.first(), duel.second(), Instant.now()));
            return AttackResult.CONSENSUAL;
        }

        Invitation invitationToAttacker = invitationsByTarget.get(attacker);
        if (invitationToAttacker != null && invitationToAttacker.challenger().equals(victim)) {
            invitationsByTarget.remove(attacker);
            activeDuels.put(pair, new ActiveDuel(attacker, victim, Instant.now()));
            incidents.remove(pair);
            return AttackResult.ACCEPTED_BY_ATTACK;
        }

        Invitation invitationToVictim = invitationsByTarget.get(victim);
        if (invitationToVictim != null && invitationToVictim.challenger().equals(attacker)) invitationsByTarget.remove(victim);

        CombatIncident incident = incidents.get(pair);
        if (incident == null || incident.lastActivity().isBefore(Instant.now().minus(Duration.ofSeconds(30)))) {
            incidents.put(pair, new CombatIncident(attacker, victim, Instant.now()));
            return AttackResult.NEW_OFFENCE;
        }
        incidents.put(pair, new CombatIncident(incident.initiator(), incident.defender(), Instant.now()));
        return incident.initiator().equals(attacker) ? AttackResult.CONTINUED_OFFENCE : AttackResult.SELF_DEFENSE;
    }

    synchronized String status(UUID player) {
        cleanup();
        for (ActiveDuel duel : activeDuels.values()) {
            if (duel.first().equals(player) || duel.second().equals(player)) return "An active duel is in progress.";
        }
        Invitation invitation = invitationsByTarget.get(player);
        if (invitation != null) return "A duel invitation is awaiting your response.";
        boolean outgoing = invitationsByTarget.values().stream().anyMatch(value -> value.challenger().equals(player));
        return outgoing ? "Your duel challenge is awaiting a response." : "No duel is pending or active.";
    }

    private void cleanup() {
        Instant now = Instant.now();
        invitationsByTarget.entrySet().removeIf(entry -> entry.getValue().created().isBefore(now.minus(INVITATION_LIFETIME)));
        activeDuels.entrySet().removeIf(entry -> entry.getValue().lastActivity().isBefore(now.minus(COMBAT_LIFETIME)));
        incidents.entrySet().removeIf(entry -> entry.getValue().lastActivity().isBefore(now.minus(Duration.ofSeconds(30))));
    }

    private String pair(UUID first, UUID second) {
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    enum ChallengeResult { CHALLENGED, ACCEPTED }
    enum AttackResult { SELF, CONSENSUAL, ACCEPTED_BY_ATTACK, NEW_OFFENCE, CONTINUED_OFFENCE, SELF_DEFENSE }
    private record Invitation(UUID challenger, UUID target, Instant created) {}
    private record ActiveDuel(UUID first, UUID second, Instant lastActivity) {}
    private record CombatIncident(UUID initiator, UUID defender, Instant lastActivity) {}
}
