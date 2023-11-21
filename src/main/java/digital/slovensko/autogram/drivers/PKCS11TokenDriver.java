package digital.slovensko.autogram.drivers;

import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import eu.europa.esig.dss.token.Pkcs11SignatureToken;
import eu.europa.esig.dss.token.PrefilledPasswordCallback;

import java.nio.file.Path;
import java.security.KeyStore;

public class PKCS11TokenDriver extends TokenDriver {
    public PKCS11TokenDriver(String name, Path path, boolean needsPassword, String shortname) {
        super(name, path, needsPassword, shortname);
    }

    @Override
    public AbstractKeyStoreTokenConnection createTokenWithPassword(Integer slotId, char[] password) {
        return new XSignatureToken(getPath().toString(), new PrefilledPasswordCallback(new KeyStore.PasswordProtection(password)), true);
    }
}
