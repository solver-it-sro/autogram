package digital.slovensko.autogram.providers;

import java.security.Provider;
import java.util.List;
import java.util.Map;

public class XProvider extends Provider {
    public XProvider(String name, String versionStr, String info) {
        super(name, versionStr, info);
        putService(new XService(this, "Signature", "SHA256withRSA", "digital.slovensko.autogram.providers.XSignatureSpi", null, null));
    }

    private class XService extends Service {
        public XService(Provider provider, String type, String algorithm, String className, List<String> aliases, Map<String, String> attributes) {
            super(provider, type, algorithm, className, aliases, attributes);
        }
    }
}
