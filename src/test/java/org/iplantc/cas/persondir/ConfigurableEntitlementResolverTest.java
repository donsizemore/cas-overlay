package org.iplantc.cas.persondir;

import java.util.ArrayList;
import java.util.List;

import org.jasig.services.persondir.IPersonAttributes;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class ConfigurableEntitlementResolverTest {

	@Ignore
	public void testGetAdminGroupsGivenUser() {

		String testuser1 = "testuser1";
		String testuser2 = "testuser2";
		String entitlement1 = "entitlement1";
		String entitlement2 = "entitlement2";

		List<String> userNames = new ArrayList<String>();
		userNames.add(testuser1);
		userNames.add(testuser2);

		List<String> entitlements = new ArrayList<String>();
		entitlements.add(entitlement1);
		entitlements.add(entitlement2);

		ConfigurableEntitlementResolver resolver = new ConfigurableEntitlementResolver();
		resolver.setConfiguredEntitlements(entitlements);
		resolver.setConfiguredUserIds(userNames);
		IPersonAttributes actual = resolver.getPerson(testuser1);
		Assert.assertNotNull("Null personAttributes returned", actual);
		Assert.assertFalse("no attributes", actual.getAttributes().isEmpty());

	}

}
