package org.acestream.livechannels.setup;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidedActionsStylist;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.acestream.engine.controller.Callback;
import org.acestream.engine.controller.EventListener;
import org.acestream.livechannels.Constants;
import org.acestream.livechannels.R;

import java.util.ArrayList;
import java.util.List;

public class InitFragment extends BaseGuidedStepFragment
    implements EventListener
{
    private static final String TAG = InitFragment.class.getSimpleName();

    private static final int ACTION_UPDATE_CHANNELS = 201;

    private static final int ACTION_GOOGLE = 101;
    private static final int ACTION_ACE_STREAM = 102;

    private static final int ACTION_CANCEL = 301;

    private static final int RC_SIGN_IN = 0;

    @Override
    public void onStart() {
        super.onStart();

        if(mEngine == null) {
            moveToNextFragment(new SettingsFragment());
        }
        else {
            mEngine.addListener(this);
            mEngine.signIn();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(mEngine != null) {
            mEngine.removeListener(this);
        }
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return new GuidanceStylist() {
            @Override
            public int onProvideLayoutId() {
                return R.layout.progress_guidance;
            }
        };
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);
        //TODO: translate
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                "Init engine",
                "This will take few seconds...",
                "Ace Stream",
                icon);

        return guidance;
    }

    @Override
    public void onSignIn(boolean success, boolean gotError) {
        Log.d(Constants.TAG, "setup:init:onSignIn: success=" + success + " gotError=" + gotError);

        if(gotError) {
            return;
        }

        // hide progress
        View progress = getActivity().findViewById(R.id.guidance_progress);
        TextView title = (TextView)getActivity().findViewById(R.id.guidance_title);
        TextView description = (TextView)getActivity().findViewById(R.id.guidance_description);
        progress.setVisibility(View.GONE);

        // set actions
        if(success) {
            //TODO: distinguish between initial and later setup

            title.setText(getString(R.string.settings_title));
            description.setText(getString(R.string.setings_description));

            List<GuidedAction> actions = new ArrayList<>();
            addAction(getActivity(),actions, ACTION_UPDATE_CHANNELS, getString(R.string.setup_update_channels), getString(R.string.setup_update_channels_desc));
            addAction(getActivity(),actions, ACTION_CANCEL, getString(R.string.setup_cancel), getString(R.string.setup_cancel_desc));
            setActions(actions);
        }
        else {
            //TODO: translate
            title.setText("Sign In");
            description.setText("Select your account type");

            List<GuidedAction> actions = new ArrayList<>();
            addAction(getActivity(), actions, ACTION_GOOGLE, "Google", "I have a Google account");
            addAction(getActivity(), actions, ACTION_ACE_STREAM, "Ace Stream", "I have an Ace Stream account");
            addAction(getActivity(),actions, ACTION_CANCEL, getString(R.string.setup_cancel), getString(R.string.setup_cancel_desc));
            setActions(actions);
        }
    }

    @Override
    public void onGoogleSignInAvaialble(boolean available) {
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        switch ((int) action.getId()){
            case ACTION_GOOGLE:
                if(mEngine != null) {
                    // first try silent sign in
                    mEngine.signInGoogleSilent(new Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            Log.d(Constants.TAG, "setup:sign_in_google: result=" + result);
                        }

                        @Override
                        public void onError(String err) {
                            Log.d(Constants.TAG, "setup:sign_in_google: err=" + err);
                            // try sign in from intent
                            Intent signInIntent = mEngine.getGoogleSignInIntent(InitFragment.this.getActivity());
                            if(signInIntent == null) {
                                Log.e(Constants.TAG, "setup:sign_in_google: null intent");
                            }
                            else {
                                startActivityForResult(signInIntent, RC_SIGN_IN);
                            }
                        }
                    });
                }
                break;
            case ACTION_ACE_STREAM:
                moveToNextFragment(new SignInAceStreamFragment());
                break;
            case ACTION_UPDATE_CHANNELS:
                syncChannels();
                break;
            case ACTION_CANCEL:
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

    @Override
    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);
        Log.d(Constants.TAG, "setup:onActivityResult: requestCode=" + requestCode + " responseCode=" + responseCode);
        if (requestCode == RC_SIGN_IN) {
            mEngine.signInGoogleFromIntent(intent, new Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    Log.d(Constants.TAG, "setup:sign_in_google_intent: result=" + result);
                    // We do nothing here becase in the case of successfull auth
                    // onSignIn will be called.
                }

                @Override
                public void onError(String err) {
                    Log.d(Constants.TAG, "setup:sign_in_google_intent: err=" + err);
                }
            });
        }
    }
}