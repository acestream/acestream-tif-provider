package org.acestream.livechannels.setup;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import android.util.Log;

import org.acestream.livechannels.R;

import java.util.List;

/**
 * Created by barbarian on 26.07.16.
 */
public class SettingsFragment extends BaseGuidedStepFragment {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private static final int ACTION_CONTINUE = 0;
    private static final int ACTION_UPDATE_CHANNELS = 22;
    //private static final int ACTION_SYNC_INTERVAL = 40;
    private static final int ACTION_BACK = 1;

//    private static final int ACTION_SYNC_INTERVAL_3 = 41;
//    private static final int ACTION_SYNC_INTERVAL_6 = 42;
//    private static final int ACTION_SYNC_INTERVAL_12 = 43;
//    private static final int ACTION_SYNC_INTERVAL_24 = 44;
//    private static final int ACTION_SYNC_INTERVAL_48 = 45;


//    private static final int ACTION_ID_PLAYER_ENGINE = 50;
//    private static final int ACTION_ID_EXO_PLAYER = 52;
//    private static final int ACTION_ID_VLC = 53;


    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.settings_title);
        String description = getString(R.string.setings_description);

        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);

        return new GuidanceStylist.Guidance(title, description, getString(R.string.input_label), icon);
    }

    @Override
    public void onCreateActions(@NonNull List actions, Bundle savedInstanceState) {

        addAction(getActivity(),actions, ACTION_UPDATE_CHANNELS, getString(R.string.setup_update_channels), getString(R.string.setup_update_channels_desc));

        //List<GuidedAction> intervalActions = new ArrayList<>();
        //addAction(getActivity(),intervalActions,ACTION_SYNC_INTERVAL_3,getResources().getQuantityString(R.plurals.hours_count, 3,3),"");
        //addAction(getActivity(),intervalActions,ACTION_SYNC_INTERVAL_6,getResources().getQuantityString(R.plurals.hours_count, 6,6),"");
        //addAction(getActivity(),intervalActions,ACTION_SYNC_INTERVAL_12,getResources().getQuantityString(R.plurals.hours_count, 12,12),"");
        //addAction(getActivity(),intervalActions,ACTION_SYNC_INTERVAL_24,getResources().getQuantityString(R.plurals.hours_count, 24,24),"");
        //addAction(getActivity(),intervalActions,ACTION_SYNC_INTERVAL_48,getResources().getQuantityString(R.plurals.hours_count, 48,48),"");

        //int syncPeriod = SyncUtils.getSyncPeriod(getActivity());
        //addDropDownAction(getActivity(),actions, ACTION_SYNC_INTERVAL,getString(R.string.setup_sync_interval),
        //        getResources().getQuantityString(R.plurals.hours_count, syncPeriod,syncPeriod),intervalActions);

        //final String sessionType = TvInputService.getSessionType(getActivity());
        //String sessionTypeTitle = "";
        //if (sessionType != null && sessionType.equals(Constants.SESSION_EXO_PLAYER)) {
        //    sessionTypeTitle = getString(R.string.exoplayer_engine_title);
        //}
        //else if (sessionType != null && sessionType.equals(Constants.SESSION_VLC)) {
        //    sessionTypeTitle = getString(R.string.vlc_engine_title);
        //}
        //else {
        //    sessionTypeTitle = getString(R.string.mediaplayer_engine_title);
        //}

        //List<GuidedAction> playerActions = new ArrayList<>();
        //addAction(getActivity(), playerActions, ACTION_ID_EXO_PLAYER, getString(R.string.exoplayer_engine_title), getString(R.string.exoplayer_engine_desc));
        //addAction(getActivity(), playerActions, ACTION_ID_VLC, getString(R.string.vlc_engine_title), getString(R.string.vlc_engine_desc));
        //addDropDownAction(getActivity(), actions, ACTION_ID_PLAYER_ENGINE, getString(R.string.player_engine_title), sessionTypeTitle, playerActions);

        addAction(getActivity(),actions, ACTION_BACK, getString(R.string.setup_cancel), getString(R.string.setup_cancel_desc));
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {

        switch ((int) action.getId()){
            case ACTION_UPDATE_CHANNELS:
                syncChannels();
                break;
            case ACTION_BACK:
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
                break;
            default:
                Log.w(TAG, "Action is not defined");
                break;
        }
    }

    private void syncChannels() {
        GuidedStepFragment fragment = new SyncingFragment();
        fragment.setArguments(getArguments());
        add(getFragmentManager(), fragment);
    }


//    @Override
//    public boolean onSubGuidedActionClicked(GuidedAction action) {
//        if(action.getId() == ACTION_SYNC_INTERVAL_3){
//            onSyncPeriodChanged(3);
//        }else if(action.getId() == ACTION_SYNC_INTERVAL_6){
//            onSyncPeriodChanged(6);
//        }else if(action.getId() == ACTION_SYNC_INTERVAL_12){
//            onSyncPeriodChanged(12);
//        }else if(action.getId() == ACTION_SYNC_INTERVAL_24){
//            onSyncPeriodChanged(24);
//        }else if(action.getId() == ACTION_SYNC_INTERVAL_48){
//            onSyncPeriodChanged(48);
//        }
//        else if(action.getId() == ACTION_ID_VLC){
//            onPlayerEngineChanged(Constants.SESSION_VLC,getString(R.string.vlc_engine_title));
//        }
//        else if(action.getId() == ACTION_ID_EXO_PLAYER){
//            onPlayerEngineChanged(Constants.SESSION_EXO_PLAYER,getString(R.string.exoplayer_engine_title));
//        }
//
//        return super.onSubGuidedActionClicked(action);
//    }

//    private void onPlayerEngineChanged(String engineType, String engineTypeTitle) {
//        TvInputService.setSessionType(getActivity(),engineType);
//
//        GuidedAction engineAct = findActionById(ACTION_ID_PLAYER_ENGINE);
//        if(engineAct!=null){
//            engineAct.setDescription(engineTypeTitle);
//            notifyActionChanged(findActionPositionById(ACTION_ID_PLAYER_ENGINE));
//        }
//    }

//    private boolean syncPeriodChanged = false;
//    private void onSyncPeriodChanged(int period){
//        SyncUtils.setSyncPeriod(getActivity(),period);
//
//        GuidedAction syncAct = findActionById(ACTION_SYNC_INTERVAL);
//        if(syncAct!=null){
//            syncAct.setDescription( getResources().getQuantityString(R.plurals.hours_count, period,period));
//            notifyActionChanged(findActionPositionById(ACTION_SYNC_INTERVAL));
//        }
//
//        syncPeriodChanged = true;
//    }

//    @Override
//    public void onStop(){
//        if(syncPeriodChanged){
//            SyncUtils.cancelAll(getActivity());
//            SyncUtils.setUpPeriodicSync(getActivity(), TVInputUtils.getInputId(getActivity(),getArguments()));
//        }
//
//        super.onStop();
//    }
}
