package org.acestream.livechannels.setup;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import java.util.List;

import org.acestream.livechannels.R;

/**
 * Created by barbarian on 26.07.16.
 */
public class IntroFragment extends BaseGuidedStepFragment {
    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                getString(R.string.setup_intro_title),
                getString(R.string.setup_intro_description),
                getString(R.string.input_label),
                icon);

        return guidance;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction action = new GuidedAction.Builder(getActivity())
                .title(getString(R.string.setup_begin_title))
                .description(getString(R.string.setup_begin_desc))
                .editable(false)
                .build();

        actions.add(action);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        // Move onto the next step
        GuidedStepFragment fragment = new SyncingFragment();
        fragment.setArguments(getArguments());
        add(getFragmentManager(), fragment);
    }
}
