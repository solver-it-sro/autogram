package digital.slovensko.autogram.core.errors;

public class CKRFunctionFailedException extends AutogramException {
    public CKRFunctionFailedException() {
        super("Nastala chyba", "Chyba komunikácie s podpisovým úložiskom", "Nastala chyba pri komunikácii s podpisovým úložiskom. V závislosti od použitého ovládača sa toto niekedy nevyhnutne stáva. \n\nSkúste zopakovať požadovanú akciu.", null);
    }
}
