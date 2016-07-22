/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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

import javax.annotation.PostConstruct;
import javax.naming.directory.SearchControls;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.Validate;
import org.jasig.cas.util.LdapUtils;
import org.jasig.services.persondir.IPersonAttributeDao;
import org.jasig.services.persondir.IPersonAttributes;
import org.jasig.services.persondir.support.MultivaluedPersonAttributeUtils;
import org.jasig.services.persondir.support.NamedPersonImpl;
import org.ldaptive.Connection;
import org.ldaptive.ConnectionFactory;
import org.ldaptive.LdapAttribute;
import org.ldaptive.LdapEntry;
import org.ldaptive.LdapException;
import org.ldaptive.SearchFilter;
import org.ldaptive.SearchOperation;
import org.ldaptive.SearchRequest;
import org.ldaptive.SearchResult;
import org.ldaptive.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of IPersonAttributeDao that combines multiple LDAP records
 * into a single instance of IPersonAttributes. The original purpose of this
 * class was to obtain the list of groups that a user belongs to in an LDAP
 * directory that didn't have a reverse lookup for group membership, but it can
 * be useful in any case where multiple LDAP records must be combined into a set
 * of attributes for a single user.
 *
 * @author Dennis Roberts
 * @since 4.1.0
 */
public final class LdapMultirecordPersonAttributeDao implements
		IPersonAttributeDao {

	/** The name of the query attribute used for the username. */
	public static final String USERNAME_ATTRIBUTE = "username";

	/** Logger instance. */
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/** LDAP connection factory. */
	@NotNull
	private ConnectionFactory connectionFactory;

	/** The base distinguished name for the LDAP search. */
	@NotNull
	private String baseDN;

	/** The search controls for the LDAP search. */
	@NotNull
	private SearchControls searchControls;

	/** The scope of the LDAP search. */
	private SearchScope searchScope;

	/** The filter to use when performing the search. */
	@NotNull
	private String searchFilter;

	/** The attributes to fetch from the directory. */
	private String[] attributes;

	/** Maps LDAP attributes to user attributes. */
	@NotNull
	private Map<String, Set<String>> resultAttributeMapping;

	/** The names of the attributes that can be obtained. */
	private Set<String> possibleUserAttributes;

	/**
	 * @param connectionFactory
	 *            the LDAP connection factory.
	 */
	public void setConnectionFactory(final ConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	/**
	 * @param baseDN
	 *            the base distinguished name for the LDAP search.
	 */
	public void setBaseDN(final String baseDN) {
		this.baseDN = baseDN;
	}

	/**
	 * @param searchControls
	 *            the search controls for the LDAP search.
	 */
	public void setSearchControls(final SearchControls searchControls) {
		this.searchControls = searchControls;
	}

	/**
	 * @param searchFilter
	 *            the filter to use when performing the search.
	 */
	public void setSearchFilter(final String searchFilter) {
		this.searchFilter = searchFilter;
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

	/** Initializes the object after the properties are set. */
	@PostConstruct
	public void initialize() {
		searchScope = determineSearchScope();
		attributes = resultAttributeMapping.keySet().toArray(
				new String[resultAttributeMapping.size()]);
	}

	/**
	 * Extracts the search scope from the search controls. The search controls
	 * object stores the search scope as an integer, so we have to convert it to
	 * the actual enumeration value.
	 *
	 * @return the search scope to use.
	 */
	private SearchScope determineSearchScope() {
		final int scopeOrdinal = searchControls.getSearchScope();
		final SearchScope[] values = SearchScope.values();
		return scopeOrdinal >= 0 && scopeOrdinal < values.length ? values[scopeOrdinal]
				: null;
	}

	public IPersonAttributes getPerson(final String uid) {
		Validate.notNull(uid, "uid may not be null.");

		final Connection connection = getConnection();
		try {
			return attributesFromSearchResult(uid,
					performSearch(connection, buildQuery(uid)));
		} finally {
			LdapUtils.closeConnection(connection);
		}
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
	private IPersonAttributes attributesFromSearchResult(final String uid,
			final SearchResult result) {
		logger.debug("found {} results for user {}", result.size(), uid);

		// Quit early if there are no results.
		if (result.size() == 0) {
			return null;
		}

		// Build the map of attributes for the user.
		final Map<String, List<Object>> attributes = new HashMap<String, List<Object>>();
		for (final LdapEntry entry : result.getEntries()) {
			for (final String ldapAttributeName : resultAttributeMapping
					.keySet()) {
				final LdapAttribute attribute = entry
						.getAttribute(ldapAttributeName);
				if (attribute != null) {
					logger.debug("adding attribute {} for user {}",
							ldapAttributeName, uid);
					final List<Object> values = new ArrayList<Object>(
							attribute.getStringValues());
					for (final String attributeName : resultAttributeMapping
							.get(ldapAttributeName)) {
						addAttribute(attributes, attributeName, values);
					}
				}
			}
		}

		logger.debug("Attributes: {}", attributes);
		return new NamedPersonImpl(uid, attributes);
	}

	/**
	 * Adds values obtained from an LDAP attribute to the growing map of user
	 * attributes.
	 *
	 * @param attributes
	 *            the map of user attributes.
	 * @param attributeName
	 *            the name of the user attribute to add the values to.
	 * @param values
	 *            the values from the LDAP attribute.
	 */
	private void addAttribute(final Map<String, List<Object>> attributes,
			final String attributeName, final List<Object> values) {
		if (attributes.containsKey(attributeName)) {
			attributes.get(attributeName).addAll(values);
		} else {
			attributes.put(attributeName, values);
		}
	}

	/**
	 * @return an LDAP connection.
	 */
	private Connection getConnection() {
		try {
			return connectionFactory.getConnection();
		} catch (final LdapException e) {
			throw new RuntimeException("unable to obtain an LDAP connection", e);
		}
	}

	/**
	 * Obtains a new LDAP connection.
	 *
	 * @param uid
	 *            the user ID.
	 * @return the LDAP connection.
	 */
	private SearchFilter buildQuery(final String uid) {
		final SearchFilter query = new SearchFilter(searchFilter);
		query.setParameter(0, uid);
		return query;
	}

	/**
	 * Performs the LDAP search.
	 *
	 * @param connection
	 *            the LDAP connection to use for the search.
	 * @param filter
	 *            the LDAP search filter.
	 * @return the search result.
	 */
	private SearchResult performSearch(final Connection connection,
			final SearchFilter filter) {
		try {
			return new SearchOperation(connection).execute(
					createRequest(filter)).getResult();
		} catch (final LdapException e) {
			throw new RuntimeException("unable to execute the LDAP query "
					+ filter, e);
		}
	}

	/**
	 * Creates a search request from a search filter.
	 *
	 * @param filter
	 *            LDAP search filter.
	 *
	 * @return ldaptive search request.
	 */
	private SearchRequest createRequest(final SearchFilter filter) {
		final SearchRequest request = new SearchRequest();
		request.setBaseDn(baseDN);
		request.setSearchFilter(filter);
		request.setReturnAttributes(attributes);
		request.setSearchScope(searchScope);
		request.setSizeLimit(searchControls.getCountLimit());
		request.setTimeLimit(searchControls.getTimeLimit());
		return request;
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

	@Override
	public Set<IPersonAttributes> getPeople(
			final Map<String, Object> queryAttributes) {
		validateQueryAttributesMap(queryAttributes);
		return getPeople(queryAttributes.get(USERNAME_ATTRIBUTE).toString());
	}

	@Override
	public Set<IPersonAttributes> getPeopleWithMultivaluedAttributes(
			final Map<String, List<Object>> queryAttributes) {
		validateQueryAttributesMap(queryAttributes);

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

	@Override
	public Set<String> getPossibleUserAttributeNames() {
		return possibleUserAttributes;
	}

	@Override
	public Set<String> getAvailableQueryAttributes() {
		return null;
	}

	@Override
	public Map<String, List<Object>> getMultivaluedUserAttributes(
			final Map<String, List<Object>> queryAttributes) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Map<String, List<Object>> getMultivaluedUserAttributes(final String s) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Map<String, Object> getUserAttributes(
			final Map<String, Object> stringObjectMap) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Map<String, Object> getUserAttributes(final String s) {
		throw new UnsupportedOperationException("not implemented");
	}
}
