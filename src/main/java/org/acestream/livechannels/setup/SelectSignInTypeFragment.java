package org.acestream.livechannels.setup;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import android.util.Log;

import org.acestream.engine.controller.Callback;
import org.acestream.livechannels.Constants;
import org.acestream.livechannels.R;

import java.util.List;

public class SelectSignInTypeFragment extends BaseGuidedStepFragment {
    private static final int ACTION_GOOGLE = 0;
    private static final int ACTION_ACE_STREAM = 1;
    private static final int ACTION_CANCEL = 2;

    private static final int RC_SIGN_IN = 0;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);
        //TODO: translate
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                "Sign in",
                "Select your account type",
                "Ace Stream",
                icon);

        return guidance;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        addAction(getActivity(), actions, ACTION_GOOGLE, "Google", "I have a Google account");
        addAction(getActivity(), actions, ACTION_ACE_STREAM, "Ace Stream", "I have an Ace Stream account");
        addAction(getActivity(), actions, ACTION_CANCEL, "Cancel", null);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        TvInputSetupActivity activity = (TvInputSetupActivity)getActivity();
        switch((int)action.getId()) {
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
                            Intent signInIntent = mEngine.getGoogleSignInIntent(SelectSignInTypeFragment.this.getActivity());
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
            case ACTION_CANCEL:
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
                break;
        }
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
                    if(result) {
                        moveToNextFragment(new SettingsFragment());
                    }
                }

                @Override
                public void onError(String err) {
                    Log.d(Constants.TAG, "setup:sign_in_google_intent: err=" + err);
                }
            });
        }
    }
}
