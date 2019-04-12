package org.acestream.livechannels.sync;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;

import org.acestream.livechannels.Constants;

import java.util.concurrent.atomic.AtomicInteger;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;


/**
 * Static helper methods for working with the SyncJobService.
 */
public class SyncUtils {

    private final static String TAG = "AS/SyncUtils";

    static final int SYNC_CHANNELS_JOB_ID = 0;
    static final int SYNC_CHANNELS_FORCE_JOB_ID = 1;
    static final int SYNC_EPG_JOB_ID = 2;

    static final String EXTRA_JOB_ID = "job_id";

    private static AtomicInteger sJobSequence = new AtomicInteger(0);

    static String getJobName(JobParameters params) {
        return getJobName(getJobId(params));
    }

    static String getJobName(int jobId) {
        switch(jobId) {
            case SYNC_CHANNELS_JOB_ID:
                return "sync_channels";
            case SYNC_CHANNELS_FORCE_JOB_ID:
                return "sync_channels_force";
            case SYNC_EPG_JOB_ID:
                return "sync_epg";
            default:
                return "unknown";
        }
    }

    /** Send the job to JobScheduler. **/
    private static void scheduleJob(Context context, JobInfo job) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    public static void requestChannelsSync(Context context, String inputId, boolean forceUpdate){
        requestSync(context, inputId, false, true, forceUpdate);
    }

    public static void requestEPGSync(Context context, String inputId){
        requestSync(context, inputId, true, false);
    }

    public static void requestSync(Context context, String inputId, boolean syncEPG, boolean syncChannels) {
        requestSync(context, inputId, syncEPG, syncChannels, false);
    }

    public static void requestSync(Context context, String inputId, boolean syncEPG, boolean syncChannels, boolean forceUpdate) {
        int sequence = sJobSequence.getAndAdd(1);
        int jobId;

        if(syncChannels) {
            if(forceUpdate) {
                jobId = SYNC_CHANNELS_FORCE_JOB_ID;
            }
            else {
                jobId = SYNC_CHANNELS_JOB_ID;
            }
        }
        else if(syncEPG) {
            jobId = SYNC_EPG_JOB_ID;
        }
        else {
            Log.e(TAG, "unknown job");
            return;
        }

        Log.v(TAG, "sync_utils: requestSync:"
                + " epg=" + syncEPG
                + " channels=" + syncChannels
                + " force=" + forceUpdate
                + " job=" + getJobName(jobId)
                + " seq=" + sequence
        );

        PersistableBundle pBundle = new PersistableBundle();
        pBundle.putInt(ContentResolver.SYNC_EXTRAS_MANUAL, 1);
        pBundle.putInt(ContentResolver.SYNC_EXTRAS_EXPEDITED, 1);
        pBundle.putString(Constants.BUNDLE_KEY_INPUT_ID, inputId);
        pBundle.putInt(EXTRA_JOB_ID, jobId);

        JobInfo.Builder builder = new JobInfo.Builder(sequence,
                new ComponentName(context, SyncJobService.class));
        JobInfo jobInfo = builder
                .setExtras(pBundle)
                .setOverrideDeadline(SyncJobService.OVERRIDE_DEADLINE_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);

        // notify that we're started
        Intent intent = new Intent(Constants.ACTION_SYNC_STATUS_CHANGED);
        intent.putExtra(Constants.BUNDLE_KEY_INPUT_ID, inputId);
        intent.putExtra(Constants.SYNC_STATUS, Constants.SYNC_STARTED);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    static int getJobId(JobParameters params) {
        if(params == null) {
            return -1;
        }
        return params.getExtras().getInt(EXTRA_JOB_ID, -1);
    }

    static int getJobSequence(JobParameters params) {
        if(params == null) {
            return -1;
        }
        return params.getJobId();
    }
}