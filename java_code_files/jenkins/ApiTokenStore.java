/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.security.apitoken;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.util.Secret;
import jenkins.security.Messages;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Restricted(NoExternalUse.class)
public class ApiTokenStore {
    private static final Logger LOGGER = Logger.getLogger(ApiTokenStore.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    
    private static final Comparator<HashedToken> SORT_BY_LOWERCASED_NAME =
            Comparator.comparing(hashedToken -> hashedToken.getName().toLowerCase(Locale.ENGLISH));
    
    private static final int TOKEN_LENGTH_V2 = 34;
    /** two hex characters, avoid starting with 0 to avoid troubles */
    private static final String LEGACY_VERSION = "10";
    private static final String HASH_VERSION = "11";
    
    private static final String HASH_ALGORITHM = "SHA-256";
    
    private List<HashedToken> tokenList;
    
    public ApiTokenStore() {
        this.init();
    }
    
    private Object readResolve() {
        this.init();
        return this;
    }
    
    private void init() {
        if (this.tokenList == null) {
            this.tokenList = new ArrayList<>();
        }
    }
    
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    public synchronized @Nonnull Collection<HashedToken> getTokenListSortedByName() {
        return tokenList.stream()
                .sorted(SORT_BY_LOWERCASED_NAME)
                .collect(Collectors.toList());
    }
    
    private void addToken(HashedToken token) {
        this.tokenList.add(token);
    }
    
    /**
     * Defensive approach to avoid involuntary change since the UUIDs are generated at startup only for UI
     * and so between restart they change
     */
    public synchronized void reconfigure(@Nonnull Map<String, JSONObject> tokenStoreDataMap) {
        tokenList.forEach(hashedToken -> {
            JSONObject receivedTokenData = tokenStoreDataMap.get(hashedToken.uuid);
            if (receivedTokenData == null) {
                LOGGER.log(Level.INFO, "No token received for {0}", hashedToken.uuid);
                return;
            }
            
            String name = receivedTokenData.getString("tokenName");
            if (StringUtils.isBlank(name)) {
                LOGGER.log(Level.INFO, "Empty name received for {0}, we do not care about it", hashedToken.uuid);
                return;
            }
            
            hashedToken.setName(name);
        });
    }
    
    /**
     * Remove the legacy token present and generate a new one using the given secret.
     */
    public synchronized void regenerateTokenFromLegacy(@Nonnull Secret newLegacyApiToken) {
        deleteAllLegacyAndGenerateNewOne(newLegacyApiToken, false);
    }
    
    /**
     * Same as {@link #regenerateTokenFromLegacy(Secret)} but only applied if there is an existing legacy token. 
     * <p>
     * Otherwise, no effect.
     */
    public synchronized void regenerateTokenFromLegacyIfRequired(@Nonnull Secret newLegacyApiToken) {
        if(tokenList.stream().noneMatch(HashedToken::isLegacy)){
            deleteAllLegacyAndGenerateNewOne(newLegacyApiToken, true);
        }
    }
    
    private void deleteAllLegacyAndGenerateNewOne(@Nonnull Secret newLegacyApiToken, boolean migrationFromExistingLegacy) {
        deleteAllLegacyTokens();
        addLegacyToken(newLegacyApiToken, migrationFromExistingLegacy);
    }
    
    private void deleteAllLegacyTokens() {
        // normally there is only one, but just in case
        tokenList.removeIf(HashedToken::isLegacy);
    }
    
    private void addLegacyToken(@Nonnull Secret legacyToken, boolean migrationFromExistingLegacy) {
        String tokenUserUseNormally = Util.getDigestOf(legacyToken.getPlainText());
        
        String secretValueHashed = this.plainSecretToHashInHex(tokenUserUseNormally);
        
        HashValue hashValue = new HashValue(LEGACY_VERSION, secretValueHashed);
        HashedToken token = HashedToken.buildNewFromLegacy(hashValue, migrationFromExistingLegacy);
        
        this.addToken(token);
    }
    
    /**
     * @return {@code null} iff there is no legacy token in the store, otherwise the legacy token is returned
     */
    public synchronized @Nullable HashedToken getLegacyToken(){
        return tokenList.stream()
                .filter(HashedToken::isLegacy)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Create a new token with the given name and return it id and secret value. 
     * Result meant to be sent / displayed and then discarded.
     */
    public synchronized @Nonnull TokenUuidAndPlainValue generateNewToken(@Nonnull String name) {
        // 16x8=128bit worth of randomness, using brute-force you need on average 2^127 tries (~10^37)
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String secretValue = Util.toHexString(random);
        String tokenTheUserWillUse = HASH_VERSION + secretValue;
        assert tokenTheUserWillUse.length() == 2 + 32;
        
        String secretValueHashed = this.plainSecretToHashInHex(secretValue);
        
        HashValue hashValue = new HashValue(HASH_VERSION, secretValueHashed);
        HashedToken token = HashedToken.buildNew(name, hashValue);
        
        this.addToken(token);
        
        return new TokenUuidAndPlainValue(token.uuid, tokenTheUserWillUse);
    }
    
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    private @Nonnull String plainSecretToHashInHex(@Nonnull String secretValueInPlainText) {
        byte[] hashBytes = plainSecretToHashBytes(secretValueInPlainText);
        return Util.toHexString(hashBytes);
    }
    
    private @Nonnull byte[] plainSecretToHashBytes(@Nonnull String secretValueInPlainText) {
        // ascii is sufficient for hex-format
        return hashedBytes(secretValueInPlainText.getBytes(StandardCharsets.US_ASCII));
    }
    
    private @Nonnull byte[] hashedBytes(byte[] tokenBytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("There is no " + HASH_ALGORITHM + " available in this system");
        }
        return digest.digest(tokenBytes);
    }
    
    /**
     * Search in the store if there is a token with the same secret as the one given
     * @return {@code null} iff there is no matching token
     */
    public synchronized @CheckForNull HashedToken findMatchingToken(@Nonnull String token) {
        String plainToken;
        if (isLegacyToken(token)) {
            plainToken = token;
        } else {
            plainToken = getHashOfToken(token);
        }
        
        return searchMatch(plainToken);
    }
    
    /**
     * Determine if the given token was generated by the legacy system or the new one
     */
    private boolean isLegacyToken(@Nonnull String token) {
        return token.length() != TOKEN_LENGTH_V2;
    }
    
    /**
     * Retrieve the hash part of the token
     * @param token assumed the token is not a legacy one and represent the full token (version + hash)
     * @return the hash part
     */
    private @Nonnull String getHashOfToken(@Nonnull String token) {
        /*
         * Structure of the token:
         * 
         * [2: version][32: real token]
         * ------------^^^^^^^^^^^^^^^^
         */
        return token.substring(2);
    }
    
    /**
     * Search in the store if there is a matching token that has the same secret.
     * @return {@code null} iff there is no matching token
     */
    private @CheckForNull HashedToken searchMatch(@Nonnull String plainSecret) {
        byte[] hashedBytes = plainSecretToHashBytes(plainSecret);
        for (HashedToken token : tokenList) {
            if (token.match(hashedBytes)) {
                return token;
            }
        }
        
        return null;
    }
    
    /**
     * Remove a token given its identifier. Effectively make it unusable for future connection.
     * 
     * @param tokenUuid The identifier of the token, could be retrieved directly from the {@link HashedToken#getUuid()}
     * @return the revoked token corresponding to the given {@code tokenUuid} if one was found, otherwise {@code null}
     */
    public synchronized @CheckForNull HashedToken revokeToken(@Nonnull String tokenUuid) {
        for (Iterator<HashedToken> iterator = tokenList.iterator(); iterator.hasNext(); ) {
            HashedToken token = iterator.next();
            if (token.uuid.equals(tokenUuid)) {
                iterator.remove();
                
                return token;
            }
        }
        
        return null;
    }
    
    /**
     * Given a token identifier and a name, the system will try to find a corresponding token and rename it
     * @return {@code true} iff the token was found and the rename was successful
     */
    public synchronized boolean renameToken(@Nonnull String tokenUuid, @Nonnull String newName) {
        for (HashedToken token : tokenList) {
            if (token.uuid.equals(tokenUuid)) {
                token.rename(newName);
                return true;
            }
        }
        
        LOGGER.log(Level.FINER, "The target token for rename does not exist, for uuid = {0}, with desired name = {1}", new Object[]{tokenUuid, newName});
        return false;
    }
    
    @Immutable
    private static class HashValue implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Allow to distinguish tokens from different versions easily to adapt the logic
         */
        private final String version;
        /**
         * Only confidential information in this class. It's a SHA-256 hash stored in hex format
         */
        private final String hash;
        
        private HashValue(String version, String hash) {
            this.version = version;
            this.hash = hash;
        }
    }
    
    /**
     * Contains information about the token and the secret value.
     * It should not be stored as is, but just displayed once to the user and then forget about it.
     */
    @Immutable
    public static class TokenUuidAndPlainValue {
        /**
         * The token identifier to allow manipulation of the token
         */
        public final String tokenUuid;
        
        /**
         * Confidential information, must not be stored.<p>
         * It's meant to be send only one to the user and then only store the hash of this value.
         */
        public final String plainValue;
        
        private TokenUuidAndPlainValue(String tokenUuid, String plainValue) {
            this.tokenUuid = tokenUuid;
            this.plainValue = plainValue;
        }
    }
    
    public static class HashedToken implements Serializable {

        private static final long serialVersionUID = 1L;

        // allow us to rename the token and link the statistics
        private String uuid;
        private String name;
        private Date creationDate;
        
        private HashValue value;
        
        private HashedToken() {
            this.init();
        }
    
        private Object readResolve() {
            this.init();
            return this;
        }
        
        private void init() {
            if(this.uuid == null){
                this.uuid = UUID.randomUUID().toString();
            }
        }
        
        public static @Nonnull HashedToken buildNew(@Nonnull String name, @Nonnull HashValue value) {
            HashedToken result = new HashedToken();
            result.name = name;
            result.creationDate = new Date();
            
            result.value = value;
            
            return result;
        }
    
        public static @Nonnull HashedToken buildNewFromLegacy(@Nonnull HashValue value, boolean migrationFromExistingLegacy) {
            HashedToken result = new HashedToken();
            result.name = Messages.ApiTokenProperty_LegacyTokenName();
            if(migrationFromExistingLegacy){
                // we do not know when the legacy token was created
                result.creationDate = null;
            }else{
                // it comes from a manual action, so we set the creation date to now
                result.creationDate = new Date();
            }
            
            result.value = value;
            
            return result;
        }
        
        public void rename(String newName) {
            this.name = newName;
        }
        
        public boolean match(byte[] hashedBytes) {
            byte[] hashFromHex;
            try {
                hashFromHex = Util.fromHexString(value.hash);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.INFO, "The API token with name=[{0}] is not in hex-format and so cannot be used", name);
                return false;
            }
            
            // String.equals() is not constant-time but this method is. No link between correctness and time spent
            return MessageDigest.isEqual(hashFromHex, hashedBytes);
        }
        
        // used by Jelly view
        public String getName() {
            return name;
        }
        
        // used by Jelly view
        public Date getCreationDate() {
            return creationDate;
        }
        
        // used by Jelly view
        /**
         * Relevant only if the lastUseDate is not null
         */
        public long getNumDaysCreation() {
            return creationDate == null ? 0 : Util.daysElapsedSince(creationDate);
        }
        
        // used by Jelly view
        public String getUuid() {
            return this.uuid;
        }
        
        public boolean isLegacy() {
            return this.value.version.equals(LEGACY_VERSION);
        }
        
        public void setName(String name) {
            this.name = name;
        }
    }
}
