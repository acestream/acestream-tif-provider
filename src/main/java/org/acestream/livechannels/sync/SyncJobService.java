package org.acestream.livechannels.sync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.util.SparseArray;

import org.acestream.engine.ServiceClient;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.livechannels.Constants;

import java.util.ArrayList;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SyncJobService extends JobService implements ServiceClient.Callback {
    private static final String TAG = "AS/SyncJobService";

    public static final String ACTION_SYNC_TASK_STARTED = "ACTION_SYNC_TASK_STARTED";
    public static final String ACTION_SYNC_TASK_FINISHED = "ACTION_SYNC_TASK_FINISHED";

    public static final String EXTRA_JOB_PARAMS = "job_params";

    public static final long OVERRIDE_DEADLINE_MILLIS = 1000;  // 1 second

    private final SparseArray<SyncTask> mTaskArray = new SparseArray<>();
    private final List<SyncTask> mEngineReadyQueue = new ArrayList<>();

    private ServiceClient mServiceClient = null;
    private boolean mEngineReady = false;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction() == null) {
                return;
            }

            JobParameters params = intent.getParcelableExtra(EXTRA_JOB_PARAMS);
            int seq = SyncUtils.getJobSequence(params);

            Log.v(TAG, "receiver: job=" + SyncUtils.getJobName(params) + " seq=" + seq + " action=" + intent.getAction());

            switch(intent.getAction()) {
                case ACTION_SYNC_TASK_STARTED:
                    break;
                case ACTION_SYNC_TASK_FINISHED:
                    syncTaskFinished(params);
                    break;
            }
        }
    };

    private void syncTaskFinished(JobParameters params) {
        int jobId = SyncUtils.getJobId(params);
        int seq = SyncUtils.getJobSequence(params);
        Log.d(TAG, "sync_job: finished: job=" + SyncUtils.getJobName(jobId) + " seq=" + seq);

        mTaskArray.delete(jobId);
        jobFinished(params, false);
        if (jobId == SyncUtils.SYNC_CHANNELS_FORCE_JOB_ID) {
            Intent intent = new Intent(Constants.ACTION_SYNC_STATUS_CHANGED);
            intent.putExtra(
                    Constants.BUNDLE_KEY_INPUT_ID,
                    params.getExtras().getString(Constants.BUNDLE_KEY_INPUT_ID)
            );
            intent.putExtra(Constants.SYNC_STATUS, Constants.SYNC_FINISHED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        final IntentFilter filter = new IntentFilter(ACTION_SYNC_TASK_STARTED);
        filter.addAction(ACTION_SYNC_TASK_FINISHED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);

        try {
            mServiceClient = new ServiceClient("SyncJob", this, this, false);
            mServiceClient.bind();
        }
        catch (ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "AceStream is not installed");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);

        if(mServiceClient != null) {
            mServiceClient.unbind();
            mServiceClient = null;
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = SyncUtils.getJobId(params);
        int sequence = SyncUtils.getJobSequence(params);

        // put job in queue
        SyncTask syncTask = mTaskArray.get(jobId);

        if(syncTask == null) {

            if(jobId == SyncUtils.SYNC_CHANNELS_JOB_ID) {
                SyncTask forcedJob = mTaskArray.get(SyncUtils.SYNC_CHANNELS_FORCE_JOB_ID);
                if(forcedJob != null) {
                    Log.v(TAG, "sync_job: start: forced job already exists:"
                            + " job=" + SyncUtils.getJobName(params)
                            + " seq=" + SyncUtils.getJobSequence(params)
                            + " otherSeq=" + forcedJob.getJobSequence()
                    );
                    return false;
                }
            }

            Log.v(TAG, "sync_job: start:"
                    + " job=" + SyncUtils.getJobName(jobId)
                    + " seq=" + sequence
                    + " engineReady=" + mEngineReady
            );

            syncTask = new SyncTask(params);
            mTaskArray.put(jobId, syncTask);

            if(mEngineReady) {
                syncTask.run(this);
            }

        }
        else {
            Log.v(TAG, "sync_job: start: already exists:"
                    + " job=" + SyncUtils.getJobName(params)
                    + " seq=" + SyncUtils.getJobSequence(params)
                    + " otherSeq=" + syncTask.getJobSequence()
            );
            return false;
        }

        if(!mEngineReady) {
            // connect to engine and execute job from queue when it's ready
            return connectEngine(syncTask);
        }
        else {
            return true;
        }
    }

    private boolean connectEngine(SyncTask taskToQueue) {
        if(mServiceClient == null) {
            Log.e(TAG, "sync_job: service client is null");
            return false;
        }

        mEngineReadyQueue.add(taskToQueue);
        Log.d(TAG, "sync_job: start engine: job=" + SyncUtils.getJobName(taskToQueue.getJobId()));

        try {
            mServiceClient.startEngine();
        }
        catch (ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "AceStream is not installed");
            return false;
        }

        return true;
    }

    private void startJobFromQueue() {
        try {
            Log.v(TAG, "sync_job: got " + mEngineReadyQueue.size() + " jobs in queue");

            // copy jobs
            for (SyncTask task: mEngineReadyQueue) {
                Log.d(TAG, "sync: start job from queue:"
                        + " job=" + SyncUtils.getJobName(task.getJobId())
                        + " seq=" + task.getJobSequence()
                );
                task.run(this);
            }

            // clear queue
            mEngineReadyQueue.clear();
        }
        catch(Throwable e) {
            Log.e(TAG, "sync: failed to start from queue", e);
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob: job=" + SyncUtils.getJobName(params) + " seq=" + SyncUtils.getJobSequence(params));

        return false;
    }

    @Override
    public void onConnected(IAceStreamEngine service) {
        Log.d(TAG, "sync_job: engine ready");
        mEngineReady = true;
        startJobFromQueue();
    }

    @Override
    public void onFailed() {
        Log.d(TAG, "sync_job: engine failed");
        mEngineReady = false;
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "sync_job: engine disconnected");
        mEngineReady = false;
    }

    @Override
    public void onUnpacking() {
        Log.d(TAG, "sync_job: engine unpacking");
    }

    @Override
    public void onStarting() {
        Log.d(TAG, "sync_job: engine starting");
    }

    @Override
    public void onStopped() {
        Log.d(TAG, "sync_job: engine stopped");
        mEngineReady = false;
    }

    @Override
    public void onPlaylistUpdated() {
        Log.d(TAG, "onPlaylistUpdated");
    }

    @Override
    public void onEPGUpdated() {
        Log.d(TAG, "onEPGUpdated");
    }

    @Override
    public void onSettingsUpdated() {
    }

    @Override
    public void onAuthUpdated() {
    }

    @Override
    public void onRestartPlayer() {
    }

    private static class SyncTask {
        private JobParameters mJobParams;

        SyncTask(JobParameters params) {
            mJobParams = params;
        }

        int getJobId() {
            return SyncUtils.getJobId(mJobParams);
        }

        int getJobSequence() {
            return SyncUtils.getJobSequence(mJobParams);
        }

        void run(Context context) {
            if(SyncUtils.getJobId(mJobParams) == SyncUtils.SYNC_CHANNELS_FORCE_JOB_ID) {
                SyncService.stopCurrentTask();
            }

            Intent intent = new Intent(context, SyncService.class);
            intent.putExtra(EXTRA_JOB_PARAMS, mJobParams);
            context.startService(intent);
        }

    }
}
