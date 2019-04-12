package org.acestream.livechannels.setup;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import java.util.List;

import org.acestream.livechannels.R;

/**
 * Created by barbarian on 26.07.16.
 */
public class SyncCompletedFragment extends BaseGuidedStepFragment {
    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                getString(R.string.sync_completed_title),
                getString(R.string.sync_completed_desc),
                getString(R.string.input_label),
                icon);

        return guidance;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction action = new GuidedAction.Builder(getActivity())
                .title(getString(R.string.setup_finished))
                .description(getString(R.string.setup_finished_desc))
                .editable(false)
                .build();

        actions.add(action);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
    }
}
