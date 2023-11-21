package digital.slovensko.autogram.providers;

import org.xipki.pkcs11.wrapper.AttributeVector;
import org.xipki.pkcs11.wrapper.Session;

import java.security.PrivateKey;

import static org.xipki.pkcs11.wrapper.PKCS11Constants.*;

public class XPrivateKey implements PrivateKey {
    private final Session session;
    private final long objectId;
    private final AttributeVector pkAttrs;

    public XPrivateKey(Session session, long objectId, AttributeVector pkAttrs) {
        this.session = session;
        this.objectId = objectId;
        this.pkAttrs = pkAttrs;
    }

    @Override
    public String getAlgorithm() {
        var keyType = pkAttrs.getLongAttrValue(CKA_KEY_TYPE);
        if (keyType == CKK_RSA) {
            return "RSA";
        }
        throw new UnsupportedOperationException(); // TODO better message
    }

    @Override
    public String getFormat() {
        return "PKCS#11";
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    public Session getSession() {
        return session;
    }

    public long getObjectId() {
        return objectId;
    }

    public String getAlias() {
        return pkAttrs.getStringAttrValue(CKA_LABEL);
    }
}
