# Salesforce, as the MDM's cleaning engine

What the customer MDM needs in the Salesforce org, in Metadata API format (`src/`), and the script
that deploys it. Salesforce cleans and deduplicates; the golden record is the MDM's
(`docs/poc-acl/plan.md`, H11; HLA CRM-MDM).

| In `src/` | What for |
| :-------- | :------- |
| `Contact.MDM_Id__c` (external id, unique), `Nationality__c`, `Document_Type__c`, `Document_Number__c` | A customer is a contact, upserted by its MDM id |
| `ClienteConsolidado__e` | The event the MDM subscribes to: a contact with an MDM id left — merged or deleted |
| Flow `Mdm_Announce_Merge` | Publishes it before the delete. `MasterRecordId` is still empty then, so the event names only what left; the MDM reads the survivor afterwards with `queryAll` |
| Flow `Mdm_Keep_Mdm_Id` | A merge must not give the survivor the absorbed contact's MDM id |
| Permission set `MDM_Integration` | The fields, for the user the integration runs as (and for stewards) |
| Matching rule `MDM_Same_Person`, duplicate rule `MDM_Possible_Duplicate` | Deliberately wide: they only propose, recording possible duplicates as duplicate record sets for a steward. Never block |

## Deploying

```bash
./deploy.py --check   # validate only
./deploy.py           # deploy, activate the flows, assign the permission set
```

It uses the MDM's own credentials: the org's External Client App with the client-credentials flow,
read from `SF_DOMAIN`, `SF_CLIENT_ID` and `SF_CLIENT_SECRET` (by default from
`~/.config/ec-demo1/salesforce.env`). The app's run-as user needs to be able to deploy metadata.

## Why flows and not Apex

The org is a Base Edition, which does not allow deploying Apex. A trigger would see `MasterRecordId`
after the delete and could name the survivor in the event; a flow cannot, hence the `queryAll`.

Deleting a field that an old flow version still references fails: activate the new version first
(`deploy.py` does, and deletes obsolete versions), then deploy the removal with a
`destructiveChangesPost.xml` next to `package.xml`.
