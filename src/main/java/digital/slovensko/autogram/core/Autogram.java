package digital.slovensko.autogram.core;

import digital.slovensko.autogram.core.errors.AutogramException;
import digital.slovensko.autogram.core.errors.BatchConflictException;
import digital.slovensko.autogram.core.errors.BatchNotStartedException;
import digital.slovensko.autogram.core.errors.UnrecognizedException;
import digital.slovensko.autogram.core.visualization.DocumentVisualizationBuilder;
import digital.slovensko.autogram.core.visualization.UnsupportedVisualization;
import digital.slovensko.autogram.drivers.TokenDriver;
import digital.slovensko.autogram.ui.BatchUiResult;
import digital.slovensko.autogram.ui.UI;
import digital.slovensko.autogram.util.Logging;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.pdfa.PDFAStructureValidator;
import javafx.application.Platform;

import java.io.File;
import java.util.function.Consumer;

public class Autogram {
    private final UI ui;
    /** Current batch, should be null if no batch was started yet */
    private Batch batch = null;
    private final DriverDetector driverDetector;

    public Autogram(UI ui) {
        this(ui, new DefaultDriverDetector());
    }

    public Autogram(UI ui, DriverDetector driverDetector) {
        this.ui = ui;
        this.driverDetector = driverDetector;
    }

    public void sign(SigningJob job) {
        ui.onUIThreadDo(() -> ui.startSigning(job, this));
    }

    public void checkPDFACompliance(SigningJob job) {
        if (!job.shouldCheckPDFCompliance())
            return;

        ui.onWorkThreadDo(() -> {
            var result = new PDFAStructureValidator().validate(job.getDocument());
            if (!result.isCompliant()) {
                ui.onUIThreadDo(() -> ui.onPDFAComplianceCheckFailed(job));
            }
        });
    }

    public void startVisualization(SigningJob job) {
        ui.onWorkThreadDo(() -> {
            try {
                var visualization = DocumentVisualizationBuilder.fromJob(job);
                ui.onUIThreadDo(() -> ui.showVisualization(visualization, this));
            } catch (Exception e) {
                Runnable onContinue = () -> ui.showVisualization(new UnsupportedVisualization(job), this);

                ui.onUIThreadDo(
                        () -> ui.showIgnorableExceptionDialog(new FailedVisualizationException(e, job, onContinue)));
            }
        });
    }

    public void sign(SigningJob job, SigningKey signingKey) {
        ui.onWorkThreadDo(() -> {
            try {
                job.signWithKeyAndRespond(signingKey);
                ui.onUIThreadDo(() -> ui.onSigningSuccess(job));
            } catch (DSSException e) {
                onSigningFailed(AutogramException.createFromDSSException(e));
            } catch (IllegalArgumentException e) {
                onSigningFailed(AutogramException.createFromIllegalArgumentException(e));
            } catch (Exception e) {
                onSigningFailed(new UnrecognizedException(e));
            }
        });
    }

    /**
     * Starts a batch - ask user - get signing key - start batch - return batch ID
     * 
     * @param totalNumberOfDocuments - expected number of documents to be signed
     * @param responder              - callback for http response
     */
    public void batchStart(int totalNumberOfDocuments, BatchResponder responder) {
        if (batch != null && !batch.isEnded())
            throw new BatchConflictException("Iné hromadné podpisovanie už prebieha");
        batch = new Batch(totalNumberOfDocuments);

        var startBatchTask = new AutogramBatchStartCallback() {
            public void accept(SigningKey key) {
                try {
                    Logging.log("Starting batch");
                    batch.start(key);
                    handleSuccess();
                } catch (Exception e) {
                    handleException(e);
                }                
            }

            private void handleException(Exception e) {
                if (e instanceof AutogramException)
                    responder.onBatchStartFailure((AutogramException) e);
                else {
                    Logging.log("Batch start failed with exception: " + e);
                    responder.onBatchStartFailure(
                            new AutogramException("Unkown error occured while starting batch", "",
                                    "Batch start failed with exception: " + e, e));
                }
            }

            private void handleSuccess() {
                ui.onWorkThreadDo(() -> responder.onBatchStartSuccess(batch));
            }
        };

        ui.onUIThreadDo(() -> {
            ui.startBatch(batch, this, startBatchTask);
        });
    }

    /**
     * Sign a single document
     * 
     * @param job
     * @param batchId - current batch ID, used to authenticate the request
     */
    public void batchSign(SigningJob job, String batchId) {
        if (batch == null)
            throw new BatchNotStartedException();

        batch.addJob(batchId, job);

        if (Platform.isFxApplicationThread()) {
            ui.onWorkThreadDo(() -> {
                ui.signBatch(job, batch.getSigningKey());
            });
        } else {
            ui.signBatch(job, batch.getSigningKey());
        }
    }

    public void updateBatch() {
        ui.onUIThreadDo(() -> {
            ui.updateBatch();
        });
    }

    /**
     * End the batch
     * 
     * @param batchId - current batch ID, used to authenticate the request
     */
    public boolean batchEnd(String batchId) {
        batch.validate(batchId);
        batch.end();
        ui.onUIThreadDo(() -> {
            ui.cancelBatch(batch);
        });
        return batch.isAllProcessed();
    }

    public void pickSigningKeyAndThen(Consumer<SigningKey> callback) {
        var drivers = driverDetector.getAvailableDrivers();
        ui.pickTokenDriverAndThen(drivers,
                (driver) -> ui.requestPasswordAndThen(driver, (password) -> ui.onWorkThreadDo(
                        () -> fetchKeysAndThen(driver, password, callback))));
    }

    private void fetchKeysAndThen(TokenDriver driver, char[] password,
            Consumer<SigningKey> callback) {
        try {
            var token = driver.createTokenWithPassword(password);
            var keys = token.getKeys();

            ui.onUIThreadDo(
                    () -> ui.pickKeyAndThen(keys, (privateKey) -> callback.accept(new SigningKey(token, privateKey))));
        } catch (DSSException e) {
            ui.onUIThreadDo(() -> ui.onPickSigningKeyFailed(AutogramException.createFromDSSException(e)));
        }
    }

    public void checkForUpdate() {
        ui.onWorkThreadDo(() -> {
            if (!Updater.newVersionAvailable())
                return;
            ui.onUIThreadDo(ui::onUpdateAvailable);
        });
    }

    public void onAboutInfo() {
        ui.onAboutInfo();
    }

    public void onDocumentSaved(File targetFile) {
        ui.onUIThreadDo(() -> ui.onDocumentSaved(targetFile));
    }

    public void onDocumentBatchSaved(BatchUiResult result) {
        ui.onUIThreadDo(() -> ui.onDocumentBatchSaved(result));
    }

    public void onSigningFailed(AutogramException e) {
        ui.onUIThreadDo(() -> ui.onSigningFailed(e));
    }
}
