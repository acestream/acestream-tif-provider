package org.acestream.livechannels.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidedActionsStylist;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import org.acestream.livechannels.Constants;
import org.acestream.livechannels.R;
import org.acestream.livechannels.sync.SyncUtils;
import org.acestream.livechannels.utils.TVInputUtils;

import java.util.List;

/**
 * Created by barbarian on 26.07.16.
 */
public class SyncingFragment extends BaseGuidedStepFragment {
    private final static String TAG = "SyncFrag";
    private boolean mFinished;

    private final BroadcastReceiver mSyncStatusChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            final String inputId = TVInputUtils.getInputId(getActivity(),getArguments());
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "got event: finished=" + mFinished + " inputId=" + inputId);
                    if (mFinished||inputId==null) {
                        return;
                    }
                    String syncStatusChangedInputId = intent.getStringExtra(
                            Constants.BUNDLE_KEY_INPUT_ID);
                    if (syncStatusChangedInputId.equals(inputId)) {
                        String syncStatus = intent.getStringExtra(Constants.SYNC_STATUS);

                        Log.d(TAG, "sync status: " + syncStatus);

                        if (syncStatus.equals(Constants.SYNC_STARTED)) {
                            //syncing
                        } else if (syncStatus.equals(Constants.SYNC_FINISHED)) {
                            //sync finished
                            Log.d(TAG, "sync completed");

                            // Move to the CompletedFragment
                            GuidedStepFragment fragment = new SyncCompletedFragment();
                            fragment.setArguments(getArguments());
                            add(getFragmentManager(), fragment);
                            mFinished = true;
                        }
                    }
                    else {
                        Log.d(TAG, "input id mismatch: changed=" + syncStatusChangedInputId);
                    }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mSyncStatusChangedReceiver,
                new IntentFilter(Constants.ACTION_SYNC_STATUS_CHANGED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mSyncStatusChangedReceiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        mFinished = false;
        
        // Force a EPG sync
        syncChannels();
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        GuidedActionsStylist stylist = new GuidedActionsStylist() {
            @Override
            public int onProvideItemLayoutId() {
                return R.layout.setup_progress;
            }

        };
        return stylist;
    }

    @Override
    public int onProvideTheme() {
        return R.style.Theme_Wizard_Setup_NoSelector;
    }


    private void syncChannels(){
        String inputId = TVInputUtils.getInputId(getActivity(),getActivity().getIntent().getExtras());
        if(inputId!=null) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences(
                    Constants.PREFERENCE_TVCHANNELS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.BUNDLE_KEY_INPUT_ID, inputId);
            editor.apply();

            SyncUtils.requestChannelsSync(getActivity(), inputId, true);
        }
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                getString(R.string.syncing_title),
                getString(R.string.syncing_desc),
                getString(R.string.input_label),
                icon);

        return guidance;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction action = new GuidedAction.Builder(getActivity())
                .title(getString(R.string.syncing_progress_title))
                .infoOnly(true)
                .build();
        actions.add(action);
    }
}