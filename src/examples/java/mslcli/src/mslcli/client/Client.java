/**
 * Copyright (c) 2014 Netflix, Inc.  All rights reserved.
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

package mslcli.client;

import java.io.IOException;
import java.net.URL;
import java.security.KeyPair;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;

import com.netflix.msl.MslException;
import com.netflix.msl.entityauth.PresharedKeyStore;
import com.netflix.msl.entityauth.RsaStore;
import com.netflix.msl.keyx.AsymmetricWrappedExchange;
import com.netflix.msl.keyx.DiffieHellmanExchange;
import com.netflix.msl.keyx.JsonWebEncryptionLadderExchange;
import com.netflix.msl.keyx.JsonWebKeyLadderExchange;
import com.netflix.msl.keyx.KeyRequestData;
import com.netflix.msl.keyx.SymmetricWrappedExchange;
import com.netflix.msl.msg.ErrorHeader;
import com.netflix.msl.msg.MessageContext;
import com.netflix.msl.msg.MslControl;
import com.netflix.msl.msg.MslControl.MslChannel;
import com.netflix.msl.tokens.MasterToken;
import com.netflix.msl.userauth.EmailPasswordStore;
import com.netflix.msl.util.MslContext;
import com.netflix.msl.util.MslStore;

import mslcli.client.msg.ClientRequestMessageContext;
import mslcli.client.util.ClientMslContext;

import mslcli.common.msg.MessageConfig;
import mslcli.common.userauth.UserAuthenticationDataHandle;
import mslcli.common.util.SharedUtil;

import static mslcli.common.Constants.*;

/**
 * MSL Client class.
 *
 * @author Vadim Spector <vspector@netflix.com>
 */

public final class Client {

    /**
     * Data object encapsulating response from the server
     */
    public static final class Response {
        private Response(final byte[] payload, final ErrorHeader errHeader) {
            this.payload = payload;
            this.errHeader = errHeader;
        }
        public byte[] getPayload() {
            return payload;
        }
        public ErrorHeader getErrorHeader() {
            return errHeader;
        }
        private final byte[] payload;
        private final ErrorHeader errHeader;
    }
        
    /**
     * @param clientId - client entity identity
     * @param mslCtrl - MSL controller implementing MSL protocol stack
     * @param mslStore - MSL Store
     */
    public Client(final String clientId, final MslControl mslCtrl, final MslStore mslStore) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Undefined Client Id");
        }
        if (mslCtrl == null) {
            throw new IllegalArgumentException("NULL MSL Control");
        }
        if (mslStore == null) {
            throw new IllegalArgumentException("NULL MSL Store");
        }

        // Set client ID
        this.clientId = clientId;

        // Set the MSL control.
        this.mslCtrl = mslCtrl;

        // Initialize MSL store.
        this.mslStore = mslStore;

        // Create the pre-shared key store.
        final PresharedKeyStore presharedKeyStore = SharedUtil.getClientPresharedKeyStore();

        // Create the RSA key store
        final RsaStore rsaStore = SharedUtil.getClientRsaStore();

        // Create the email/password store.
        final EmailPasswordStore emailPasswordStore = SharedUtil.getClientEmailPasswordStore();

        // Set up the MSL context
        this.mslCtx = new ClientMslContext(clientId, presharedKeyStore, rsaStore, emailPasswordStore, mslStore);

        // initialize key request data
        this.keyRequestDataSet = new HashSet<KeyRequestData>();
    }

    /**
     * Send single request.
     * @param request message payload to send
     * @param cfg message security policies
     * @param remoteUrl target URL for sending message
     */
    public Response sendRequest(final byte[] request, final MessageConfig cfg, final URL remoteUrl)
        throws ExecutionException, IOException, InterruptedException, MslException
    {
        if (userAuthenticationDataHandle == null) {
            throw new IllegalStateException("Uninitialized UserAuthenticationDataHandle");
        }

        final MessageContext msgCtx = new ClientRequestMessageContext(
            cfg.isEncrypted,
            cfg.isIntegrityProtected,
            cfg.isNonReplayable,
            cfg.userId,
            userAuthenticationDataHandle,
            keyRequestDataSet,
            request
            );

        final Future<MslChannel> f = mslCtrl.request(mslCtx, msgCtx, remoteUrl, TIMEOUT_MS);
        final MslChannel ch;
        ch = f.get();
        if (ch == null)
            return null;

        final ErrorHeader errHeader = ch.input.getErrorHeader();
        if (errHeader == null) {
            return new Response(SharedUtil.readIntoArray(ch.input), null);
        } else {
            return new Response(null, errHeader);
        }
    }

    /**
     * Set key request data for specific key request and (if applicable) mechanism.
     */
    public void setKeyRequestData(final String kxType, final String mechanism) throws MslException, IOException {
        if (kxType == null) {
            throw new IllegalArgumentException("NULL Key Exchange Type");
        }
        keyRequestDataSet.clear();
        if (KX_DH.equals(kxType)) {
            final KeyPair dhKeyPair = SharedUtil.generateDiffieHellmanKeys(DEFAULT_DH_PARAMS_ID);
            keyRequestDataSet.add(new DiffieHellmanExchange.RequestData(DEFAULT_DH_PARAMS_ID, ((DHPublicKey)dhKeyPair.getPublic()).getY(), (DHPrivateKey)dhKeyPair.getPrivate()));
        } else if (KX_SWE.equals(kxType)) {
            keyRequestDataSet.add(new SymmetricWrappedExchange.RequestData(SymmetricWrappedExchange.KeyId.PSK));
        } else if (KX_AWE.equals(kxType)) {
            if (mechanism == null) {
                throw new IllegalArgumentException("Missing Key Wrapping Mechanism for Asymmetric Wrapped Key Exchange");
            }
            final AsymmetricWrappedExchange.RequestData.Mechanism m = Enum.valueOf(AsymmetricWrappedExchange.RequestData.Mechanism.class, mechanism);
            if (aweKeyPair == null) {
               aweKeyPair = SharedUtil.generateAsymmetricWrappedExchangeKeyPair();
            }
            keyRequestDataSet.add(new AsymmetricWrappedExchange.RequestData(DEFAULT_AWE_KEY_PAIR_ID, m, aweKeyPair.getPublic(), aweKeyPair.getPrivate()));
        } else if (KX_JWEL.equals(kxType)) {
            final JsonWebEncryptionLadderExchange.Mechanism m = JsonWebEncryptionLadderExchange.Mechanism.PSK;
            final byte[] wrapdata = null;
            keyRequestDataSet.add(new JsonWebEncryptionLadderExchange.RequestData(m, wrapdata));
        } else if (KX_JWKL.equals(kxType)) {
            final JsonWebKeyLadderExchange.Mechanism m = JsonWebKeyLadderExchange.Mechanism.PSK;
            final byte[] wrapdata = null;
            keyRequestDataSet.add(new JsonWebKeyLadderExchange.RequestData(m, wrapdata));
        } else {
            throw new IllegalArgumentException("Unsupported Key Exchange Type " + kxType);
        }
    }

    /**
     * Set user authentication data handle
     */
    public void setUserAuthenticationDataHandle(final UserAuthenticationDataHandle userAuthenticationDataHandle) {
        if (userAuthenticationDataHandle == null) {
            throw new IllegalArgumentException("NULL UserAuthenticationDataHandle");
        }
        this.userAuthenticationDataHandle = userAuthenticationDataHandle;
    }

    public MslStore getMslStore() {
        return mslStore;
    }

    /** Client Entity ID */
    private final String clientId;

    /** MSL context */
    private final MslContext mslCtx;

    /** MSL control */
    private final MslControl mslCtrl;

    /** User Authentication Data */
    private UserAuthenticationDataHandle userAuthenticationDataHandle;

    /** key request data set chosen by MslControl in the order of preference */
    private final Set<KeyRequestData> keyRequestDataSet;

    /** MSL store storing master tokens with associated crypto context, user id tokens, and service tokens */
    private final MslStore mslStore;

    /** Cached RSA Key Pair for asymmetric key wrap key exchange to avoid expensive key pair generation.
     * This is an optimization specific to this application, to avoid annoying delays in generating
     * 4096-bit RSA key pairs. Real-life implementations should not re-use key wrapping keys
     * too many times.
     */
    private KeyPair aweKeyPair = null;
}
