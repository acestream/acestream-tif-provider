package org.acestream.livechannels.setup;

import android.app.Activity;
import android.os.Bundle;
import androidx.leanback.app.GuidedStepFragment;

import org.acestream.engine.controller.Engine;
import org.acestream.livechannels.model.AppContext;
import org.acestream.livechannels.utils.TvContractUtils;


public class TvInputSetupActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GuidedStepFragment fragment;
        Engine engine = AppContext.getEngineFactory().getInstance();

        if(engine != null) {
            fragment = new InitFragment();
        }
        else {
            if(TvContractUtils.haveChannels(getContentResolver())) {
                fragment = new SettingsFragment();
            }
            else {
                fragment = new IntroFragment();
            }
        }

        fragment.setArguments(getIntent().getExtras());
        GuidedStepFragment.addAsRoot(this, fragment, android.R.id.content);
    }
}

