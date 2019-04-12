package org.acestream.livechannels.tvinput;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.acestream.livechannels.Constants;
import org.acestream.livechannels.model.AppContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;


public class TvInputService extends android.media.tv.TvInputService {
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private List<BaseSession> mSessions;

    @Override
    public void onCreate() {
        super.onCreate();

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mSessions = new ArrayList<>();

        Log.d(Constants.TAG, "tvinput:onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(Constants.TAG, "tvinput: onDestroy");

        for(BaseSession session: mSessions) {
            session.onRelease();
        }

        mHandlerThread.quit();
        mHandlerThread = null;
        mHandler = null;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.d(Constants.TAG, "tvinput: create session: inputId=" + inputId);

        BaseSession session;
        try {
            Constructor<?> ctor = AppContext.getSessionClass().getConstructor(Context.class, Handler.class);
            session = (BaseSession)ctor.newInstance(this, mHandler);
        }
        catch (Throwable e) {
            throw new IllegalStateException("Failed to create session", e);
        }

        mSessions.add(session);

        return session;
    }

    public static String getPlaylistHash(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVCHANNELS, Context.MODE_PRIVATE);

        return sharedPreferences.getString(Constants.KEY_PLAYLIST_HASH, null);
    }

    public static void setPlaylistHash(Context context, String playlistHash) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVCHANNELS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.KEY_PLAYLIST_HASH, playlistHash);
        editor.apply();
    }
}
