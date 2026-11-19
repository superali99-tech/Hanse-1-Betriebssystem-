package org.novaos.mobile;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Looks for a NovaOS update in the background every few hours (also when NovaOS is not
 * open), downloads and checks it over Wi-Fi and shows the notification - nobody has to
 * search in the settings.
 */
public class UpdateJob extends JobService {
    private static final int ID = 4711;
    private static final long EVERY_MS = 3 * 60 * 60 * 1000L;

    /** Plans the job once (a periodic job survives restarts of the phone). */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(ID) != null) return;
        try {
            js.schedule(new JobInfo.Builder(ID, new ComponentName(c, UpdateJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(EVERY_MS)
                    .setPersisted(true)
                    .build());
        } catch (RuntimeException ignored) {
            // the home screen still checks when it is shown
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Updates.checkAndPrepare(getApplicationContext());
            jobFinished(params, false);
        }, "novaos-update").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;  // try again later
    }
}
