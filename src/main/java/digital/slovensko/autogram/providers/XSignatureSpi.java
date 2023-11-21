package digital.slovensko.autogram.providers;

import org.bouncycastle.asn1.*;
import org.xipki.pkcs11.wrapper.Mechanism;
import org.xipki.pkcs11.wrapper.PKCS11Constants;
import org.xipki.pkcs11.wrapper.PKCS11Exception;
import org.xipki.pkcs11.wrapper.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;

import static org.xipki.pkcs11.wrapper.PKCS11Constants.CKU_CONTEXT_SPECIFIC;

public class XSignatureSpi extends java.security.SignatureSpi {
    public static final byte[] SHA256_OID_PRELUDE = {48, 49, 48, 13, 6, 9, 96, -122, 72, 1, 101, 3, 4, 2, 1, 5, 0};

    private Session currentSession;
    private MessageDigest md;

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        throw new RuntimeException();
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        var pin = "123456".toCharArray(); // TODO request PIN and continue later

        var pk = (XPrivateKey) privateKey;
        currentSession = pk.getSession();
        try {
            md = MessageDigest.getInstance("SHA-256"); // TODO move elsewhere and do not hardcode
            currentSession.signInit(new Mechanism(PKCS11Constants.CKM_RSA_PKCS), pk.getObjectId()); // TODO refactor - move to key signinit
            currentSession.login(CKU_CONTEXT_SPECIFIC, pin);
        } catch (PKCS11Exception | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        throw new RuntimeException();
    }

    @Override
    protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
        md.update(b, off, len);
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        try {
            var digest = md.digest();

            // TODO megahack pkcs1 1.5 RSA format
            var out = new ByteArrayOutputStream();
            var asn = ASN1OutputStream.create(out, ASN1Encoding.DER);
            out.write(SHA256_OID_PRELUDE);
            asn.writeObject(new DEROctetString(digest));
            var data = out.toByteArray();

            return currentSession.sign(data);
        } catch (PKCS11Exception | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new UnsupportedOperationException();
    }
}
