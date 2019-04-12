package org.acestream.livechannels.setup;

import android.content.Context;
import android.os.Bundle;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidedAction;
import android.util.Log;

import java.util.List;

import org.acestream.engine.controller.Engine;
import org.acestream.livechannels.R;
import org.acestream.livechannels.model.AppContext;

/**
 * Created by barbarian on 26.07.16.
 */
public abstract class BaseGuidedStepFragment extends GuidedStepFragment {
    private final static String TAG = "AS/BaseGuidedStep";
    protected Engine mEngine;

    @Override
    public int onProvideTheme() {
        return R.style.Theme_Wizard_Setup;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        mEngine = AppContext.getEngineFactory().getInstance();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        if(mEngine != null) {
            mEngine.destroy();
        }
    }

    protected static void addAction(Context context, List<GuidedAction> actions, long id, String title, String desc) {
        addAction(context, actions, id, title, desc, false);
    }

    protected static void addAction(Context context, List<GuidedAction> actions, long id, String title, String desc, boolean editable) {
        actions.add(new GuidedAction.Builder(context)
                .id(id)
                .title(title)
                .description(desc)
                .editable(editable)
                .build());
    }

    protected static void addDropDownAction(Context context,List<GuidedAction> actions,long id, String title, String desc,List<GuidedAction> selectionActions){
        actions.add(new GuidedAction.Builder(context)
                .id(id)
                .title(title)
                .description(desc)
                .subActions(selectionActions)
                .build());
    }

    protected void moveToNextFragment(GuidedStepFragment fragment) {
        fragment.setArguments(getArguments());
        add(getFragmentManager(), fragment);
    }
}

