/**
 * Copyright (c) 2015 Netflix, Inc.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.msl.entityauth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.netflix.msl.MslCryptoException;
import com.netflix.msl.MslEncodingException;
import com.netflix.msl.MslEntityAuthException;
import com.netflix.msl.MslError;
import com.netflix.msl.test.ExpectedMslException;
import com.netflix.msl.util.JsonUtils;
import com.netflix.msl.util.MockMslContext;
import com.netflix.msl.util.MslContext;

/**
 * Unauthenticated suffixed entity authentication data unit tests.
 * 
 * @author Wesley Miaw <wmiaw@netflix.com>
 */
public class UnauthenticatedSuffixedAuthenticationDataTest {
    /** JSON key entity authentication scheme. */
    private static final String KEY_SCHEME = "scheme";
    /** JSON key entity authentication data. */
    private static final String KEY_AUTHDATA = "authdata";
    
    /** JSON key entity root. */
    private static final String KEY_ROOT = "root";
    /** JSON key entity suffix. */
    private static final String KEY_SUFFIX = "suffix";
    
    /** Identity concatenation character. */
    private static final String CONCAT_CHAR = ".";

    @Rule
    public ExpectedMslException thrown = ExpectedMslException.none();
    
    private static final String ROOT = "root";
    private static final String SUFFIX = "suffix";
    
    @BeforeClass
    public static void setup() throws IOException, MslEncodingException, MslCryptoException {
        ctx = new MockMslContext(EntityAuthenticationScheme.NONE_SUFFIXED, false);
    }
    
    @AfterClass
    public static void teardown() {
        ctx = null;
    }
    
    @Test
    public void ctors() throws MslEncodingException, JSONException {
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX);
        assertEquals(ROOT + CONCAT_CHAR + SUFFIX, data.getIdentity());
        assertEquals(ROOT, data.getRoot());
        assertEquals(SUFFIX, data.getSuffix());
        assertEquals(EntityAuthenticationScheme.NONE_SUFFIXED, data.getScheme());
        final JSONObject authdata = data.getAuthData();
        assertNotNull(authdata);
        final String jsonString = data.toJSONString();
        assertNotNull(jsonString);
        
        final UnauthenticatedSuffixedAuthenticationData joData = new UnauthenticatedSuffixedAuthenticationData(authdata);
        assertEquals(data.getIdentity(), joData.getIdentity());
        assertEquals(data.getRoot(), joData.getRoot());
        assertEquals(data.getSuffix(), joData.getSuffix());
        assertEquals(data.getScheme(), joData.getScheme());
        final JSONObject joAuthdata = joData.getAuthData();
        assertNotNull(joAuthdata);
        assertTrue(JsonUtils.equals(authdata, joAuthdata));
        final String joJsonString = joData.toJSONString();
        assertNotNull(joJsonString);
        assertEquals(jsonString, joJsonString);
    }
    
    @Test
    public void jsonString() throws JSONException {
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX);
        final JSONObject jo = new JSONObject(data.toJSONString());
        assertEquals(EntityAuthenticationScheme.NONE_SUFFIXED.toString(), jo.getString(KEY_SCHEME));
        final JSONObject authdata = jo.getJSONObject(KEY_AUTHDATA);
        assertEquals(ROOT, authdata.getString(KEY_ROOT));
        assertEquals(SUFFIX, authdata.getString(KEY_SUFFIX));
    }
    
    @Test
    public void create() throws JSONException, MslEntityAuthException, MslEncodingException, MslCryptoException {
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX);
        final String jsonString = data.toJSONString();
        final JSONObject jo = new JSONObject(jsonString);
        final EntityAuthenticationData entitydata = EntityAuthenticationData.create(ctx, jo);
        assertNotNull(entitydata);
        assertTrue(entitydata instanceof UnauthenticatedSuffixedAuthenticationData);
        
        final UnauthenticatedSuffixedAuthenticationData joData = (UnauthenticatedSuffixedAuthenticationData)entitydata;
        assertEquals(data.getIdentity(), joData.getIdentity());
        assertEquals(data.getRoot(), joData.getRoot());
        assertEquals(data.getSuffix(), joData.getSuffix());
        assertEquals(data.getScheme(), joData.getScheme());
        final JSONObject joAuthdata = joData.getAuthData();
        assertNotNull(joAuthdata);
        assertTrue(JsonUtils.equals(data.getAuthData(), joAuthdata));
        final String joJsonString = joData.toJSONString();
        assertNotNull(joJsonString);
        assertEquals(jsonString, joJsonString);
    }
    
    @Test
    public void missingRoot() throws MslEncodingException {
        thrown.expect(MslEncodingException.class);
        thrown.expectMslError(MslError.JSON_PARSE_ERROR);

        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX);
        final JSONObject authdata = data.getAuthData();
        authdata.remove(KEY_ROOT);
        new UnauthenticatedSuffixedAuthenticationData(authdata);
    }
    
    @Test
    public void missingSuffix() throws MslEncodingException {
        thrown.expect(MslEncodingException.class);
        thrown.expectMslError(MslError.JSON_PARSE_ERROR);

        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX);
        final JSONObject authdata = data.getAuthData();
        authdata.remove(KEY_SUFFIX);
        new UnauthenticatedSuffixedAuthenticationData(authdata);
    }
    
    @Test
    public void equalsRoot() throws MslEncodingException, JSONException, MslEntityAuthException, MslCryptoException {
        final UnauthenticatedSuffixedAuthenticationData dataA = new UnauthenticatedSuffixedAuthenticationData(ROOT + "A", SUFFIX);
        final UnauthenticatedSuffixedAuthenticationData dataB = new UnauthenticatedSuffixedAuthenticationData(ROOT + "B", SUFFIX);
        final EntityAuthenticationData dataA2 = EntityAuthenticationData.create(ctx, new JSONObject(dataA.toJSONString()));
        
        assertTrue(dataA.equals(dataA));
        assertEquals(dataA.hashCode(), dataA.hashCode());
        
        assertFalse(dataA.equals(dataB));
        assertFalse(dataB.equals(dataA));
        assertTrue(dataA.hashCode() != dataB.hashCode());
        
        assertTrue(dataA.equals(dataA2));
        assertTrue(dataA2.equals(dataA));
        assertEquals(dataA.hashCode(), dataA2.hashCode());
    }
    
    @Test
    public void equalsSuffix() throws MslEncodingException, JSONException, MslEntityAuthException, MslCryptoException {
        final UnauthenticatedSuffixedAuthenticationData dataA = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX + "A");
        final UnauthenticatedSuffixedAuthenticationData dataB = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX + "B");
        final EntityAuthenticationData dataA2 = EntityAuthenticationData.create(ctx, new JSONObject(dataA.toJSONString()));
        
        assertTrue(dataA.equals(dataA));
        assertEquals(dataA.hashCode(), dataA.hashCode());
        
        assertFalse(dataA.equals(dataB));
        assertFalse(dataB.equals(dataA));
        assertTrue(dataA.hashCode() != dataB.hashCode());
        
        assertTrue(dataA.equals(dataA2));
        assertTrue(dataA2.equals(dataA));
        assertEquals(dataA.hashCode(), dataA2.hashCode());
    }
    
    @Test
    public void equalsObject() {
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(ROOT, SUFFIX);
        assertFalse(data.equals(null));
        assertFalse(data.equals(ROOT));
        assertTrue(data.hashCode() != ROOT.hashCode());
    }

    /** MSL context. */
    private static MslContext ctx;
}
