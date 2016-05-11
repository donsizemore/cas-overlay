/**
 * 
 */
package org.iplantc.cas.persondir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.Validate;
import org.jasig.services.persondir.IPersonAttributeDao;
import org.jasig.services.persondir.IPersonAttributes;
import org.jasig.services.persondir.support.MultivaluedPersonAttributeUtils;
import org.jasig.services.persondir.support.NamedPersonImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attribute DAO that resolves entitlements based on Spring configuration
 * 
 * @author Mike Conway - DICE
 *
 */
public class ConfigurableEntitlementResolver implements IPersonAttributeDao {

	/** The name of the query attribute used for the username. */
	public static final String USERNAME_ATTRIBUTE = "username";

	/** Logger instance. */
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	/**
	 * User ids that will trigger adding of entitlement attribs
	 */
	private List<String> configuredUserIds = new ArrayList<String>();

	/**
	 * Configured entitelment attributes that are returned in response to the
	 * configured users
	 */
	private List<String> configuredEntitlements = new ArrayList<String>();

	/** Maps LDAP attributes to user attributes. */
	@NotNull
	private Map<String, Set<String>> resultAttributeMapping;

	/** The names of the attributes that can be obtained. */
	private Set<String> possibleUserAttributes;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jasig.services.persondir.IPersonAttributeDao#
	 * getAvailableQueryAttributes ()
	 */
	@Override
	public Set<String> getAvailableQueryAttributes() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jasig.services.persondir.IPersonAttributeDao#getMultivaluedUserAttributes
	 * (java.util.Map)
	 */
	@Override
	public Map<String, List<Object>> getMultivaluedUserAttributes(
			Map<String, List<Object>> arg0) {
		throw new UnsupportedOperationException("not implemented");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jasig.services.persondir.IPersonAttributeDao#getMultivaluedUserAttributes
	 * (java.lang.String)
	 */
	@Override
	public Map<String, List<Object>> getMultivaluedUserAttributes(String uid) {
		throw new UnsupportedOperationException("not implemented");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jasig.services.persondir.IPersonAttributeDao#getPeople(java.util.Map)
	 */
	@Override
	public Set<IPersonAttributes> getPeople(Map<String, Object> queryAttributes) {
		validateQueryAttributesMap(queryAttributes);
		return getPeople(queryAttributes.get(USERNAME_ATTRIBUTE).toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jasig.services.persondir.IPersonAttributeDao#
	 * getPeopleWithMultivaluedAttributes(java.util.Map)
	 */
	@Override
	public Set<IPersonAttributes> getPeopleWithMultivaluedAttributes(
			Map<String, List<Object>> queryAttributes) {
		// Get the username to search for.
		final List<Object> usernames = queryAttributes.get(USERNAME_ATTRIBUTE);
		if (usernames == null || usernames.size() == 0) {
			throw new RuntimeException("no username provided");
		}
		if (usernames.size() > 1) {
			throw new RuntimeException(
					"queries for multiple usernames are not supported");
		}
		final String uid = usernames.get(0).toString();

		return getPeople(uid);
	}

	/**
	 * Gets all matching records for a user ID.
	 *
	 * @param uid
	 *            the user ID.
	 * @return the set of matching users.
	 */
	private Set<IPersonAttributes> getPeople(final String uid) {
		final Set<IPersonAttributes> result = new HashSet<IPersonAttributes>();
		final IPersonAttributes person = getPerson(uid);
		if (person != null) {
			result.add(person);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jasig.services.persondir.IPersonAttributeDao#getPerson(java.lang.
	 * String)
	 */
	@Override
	public IPersonAttributes getPerson(String uid) {
		logger.debug("getPerson()");
		Validate.notNull(uid, "uid may not be null.");
		logger.debug("uid:{}", uid);

		logger.debug("configuredEntitlements at time of getPerson(): {}",
				this.configuredEntitlements);
		// Build the map of attributes for the user.
		final Map<String, List<Object>> attributes = new HashMap<String, List<Object>>();
		final List<Object> entitlements = new ArrayList<Object>();

		boolean matched = false;
		for (String configuredUid : this.configuredUserIds) {
			if (configuredUid.equals(uid)) {
				matched = true;
				logger.info("matched uid:{}", uid);
				break;
			}
		}

		if (matched) {
			logger.info("matched!!");
			for (String attribute : this.configuredEntitlements) {
				logger.info("adding value:{} to entitlements", attribute);
				entitlements.add(attribute);
			}
		}

		attributes.put("cn", entitlements);
		logger.info("entitlements:{}", entitlements);
		return attributesFromSearchResult(uid, attributes);

	}
	
	/**
	 * Converts an LDAP search result to an instance of IPersonAttributes.
	 *
	 * @param uid
	 *            the user ID.
	 * @param result
	 *            the LDAP search result.
	 * @return the IPersonAttributes instance.
	 */
	private NamedPersonImpl attributesFromSearchResult(final String uid,
			final Map<String, List<Object>> result) {
		logger.debug("found {} results for user {}", result.size(), uid);

		// Quit early if there are no results.
		if (result.size() == 0) {
			return null;
		}

		// Build the map of attributes for the user.
		final Map<String, List<Object>> attributes = new HashMap<String, List<Object>>();
		String mappedKey = null;
		boolean matched = false;
		for (String key : result.keySet()) {
			logger.debug("have result key:{}", key);
			
			for (String resultKey : resultAttributeMapping.keySet()) {
				if (resultKey.equals(key)) {
					for (final String attributeName : resultAttributeMapping
							.get(resultKey)) {
						logger.info("mapping: {}", key);
						logger.info("to result keys:{}", attributeName);
						attributes.put(attributeName, result.get(key));
					}
				}
			}
		}
		

		logger.debug("Attributes: {}", attributes);
		return new NamedPersonImpl(uid, attributes);
		
	}


	@Override
	public Set<String> getPossibleUserAttributeNames() {
		return possibleUserAttributes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jasig.services.persondir.IPersonAttributeDao#getUserAttributes(java
	 * .util.Map)
	 */
	@Override
	public Map<String, Object> getUserAttributes(Map<String, Object> arg0) {
		throw new UnsupportedOperationException("not implemented");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jasig.services.persondir.IPersonAttributeDao#getUserAttributes(java
	 * .lang.String)
	 */
	@Override
	public Map<String, Object> getUserAttributes(String arg0) {
		throw new UnsupportedOperationException("not implemented");

	}

	/**
	 * Validates the common portions of the query attributes map.
	 *
	 * @param queryAttributes
	 *            the query attributes map.
	 */
	private void validateQueryAttributesMap(final Map<String, ?> queryAttributes) {
		if (queryAttributes.size() != 1) {
			throw new RuntimeException(
					"queries for multiple attributes are not supported");
		}
		if (!queryAttributes.containsKey(USERNAME_ATTRIBUTE)) {
			throw new RuntimeException("no username provided");
		}
	}

	public List<String> getConfiguredUserIds() {
		return configuredUserIds;
	}

	public void setConfiguredUserIds(List<String> configuredUserIds) {
		this.configuredUserIds = configuredUserIds;
		logger.info("configured user ids:{}", configuredUserIds);
	}

	public List<String> getConfiguredEntitlements() {
		return configuredEntitlements;
	}

	public void setConfiguredEntitlements(List<String> configuredEntitlements) {
		this.configuredEntitlements = configuredEntitlements;
		logger.info("configuredEntitlements:{}", configuredEntitlements);
	}

	/**
	 * @param resultAttributeMapping
	 *            maps LDAP attributes to user attributes.
	 */
	public void setResultAttributeMapping(
			final Map<String, ?> resultAttributeMapping) {
		this.resultAttributeMapping = parseAttributeMapping(resultAttributeMapping);
		this.possibleUserAttributes = determinePossibleAttributeNames(this.resultAttributeMapping);
	}

	/**
	 * Determines the attribute names that can possibly be retrieved by this
	 * object.
	 *
	 * @param resultAttributeMapping
	 *            the configured result attribute mapping.
	 * @return the set of possible attribute names.
	 */
	private Set<String> determinePossibleAttributeNames(
			final Map<String, Set<String>> resultAttributeMapping) {
		final Collection<String> attributeNames = MultivaluedPersonAttributeUtils
				.flattenCollection(resultAttributeMapping.values());
		return Collections.unmodifiableSet(new HashSet<String>(attributeNames));
	}

	/**
	 * Parses the result attribute mapping expected by the setter into the
	 * format that this class needs.
	 *
	 * @param resultAttributeMapping
	 *            the configured result attribute mapping.
	 * @return the parsed result attribute mapping.
	 */
	private Map<String, Set<String>> parseAttributeMapping(
			final Map<String, ?> resultAttributeMapping) {
		final Map<String, Set<String>> parsedAttributeMapping = MultivaluedPersonAttributeUtils
				.parseAttributeToAttributeMapping(resultAttributeMapping);

		// The result attribute mapping may not contain any empty keys.
		if (parsedAttributeMapping.containsKey("")) {
			final String msg = "The map from attribute names to attributes must not contain empty keys.";
			throw new IllegalArgumentException(msg);
		}

		return parsedAttributeMapping;
	}

}
