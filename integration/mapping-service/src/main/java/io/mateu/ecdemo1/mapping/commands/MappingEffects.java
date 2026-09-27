package io.mateu.ecdemo1.mapping.commands;

import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.DirectDefinitions;
import io.mateu.ecdemo1.mapping.dictionary.ImportedProfiles;
import io.mateu.ecdemo1.mapping.proposals.AgentProposals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** The mapping's own services, doing what the commands ask. */
@Component
@RequiredArgsConstructor
public class MappingEffects implements MappingCommands.Effects {

    final DirectDefinitions definitions;
    final AgentProposals agentProposals;
    final Causes causes;
    final ImportedProfiles importedProfiles;

    @Override
    public void defineUnlessInForce(MappingCommand.DefineEquivalence c) {
        definitions.defineUnlessInForce(new Dictionary.Proposal(c.codeType(), c.hotelCode(), c.sourceCode(), c.targetCode(),
                Map.of(), null, null), c.by());
    }

    @Override
    public void requestAgentProposal(String hotelCode) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentProposals.requestProposalInBackground(hotelCode);
                }
            });
        } else {
            agentProposals.requestProposalInBackground(hotelCode);
        }
    }

    @Override
    public void resolveCauseIfOpen(String causeKey, String by) {
        causes.resolveIfOpen(causeKey, by);
    }

    @Override
    public void recordPartnerProfile(String partnerCode, String pmsProfileId, String profileType) {
        importedProfiles.record(partnerCode, pmsProfileId, profileType);
    }
}
