package org.acestream.livechannels.setup;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.acestream.engine.controller.Callback;
import org.acestream.livechannels.Constants;
import org.acestream.livechannels.R;

import java.util.List;

public class SignInAceStreamFragment extends BaseGuidedStepFragment {
    private static final int ACTION_SIGN_IN = 1;

    private GuidedAction mLoginAction = null;
    private GuidedAction mPasswordAction = null;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

        Drawable icon = getActivity().getDrawable(R.drawable.ic_live_tv);
        //TODO: translate
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                "Sign in",
                "Enter your credentials",
                "Ace Stream",
                icon);

        return guidance;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        mLoginAction = new GuidedAction.Builder(getActivity())
                .title("")
                .description("Your email")
                .editable(true)
                .build();

        mPasswordAction = new GuidedAction.Builder(getActivity())
                .title("")
                .description("Your password")
                .editable(true)
                .build();

        actions.add(mLoginAction);
        actions.add(mPasswordAction);

        addAction(getActivity(), actions, ACTION_SIGN_IN, "Sign In", null);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        // Move onto the next step
        switch((int)action.getId()) {
            case ACTION_SIGN_IN:
                if(mEngine == null) {
                    moveToNextFragment(new SettingsFragment());
                    return;
                }

                String login = mLoginAction.getTitle().toString();
                String password = mPasswordAction.getTitle().toString();

                if(TextUtils.isEmpty(login)) {
                    Toast.makeText(getActivity(), "Please enter email", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(TextUtils.isEmpty(password)) {
                    Toast.makeText(getActivity(), "Please enter password", Toast.LENGTH_SHORT).show();
                    return;
                }

                // remove spaces from login ("smart typing" can add spaces after each ".")
                login = login.replace(" ", "");

                Log.d(Constants.TAG, "setup:sign_in_as: login=" + login + " password=" + password);

                mEngine.signInAceStream(login, password, new Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        Log.d(Constants.TAG, "setup:sign_in_as: result=" + result);
                        if(result) {
                            //TODO: distinguish between initial and later setup
                            moveToNextFragment(new SettingsFragment());
                        }
                        else {
                            //TODO: translate and make better message
                            Toast.makeText(getActivity(), "Sign in failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String err) {
                        Log.e(Constants.TAG, "setup:init: error: " + err);
                        //TODO: translate and make better message
                        Toast.makeText(getActivity(), "Sign in failed", Toast.LENGTH_SHORT).show();
                    }
                });
                break;

        }
    }
}
