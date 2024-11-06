# Description

In APIM 3.2.0 adding common custom policies functionality was not available OOTB from the publisher UI.

However since the common mediation policies were managed from the Registry, adding custom common policies through the registry(registry path /_system/governance/apimgt/customsequences/) is possible.

In 4.2.0 adding custom common policy support is available from the publisher UI and the policies are stored in AM_DB.
When a user is migrating from 3.2.0 to 4.2.0 the custom common polices in registry is not getting added to 4.2.0 as common policies. 

Hence, this client is designed to migrate registry common policies to AM_DB in 4.2.0 so that the policies will be available as common policies in 4.2.0.

# Implementation Details

With the migration process the 3.2.0 registry data getting added to 4.2.0 and these custom common policies resides in 4.2.0 registry as well. So the client is impletemented to read regsitry policies from the path /_system/governance/apimgt/customsequences/ and store in AM_DB relavant databases.

# How to use

1. Build the project using `mvn clean install`
2. Add the bundle to `<APIM_HOME>/repository/components/dropins`
3. Start the APIM using command `sh api-manager.sh -Dmigrate`
