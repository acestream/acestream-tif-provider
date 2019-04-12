package org.acestream.livechannels.sync;

import android.app.IntentService;
import android.app.job.JobParameters;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import org.acestream.livechannels.Constants;
import org.acestream.livechannels.datasource.ChannelsAPI;
import org.acestream.livechannels.datasource.DataSourceProvider;
import org.acestream.livechannels.model.Channel;
import org.acestream.livechannels.model.Program;
import org.acestream.livechannels.tvinput.TvInputService;
import org.acestream.livechannels.utils.TvContractUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SyncService extends IntentService {
    public static final String TAG = "AS/SyncService";

    private static final int FULL_SYNC_WINDOW_SEC = 60 * 60 * 24 * 5;  // 5 days
    private static final int BATCH_OPERATION_COUNT = 100;
    private static final int FETCH_EPG_BATCH_COUNT = 100;

    private static volatile boolean sStopCurrentTask = false;

    public static void stopCurrentTask() {
        Log.v(TAG, "stopCurrentTask");
        sStopCurrentTask = true;
    }

    public SyncService() {
        super("SyncService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(intent == null) {
            Log.e(TAG, "onHandleIntent: null intent");
            return;
        }

        if(sStopCurrentTask) {
            Log.v(TAG, "onHandleIntent: reset stop flag");
            sStopCurrentTask = false;
        }

        JobParameters params = intent.getParcelableExtra(SyncJobService.EXTRA_JOB_PARAMS);

        notifyCaller(SyncJobService.ACTION_SYNC_TASK_STARTED, params);
        try {
            doSync(params);
        }
        catch(Throwable e) {
            // This can happen when trying to run sync on non Android TV device
            Log.e(TAG, "Failed to sync: " + e.getMessage());
        }
        // Always reset stop flag when done
        sStopCurrentTask = false;
        notifyCaller(SyncJobService.ACTION_SYNC_TASK_FINISHED, params);
    }

    private void notifyCaller(String action, JobParameters params) {
        Intent intent = new Intent(action);
        intent.putExtra(SyncJobService.EXTRA_JOB_PARAMS, params);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void doSync(JobParameters params) {
        int jobId = SyncUtils.getJobId(params);
        int sequence = SyncUtils.getJobSequence(params);

        PersistableBundle extras = params.getExtras();
        String inputId = extras.getString(Constants.BUNDLE_KEY_INPUT_ID);
        if (inputId == null) {
            Log.e(TAG, "sync_job: null input id");
            return;
        }

        Log.v(TAG, String.format("sync_job: start sync: job=%s seq=%d",
                SyncUtils.getJobName(jobId),
                sequence));

        if(jobId == SyncUtils.SYNC_CHANNELS_JOB_ID || jobId == SyncUtils.SYNC_CHANNELS_FORCE_JOB_ID) {
            //NOTE: sync channels task is never interrupted because it's fast
            try {
                boolean needUpdate = false;
                boolean forceUpdate = (jobId == SyncUtils.SYNC_CHANNELS_FORCE_JOB_ID);

                if (forceUpdate) {
                    // always update when forced
                    needUpdate = true;
                }
                else {
                    // compare playlist hashes before update
                    String localHash = TvInputService.getPlaylistHash(this);
                    String remoteHash = getPlaylistHash(this, inputId);

                    if(localHash == null || !localHash.equals(remoteHash)) {
                        needUpdate = true;
                        TvInputService.setPlaylistHash(this, remoteHash);
                    }

                    Log.d(TAG, "sync_job: check playlist hash: seq=" + sequence + " needUpdate=" + needUpdate + " remote=" + remoteHash + " local=" + localHash);
                }

                if (needUpdate) {
                    List<Channel> newChannels = loadChannels(this, inputId, forceUpdate);
                    Log.d(TAG, "sync_job: playlist loaded: seq=" + sequence + " count=" + newChannels.size());
                    updateChannels(this, inputId, newChannels);
                }
            }
            catch(ChannelsAPI.ApiError e) {
                Log.e(TAG, "failed to get channels, stop sync: seq=" + sequence + " err=" + e.getMessage());
                return;
            }
        }

        if(jobId == SyncUtils.SYNC_EPG_JOB_ID) {
            List<Channel> channels = TvContractUtils.getChannels(this.getContentResolver());
            if (channels == null) {
                Log.w(TAG, "sync_job: null channels");
                return;
            }

            Log.v(TAG, "sync_job: sync epg: channels=" + channels.size() + " seq=" + sequence + " id=" + SyncUtils.getJobName(jobId));

            if(sStopCurrentTask) {
                Log.v(TAG, "sync_job: task cancelled: job=" + SyncUtils.getJobName(jobId) + " seq=" + sequence);
                return;
            }

            long t, timeLoad, timeUpdate;
            long startMs = System.currentTimeMillis();
            long endMs = startMs + FULL_SYNC_WINDOW_SEC * 1000;

            List<Channel> channelsToFetch = new ArrayList<>(FETCH_EPG_BATCH_COUNT);

            for (int i = 0; i < channels.size(); ++i) {
                if(channels.get(i).getId()>0) {
                    Channel channel = channels.get(i);
                    channelsToFetch.add(channel);

                    if(channelsToFetch.size() >= FETCH_EPG_BATCH_COUNT) {
                        try {
                            t = System.currentTimeMillis();
                            Map<Channel,List<Program>> epgData = loadEPGBatch(this, channelsToFetch, startMs, endMs);
                            timeLoad = System.currentTimeMillis() - t;

                            if(sStopCurrentTask) {
                                Log.v(TAG, "sync_job: task cancelled: job=" + SyncUtils.getJobName(jobId) + " seq=" + sequence);
                                return;
                            }

                            t = System.currentTimeMillis();
                            if(epgData == null) {
                                break;
                            }

                            updateProgramsBatch(epgData);
                            timeUpdate = System.currentTimeMillis() - t;

                            Log.d(TAG, "sync_job: sync epg done: batch_size=" + channelsToFetch.size() + " epg_size=" + epgData.size() + " load=" + timeLoad + " update=" + timeUpdate);

                            if(sStopCurrentTask) {
                                Log.v(TAG, "sync_job: task cancelled: job=" + SyncUtils.getJobName(jobId) + " seq=" + sequence);
                                return;
                            }
                        } catch (ChannelsAPI.ApiError e) {
                            Log.e(TAG, "sync_job: failed to get EPG, stop sync: seq=" + sequence + " err=" + e.getMessage());
                            break;
                        }
                        finally {
                            channelsToFetch.clear();
                        }
                    }
                }
            }

            if(sStopCurrentTask) {
                Log.v(TAG, "sync_job: task cancelled: job=" + SyncUtils.getJobName(jobId) + " seq=" + sequence);
                return;
            }

            // sync rest of channels
            if(channelsToFetch.size() > 0) {
                try {
                    Log.d(TAG, "sync_job: sync epg: batchSize=" + channelsToFetch.size());
                    Map<Channel,List<Program>> epgData = loadEPGBatch(this, channelsToFetch, startMs, endMs);
                    Log.d(TAG, "sync_job: sync epg done: batchSize=" + channelsToFetch.size() + " epgData=" + epgData.size());

                    updateProgramsBatch(epgData);
                } catch (ChannelsAPI.ApiError e) {
                    Log.e(TAG, "sync_job: failed to get EPG, stop sync: seq=" + sequence + " err=" + e.getMessage());
                }
            }

            Log.d(TAG, "sync_job: sync epg finished");
        }
    }

    private void updateChannels(Context context, String inputId, List<Channel> channels) {
        TvContractUtils.updateChannels(context, inputId, channels);
    }

    private void updateProgramsBatch(Map<Channel,List<Program>> epgData) {
        for(Map.Entry<Channel,List<Program>> item: epgData.entrySet()) {
            updatePrograms(item.getKey(), item.getValue());
        }
    }

    /**
     * Updates the system database, TvProvider, with the given programs.
     *
     * <p>If there is any overlap between the given and existing programs, the existing ones
     * will be updated with the given ones if they have the same title or replaced.
     *
     * @param channel The channel where the program info will be added.
     * @param newPrograms A list of {@link Program} instances which includes program
     *         information.
     */
    private void updatePrograms(Channel channel, List<Program> newPrograms) {
        Uri channelUri = TvContract.buildChannelUri(channel.getId());
        final int fetchedProgramsCount = newPrograms.size();
        if (fetchedProgramsCount == 0) {
            return;
        }

        //delete old programs. Temporary solution
        getContentResolver().delete(TvContract.buildProgramsUriForChannel(channelUri), null, null);

        List<Program> oldPrograms = TvContractUtils.getPrograms(getContentResolver(),
                channelUri);
        Program firstNewProgram = newPrograms.get(0);
        int oldProgramsIndex = 0;
        int newProgramsIndex = 0;
        // Skip the past programs. They will be automatically removed by the system.
        for (Program program : oldPrograms) {
            oldProgramsIndex++;
            if (program.getEndTimeUtcMillis() > firstNewProgram.getStartTimeUtcMillis()) {
                break;
            }
        }
        // Compare the new programs with old programs one by one and update/delete the old one
        // or insert new program if there is no matching program in the database.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        while (newProgramsIndex < fetchedProgramsCount) {
            Program oldProgram = oldProgramsIndex < oldPrograms.size()
                    ? oldPrograms.get(oldProgramsIndex) : null;
            Program newProgram = new Program.Builder(newPrograms.get(newProgramsIndex))
                    .setChannelId(ContentUris.parseId(channelUri)).build();
            boolean addNewProgram = false;
            if (oldProgram != null) {
                if (oldProgram.equals(newProgram)) {
                    // Exact match. No need to update. Move on to the next programs.
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (needsUpdate(oldProgram, newProgram)) {
                    // Partial match. Update the old program with the new one.
                    // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                    // could be application specific settings which belong to the old program.
                    ops.add(ContentProviderOperation.newUpdate(
                            TvContract.buildProgramUri(oldProgram.getProgramId()))
                            .withValues(newProgram.toContentValues())
                            .build());
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (oldProgram.getEndTimeUtcMillis()
                        < newProgram.getEndTimeUtcMillis()) {
                    // No match. Remove the old program first to see if the next program in
                    // {@code oldPrograms} partially matches the new program.
                    ops.add(ContentProviderOperation.newDelete(
                            TvContract.buildProgramUri(oldProgram.getProgramId()))
                            .build());
                    oldProgramsIndex++;
                } else {
                    // No match. The new program does not match any of the old programs. Insert
                    // it as a new program.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
            } else {
                // No old programs. Just insert new programs.
                addNewProgram = true;
                newProgramsIndex++;
            }
            if (addNewProgram) {
                ops.add(ContentProviderOperation
                        .newInsert(TvContract.Programs.CONTENT_URI)
                        .withValues(newProgram.toContentValues())
                        .build());
            }
            // Throttle the batch operation not to cause TransactionTooLargeException.
            if (ops.size() > BATCH_OPERATION_COUNT
                    || newProgramsIndex >= fetchedProgramsCount) {
                try {
                    getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "sync_job: failed to insert programs.", e);
                    return;
                }
                ops.clear();
            }
        }
    }

    /**
     * Returns {@code true} if the {@code oldProgram} program needs to be updated with the
     * {@code newProgram} program.
     */
    private boolean needsUpdate(Program oldProgram, Program newProgram) {
        // NOTE: Here, we update the old program if it has the same title and overlaps with the
        // new program. The test logic is just an example and you can modify this. E.g. check
        // whether the both programs have the same program ID if your EPG supports any ID for
        // the programs.
        return //oldProgram.getTitle().equals(newProgram.getTitle())&&
                oldProgram.getStartTimeUtcMillis() <= newProgram.getEndTimeUtcMillis()
                        && newProgram.getStartTimeUtcMillis() <= oldProgram.getEndTimeUtcMillis();
    }

    private static Map<Channel,List<Program>> loadEPGBatch(Context context, List<Channel> channels, long startTimeMs, long endTimeMs) throws ChannelsAPI.ApiError {
        return  DataSourceProvider.getEPGBatch(context, channels, startTimeMs, endTimeMs);
    }

    private static List<Channel> loadChannels(Context mContext, String inputId, boolean forceUpdate) throws ChannelsAPI.ApiError {
        return DataSourceProvider.getAllChannels(mContext, inputId, forceUpdate);
    }

    private static String getPlaylistHash(Context mContext, String inputId) throws ChannelsAPI.ApiError {
        return DataSourceProvider.getPlaylistHash(mContext, inputId);
    }
}
