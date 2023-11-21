package digital.slovensko.autogram.drivers;

import digital.slovensko.autogram.core.errors.PINIncorrectException;
import digital.slovensko.autogram.providers.XPrivateKey;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.KSPrivateKeyEntry;
import eu.europa.esig.dss.token.PrefilledPasswordCallback;
import org.xipki.pkcs11.wrapper.PKCS11Exception;
import org.xipki.pkcs11.wrapper.PKCS11Module;
import org.xipki.pkcs11.wrapper.Slot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.xipki.pkcs11.wrapper.PKCS11Constants.*;

public class XSignatureToken extends AbstractKeyStoreTokenConnection {
    private final String pkcsPath;
    private final PrefilledPasswordCallback passwordProtection;
    private final boolean scanAllSlots;
    private PKCS11Module pkcsModule;

    public XSignatureToken(String pkcsPath, PrefilledPasswordCallback passwordProtection, boolean scanAllSlots) {
        this.pkcsPath = pkcsPath;
        this.passwordProtection = passwordProtection;
        this.scanAllSlots = scanAllSlots;
    }

    @Override
    protected KeyStore getKeyStore() throws DSSException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected KeyStore.PasswordProtection getKeyProtectionParameter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DSSPrivateKeyEntry> getKeys() {
        var keys = new ArrayList<DSSPrivateKeyEntry>();

        try {
            pkcsModule = PKCS11Module.getInstance(pkcsPath);
            pkcsModule.initialize();

            var slots = pkcsModule.getSlotList(true);

            if (!scanAllSlots) {
                slots = new Slot[]{slots[0]}; // TODO slotIndex
            }

            Map<ByteBuffer, Object[]> entryDataById = new LinkedHashMap<>();

            boolean wasLoggedIn = false;

            for (var slot : slots) {
                var token = slot.getToken();
                var session = token.openSession(false);

                if (!wasLoggedIn) {
                    var tokenInfo = session.getToken().getTokenInfo();
                    if (tokenInfo.isLoginRequired()) {
                        if (!tokenInfo.isProtectedAuthenticationPath()) { // TODO actually ask this driver to allow per driver overrides
                            session.login(CKU_USER, null);
                        } else {
                            session.login(CKU_USER, passwordProtection.getPassword());
                        }
                    }
                    wasLoggedIn = true;
                }

                var objectIds = session.findObjects(Integer.MAX_VALUE);

                for (var objectId : objectIds) {
                    var attrs = session.getDefaultAttrValues(objectId);
                    var cka_class = attrs.getLongAttrValue(CKA_CLASS);
                    var cka_id = ByteBuffer.wrap(attrs.getByteArrayAttrValue(CKA_ID));
                    var cka_value = attrs.getByteArrayAttrValue(CKA_VALUE);

                    if (!entryDataById.containsKey(cka_id)) {
                        entryDataById.put(cka_id, new Object[2]);
                    }

                    if (cka_class == CKO_CERTIFICATE) {
                        var is = new ByteArrayInputStream(cka_value);
                        var fac = CertificateFactory.getInstance("X509");
                        var cert = fac.generateCertificate(is);

                        entryDataById.get(cka_id)[0] = cert;
                    } else if (cka_class == CKO_PRIVATE_KEY) {
                        var pk = new XPrivateKey(session, objectId, attrs);

                        entryDataById.get(cka_id)[1] = pk;
                    } else {
                        throw new IllegalStateException("Unexpected value: " + cka_class);
                    }
                }
            }

            for (Object[] entry : entryDataById.values()) {
                if (entry[0] == null || entry[1] == null) continue;

                var pk = (XPrivateKey) entry[1];
                var cert = (Certificate) entry[0];

                var pke = new KeyStore.PrivateKeyEntry(pk, new Certificate[]{cert});
                var key = new KSPrivateKeyEntry(pk.getAlias(), pke);
                keys.add(key);
            }
        } catch (PKCS11Exception e) {
            if (e.getErrorCode() == CKR_PIN_INCORRECT) {
                throw new PINIncorrectException();
            } else {
                throw new RuntimeException(e);
            }
        } catch (IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
        return keys;
    }

    @Override
    public void close() {
        // TODO
        try {
            pkcsModule.finalize(null);
        } catch (PKCS11Exception e) {
            throw new RuntimeException(e);
        }
    }
}
