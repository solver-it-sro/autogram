package digital.slovensko.autogram.ui;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import digital.slovensko.autogram.core.Autogram;
import digital.slovensko.autogram.core.Batch;
import digital.slovensko.autogram.core.BatchResponder;
import digital.slovensko.autogram.core.SigningJob;
import digital.slovensko.autogram.core.TargetPath;
import digital.slovensko.autogram.core.errors.AutogramException;
import digital.slovensko.autogram.util.Logging;

public class BatchGuiFileResponder extends BatchResponder {
    private final Autogram autogram;
    private final List<File> list;
    // private List<SigningJob> jobs = new ArrayList<SigningJob>();
    private Map<File, File> targetFiles = new HashMap<File, File>();
    private Map<File, AutogramException> errors = new HashMap<File, AutogramException>();
    private boolean uiNotifiedOnAllFilesSigned = false;
    private TargetPath targetPath;

    public BatchGuiFileResponder(Autogram autogram, List<File> list) {
        this.autogram = autogram;
        this.list = list;
    }

    @Override
    public void onBatchStartSuccess(Batch batch) {
        try {
            targetPath = TargetPath.fromBatchSource(list.get(0).toPath());
            targetPath.mkdirIfDir();

        } catch (AutogramException e) {
            autogram.onSigningFailed(e);
            throw e;
        }

        for (File file : list) {
            try {
                targetFiles.put(file, null);
                errors.put(file, null);
                Logging.log("1 Signing " + file.toString());
                var responder = new SaveFileFromBatchResponder(file, autogram, targetPath, (File targetFile) -> {
                    targetFiles.put(file, targetFile);
                    Logging.log(batch.getProcessedDocumentsCount() + " / " + batch.getTotalNumberOfDocuments()
                            + " signed " + file.toString());
                    if (batch.isAllProcessed()) {
                        onAllFilesSigned();
                    }
                }, (AutogramException error) -> {
                    Logging.log("Signing failed " + file.toString() + " all:" + batch.isAllProcessed());
                    errors.put(file, error);
                    if (batch.isAllProcessed()) {
                        onAllFilesSigned();
                    }
                });
                var job = SigningJob.buildFromFileBatch(file, autogram, responder);
                autogram.batchSign(job, batch.getBatchId());
                Logging.log("Started batchSigning " + file.toString() + "for job " + job.hashCode());
                Logging.log("Ended batchSigning " + file.toString() + "for job " + job.hashCode());
            } catch (AutogramException e) {
                autogram.onSigningFailed(e);
                break;
            }
        }
    }

    private void onAllFilesSigned() { // synchronized
        if (!uiNotifiedOnAllFilesSigned) {
            uiNotifiedOnAllFilesSigned = true;
            System.out.println(errors.values().stream().map(e -> e == null ? "" : e.toString()).toList());
            var result = new BatchGuiResult(targetPath, targetFiles, errors);
            autogram.onDocumentBatchSaved(result);
        }
    }

    @Override
    public void onBatchStartFailure(AutogramException error) {
        autogram.onSigningFailed(error);
    }

    @Override
    public void onBatchSignFailed(AutogramException error) {

    }
}
