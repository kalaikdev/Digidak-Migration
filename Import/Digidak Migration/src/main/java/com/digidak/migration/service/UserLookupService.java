package com.digidak.migration.service;

import com.digidak.migration.repository.RealSessionManager;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.DfQuery;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for resolving user display names to login names
 * Handles user lookup from Documentum dm_user table with caching and normalization
 */
public class UserLookupService {
    private static final Logger logger = LogManager.getLogger(UserLookupService.class);

    private RealSessionManager sessionManager;
    private Map<String, String> userLoginCache; // displayName -> loginName
    private Set<String> notFoundUsers; // Track users not found

    public UserLookupService(RealSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.userLoginCache = new ConcurrentHashMap<>();
        this.notFoundUsers = ConcurrentHashMap.newKeySet();
    }

    /**
     * Resolve display name to login name
     * Returns null if user not found
     */
    public String resolveUserLogin(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }

        displayName = displayName.trim();

        // Check cache first
        if (userLoginCache.containsKey(displayName)) {
            return userLoginCache.get(displayName);
        }

        // Check if already marked as not found
        if (notFoundUsers.contains(displayName)) {
            return null;
        }

        IDfSession session = null;
        try {
            session = sessionManager.getSession();

            // Try exact match on user_name
            String loginName = queryUserByDisplayName(session, displayName);

            if (loginName == null) {
                // Try normalized variations
                loginName = tryNormalizedUserLookup(session, displayName);
            }

            if (loginName != null) {
                userLoginCache.put(displayName, loginName);
                logger.debug("Resolved user '{}' to login '{}'", displayName, loginName);
                return loginName;
            } else {
                notFoundUsers.add(displayName);
                logger.warn("User not found in Documentum: '{}'", displayName);
                return null;
            }

        } catch (Exception e) {
            logger.error("Error resolving user '{}': {}", displayName, e.getMessage());
            return null;
        } finally {
            if (session != null) {
                sessionManager.releaseSession(session);
            }
        }
    }

    /**
     * Batch resolve multiple users (optimization)
     */
    public Map<String, String> batchResolveUsers(List<String> displayNames) {
        System.out.println("=== USER LOOKUP === batchResolveUsers called with " + (displayNames != null ? displayNames.size() : 0) + " display names: " + displayNames);
        logger.info("=== USER LOOKUP === batchResolveUsers called with {} display names: {}",
                   displayNames != null ? displayNames.size() : 0, displayNames);

        Map<String, String> results = new HashMap<>();

        if (displayNames == null || displayNames.isEmpty()) {
            System.out.println("=== USER LOOKUP === No display names provided");
            logger.warn("=== USER LOOKUP === No display names provided");
            return results;
        }

        // Filter out cached and not-found users
        List<String> toQuery = new ArrayList<>();
        for (String displayName : displayNames) {
            if (displayName == null || displayName.trim().isEmpty()) {
                continue;
            }

            String trimmed = displayName.trim();
            if (userLoginCache.containsKey(trimmed)) {
                results.put(trimmed, userLoginCache.get(trimmed));
                logger.info("=== USER LOOKUP === Found in cache: '{}' -> '{}'", trimmed, userLoginCache.get(trimmed));
            } else if (!notFoundUsers.contains(trimmed)) {
                toQuery.add(trimmed);
                logger.info("=== USER LOOKUP === Need to query: '{}'", trimmed);
            } else {
                logger.info("=== USER LOOKUP === Previously marked as not found: '{}'", trimmed);
            }
        }

        if (toQuery.isEmpty()) {
            logger.info("=== USER LOOKUP === No users need querying, returning {} cached results", results.size());
            return results;
        }

        logger.info("=== USER LOOKUP === Need to query {} users from dm_user", toQuery.size());

        IDfSession session = null;
        try {
            session = sessionManager.getSession();

            // Build IN clause query (max 50 users per query)
            for (int i = 0; i < toQuery.size(); i += 50) {
                int end = Math.min(i + 50, toQuery.size());
                List<String> batch = toQuery.subList(i, end);

                logger.info("=== USER LOOKUP === Querying batch of {} users", batch.size());
                Map<String, String> batchResults = queryUsersInBatch(session, batch);
                logger.info("=== USER LOOKUP === Batch query returned {} results: {}", batchResults.size(), batchResults);

                results.putAll(batchResults);

                // Cache results - cache both dm_user.user_name and original CSV name
                userLoginCache.putAll(batchResults);

                // Match batch names to query results (case-insensitive) with normalized fallback
                for (String name : batch) {
                    String matchedLogin = batchResults.get(name);
                    if (matchedLogin == null) {
                        // Try case-insensitive match against dm_user.user_name keys
                        for (Map.Entry<String, String> entry : batchResults.entrySet()) {
                            if (entry.getKey().equalsIgnoreCase(name)) {
                                matchedLogin = entry.getValue();
                                userLoginCache.put(name, matchedLogin);
                                logger.info("=== USER LOOKUP === Matched '{}' to dm_user '{}' (case-insensitive)",
                                           name, entry.getKey());
                                break;
                            }
                        }
                    }
                    if (matchedLogin == null) {
                        // Fallback: try individual resolution with normalized name variations
                        logger.info("=== USER LOOKUP === Trying normalized lookup for '{}'", name);
                        String resolvedLogin = queryUserByDisplayName(session, name);
                        if (resolvedLogin != null) {
                            // queryUserByDisplayName matched by user_name, so CSV name IS the user_name
                            matchedLogin = resolvedLogin;
                            results.put(name, resolvedLogin);
                            userLoginCache.put(name, resolvedLogin);
                            logger.info("=== USER LOOKUP === Resolved '{}' via exact display name to '{}'",
                                       name, resolvedLogin);
                        } else {
                            // Try normalized variations - need user_name for ACL grant()
                            String[] resolved = tryNormalizedUserLookupFull(session, name);
                            if (resolved != null) {
                                String userName = resolved[0];  // dm_user.user_name
                                String loginName = resolved[1]; // dm_user.user_login_name
                                matchedLogin = loginName;
                                // Use dm_user.user_name as key (required for ACL grant())
                                results.put(userName, loginName);
                                userLoginCache.put(name, loginName);
                                userLoginCache.put(userName, loginName);
                                logger.info("=== USER LOOKUP === Resolved '{}' via normalized lookup to user_name='{}', login='{}'",
                                           name, userName, loginName);
                            }
                        }
                    }
                    if (matchedLogin == null) {
                        notFoundUsers.add(name);
                        logger.warn("=== USER LOOKUP === User '{}' NOT FOUND in dm_user", name);
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("=== USER LOOKUP === EXCEPTION in batch user resolution: " + e.getMessage());
            e.printStackTrace();
            logger.error("=== USER LOOKUP === EXCEPTION in batch user resolution: {}", e.getMessage(), e);
        } finally {
            if (session != null) {
                sessionManager.releaseSession(session);
            }
        }

        System.out.println("=== USER LOOKUP === batchResolveUsers returning " + results.size() + " total results: " + results);
        logger.info("=== USER LOOKUP === batchResolveUsers returning {} total results: {}", results.size(), results);
        return results;
    }

    /**
     * Query user by display name (exact match)
     */
    private String queryUserByDisplayName(IDfSession session, String displayName) throws Exception {
        String dql = "SELECT user_login_name FROM dm_user WHERE user_name = '"
                     + escapeDql(displayName) + "'";

        IDfQuery query = new DfQuery();
        query.setDQL(dql);

        IDfCollection collection = query.execute(session, IDfQuery.DF_READ_QUERY);
        try {
            if (collection.next()) {
                return collection.getString("user_login_name");
            }
        } finally {
            collection.close();
        }

        return null;
    }

    /**
     * Query users in batch using IN clause
     */
    private Map<String, String> queryUsersInBatch(IDfSession session, List<String> displayNames) throws Exception {
        Map<String, String> results = new HashMap<>();

        // Build IN clause
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < displayNames.size(); i++) {
            if (i > 0) inClause.append(", ");
            inClause.append("'").append(escapeDql(displayNames.get(i))).append("'");
        }

        String dql = "SELECT user_name, user_login_name FROM dm_user WHERE user_name IN ("
                     + inClause + ")";

        IDfQuery query = new DfQuery();
        query.setDQL(dql);

        IDfCollection collection = query.execute(session, IDfQuery.DF_READ_QUERY);
        try {
            while (collection.next()) {
                String userName = collection.getString("user_name");
                String loginName = collection.getString("user_login_name");
                results.put(userName, loginName);
            }
        } finally {
            collection.close();
        }

        return results;
    }

    /**
     * Try normalized user lookup with common naming patterns
     */
    private String tryNormalizedUserLookup(IDfSession session, String displayName) {
        // Try common variations:
        // "E Prathap" -> "eprathap", "e.prathap", "prathape"
        // "Smt Shaban Banu" -> "shabanbanu", "s.banu"

        List<String> variations = generateLoginVariations(displayName);

        for (String variation : variations) {
            try {
                String dql = "SELECT user_name, user_login_name FROM dm_user WHERE LOWER(user_login_name) = '"
                            + variation.toLowerCase() + "'";

                IDfQuery query = new DfQuery();
                query.setDQL(dql);

                IDfCollection collection = query.execute(session, IDfQuery.DF_READ_QUERY);
                try {
                    if (collection.next()) {
                        String userName = collection.getString("user_name");
                        String loginName = collection.getString("user_login_name");
                        logger.debug("Resolved '{}' to user_name='{}', login='{}' via normalized lookup",
                                    displayName, userName, loginName);
                        return loginName;
                    }
                } finally {
                    collection.close();
                }
            } catch (Exception e) {
                // Continue to next variation
                logger.trace("Failed normalized lookup for variation '{}': {}", variation, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Try normalized user lookup returning both user_name and user_login_name.
     * Returns String[]{user_name, user_login_name} or null if not found.
     */
    private String[] tryNormalizedUserLookupFull(IDfSession session, String displayName) {
        List<String> variations = generateLoginVariations(displayName);

        for (String variation : variations) {
            try {
                String dql = "SELECT user_name, user_login_name FROM dm_user WHERE LOWER(user_login_name) = '"
                            + variation.toLowerCase() + "'";

                IDfQuery query = new DfQuery();
                query.setDQL(dql);

                IDfCollection collection = query.execute(session, IDfQuery.DF_READ_QUERY);
                try {
                    if (collection.next()) {
                        String userName = collection.getString("user_name");
                        String loginName = collection.getString("user_login_name");
                        logger.debug("Resolved '{}' to user_name='{}', login='{}' via normalized lookup",
                                    displayName, userName, loginName);
                        return new String[]{userName, loginName};
                    }
                } finally {
                    collection.close();
                }
            } catch (Exception e) {
                logger.trace("Failed normalized lookup for variation '{}': {}", variation, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Generate login name variations from display name
     */
    private List<String> generateLoginVariations(String displayName) {
        List<String> variations = new ArrayList<>();

        // Remove titles and prefixes
        String cleaned = displayName.replaceAll("(?i)^(Shri|Smt|Ms\\.|Mr\\.|Dr\\.)\\s+", "").trim();

        String[] parts = cleaned.split("\\s+");

        if (parts.length == 2) {
            // firstname lastname
            String first = parts[0];
            String last = parts[1];

            variations.add(first.toLowerCase() + last.toLowerCase()); // firstlast
            variations.add(first.toLowerCase() + "." + last.toLowerCase()); // first.last
            variations.add(first.charAt(0) + last.toLowerCase()); // flast
            variations.add(last.toLowerCase() + first.charAt(0)); // lastf
            variations.add(first.substring(0, Math.min(1, first.length())).toLowerCase() +
                          last.toLowerCase()); // flast (safe version)
        } else if (parts.length > 2) {
            // Multiple parts - use first and last
            String first = parts[0];
            String last = parts[parts.length - 1];

            variations.add(first.toLowerCase() + last.toLowerCase());
            variations.add(first.toLowerCase() + "." + last.toLowerCase());
            variations.add(first.charAt(0) + last.toLowerCase());
        } else if (parts.length == 1) {
            // Single name
            variations.add(cleaned.toLowerCase());
        }

        return variations;
    }

    /**
     * Escape single quotes in DQL
     */
    private String escapeDql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    /**
     * Get cached user mappings
     */
    public Map<String, String> getUserLoginCache() {
        return new HashMap<>(userLoginCache);
    }

    /**
     * Get users that were not found
     */
    public Set<String> getNotFoundUsers() {
        return new HashSet<>(notFoundUsers);
    }

    /**
     * Clear all caches
     */
    public void clearCache() {
        userLoginCache.clear();
        notFoundUsers.clear();
    }
}
