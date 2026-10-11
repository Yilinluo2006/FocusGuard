package com.luoyilin.focusguard;

import android.app.job.*;
import java.util.HashMap;
import java.util.Map;

public class CloudBackupJobService extends JobService {
    private final Map<Integer, CloudBackupManager.Task> tasks = new HashMap<>();
    @Override public boolean onStartJob(JobParameters params) {
        CloudBackupManager.Task task = CloudBackupManager.sync(this, result -> {
            tasks.remove(params.getJobId());
            jobFinished(params, result.retry);
        });
        tasks.put(params.getJobId(), task);
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        CloudBackupManager.Task task = tasks.remove(params.getJobId());
        if (task != null) task.cancel();
        return CloudBackupManager.enabled(this);
    }
}
