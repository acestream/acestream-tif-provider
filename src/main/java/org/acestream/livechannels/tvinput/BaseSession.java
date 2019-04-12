package org.acestream.livechannels.tvinput;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer.util.Util;

import org.acestream.engine.ServiceClient;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.livechannels.BuildConfig;
import org.acestream.livechannels.Constants;
import org.acestream.livechannels.R;
import org.acestream.livechannels.datasource.ChannelsAPI;
import org.acestream.livechannels.datasource.MediaFile;
import org.acestream.livechannels.datasource.PlaybackResponse;
import org.acestream.livechannels.datasource.StatusResponse;
import org.acestream.livechannels.model.Channel;
import org.acestream.livechannels.model.ContentDescriptor;
import org.acestream.livechannels.model.NowPlaying;
import org.acestream.livechannels.model.Program;
import org.acestream.livechannels.utils.TvContractUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static android.content.Context.ACTIVITY_SERVICE;


abstract public class BaseSession
        extends
            android.media.tv.TvInputService.Session
        implements
            Handler.Callback,
            ServiceClient.Callback
{
    protected static final String TAG = "AceStream/BaseSession";
    private static AtomicInteger sSessionCounter = new AtomicInteger();

    // tune delay in milliseconds
    private static final int TUNE_DELAY = 1000;

    private static final int MAINTAIN_PLAYBACK_INTERVAL = 1000;

    private static final int MSG_UPDATE_SESSION = 1000;
    private static final int MSG_PLAYBACK_FAILED = 1001;
    private static final int MSG_STOP_ENGINE_SESSION = 1002;
    private static final int MSG_TUNE = 1003;
    private static final int MSG_PLAYBACK_FAILED_ON_NETWORK_ERROR = 1004;

    protected final Context mContext;
    protected final Handler mServiceHandler;
    protected final Handler mSessionHandler;
    protected final int mSessionNumber;
    private final TvInputManager mTvInputManager;
    private ServiceClient mServiceClient;
    private boolean mReleased = false;

    protected Surface mSurface;
    protected int mSurfaceWidth = 0;
    protected int mSurfaceHeight = 0;
    protected float mVolume;

    private boolean reloadingStream = false;
    private boolean aceEngineReady = false;
    // true if engine was started at least once
    private boolean mEngineWasStarted = false;
    protected PlayChannelRunnable mPlayChannelRunnable;

    // current engine session
    protected NowPlaying mEngineSession = null;

    // the channel we're going to tune
    private Uri mChannelToTune = null;

    // overlay
    private RelativeLayout mOverlayView;
    private TextView mMsgView;
    private TextView mDebugView;

    private Runnable mMaintainPlaybackTask = new Runnable() {
        @Override
        public void run() {
            try {
                maintainPlayback();
            }
            catch(Throwable e) {
                Log.e(TAG, "maintain playback failed", e);
            }
            finally {
                mServiceHandler.postDelayed(mMaintainPlaybackTask, MAINTAIN_PLAYBACK_INTERVAL);
            }

        }
    };

    public BaseSession(Context context, Handler serviceHandler) {
        super(context);
        mContext = context;

        mServiceHandler = serviceHandler;
        mSessionHandler = new Handler(this);

        mSessionNumber = sSessionCounter.getAndIncrement();
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);

        try {
            mServiceClient = new ServiceClient("LiveSession", mContext, this, true);
            mServiceClient.bind();
        }
        catch (ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "AceStream is not installed");
        }

        setOverlayViewEnabled(true);

        mServiceHandler.postDelayed(mMaintainPlaybackTask, MAINTAIN_PLAYBACK_INTERVAL);
    }

    @Override
    public boolean onTune(Uri channelUri) {
        Log.d(Constants.TAG, "session:" + mSessionNumber + ":tune: uri=" + channelUri.toString());

        // Remember the channel uri and start it after |TUNE_DELAY| if the user hasn't selected
        // another channel.
        mChannelToTune = channelUri;
        Message msg = mSessionHandler.obtainMessage(MSG_TUNE, channelUri);
        mSessionHandler.sendMessageDelayed(msg, TUNE_DELAY);

        return true;
    }

    /**
     * Return true if channel was changed.
     *
     * @param uri New channel uri
     */
    protected boolean setCurrentChannelUri(Uri uri) {
        Uri currentChannelUri = null;

        if(mEngineSession != null) {
            currentChannelUri = mEngineSession.getChannelUri();
        }

        if(currentChannelUri == null && uri == null) {
            return false;
        }
        else if(currentChannelUri != null && uri != null && uri.equals(currentChannelUri)) {
            return false;
        }

        Log.d(Constants.TAG, "session: channel uri changed: session=" + mEngineSession + " uri=" + currentChannelUri + "->" + uri);

        // stop current session when switching to another
        if(mEngineSession != null) {
            scheduleStopEngineSession(mEngineSession);
        }

        if(uri == null) {
            mEngineSession = null;
        }
        else {
            mEngineSession = new NowPlaying(uri);
        }

        return true;
    }

    protected void scheduleStopEngineSession(NowPlaying session) {
        Log.v(TAG, "scheduleStopEngineSession: session=" + session);
        mSessionHandler.removeMessages(MSG_UPDATE_SESSION);
        mSessionHandler.obtainMessage(MSG_STOP_ENGINE_SESSION, session).sendToTarget();
    }

    protected void stopEngineSession(NowPlaying session) {
        if(session == null) {
            return;
        }
        Log.d(Constants.TAG, "session:msg: stop engine session: session=" + session.toString());

        // nothing to stop if not started
        if(session.isStarted()) {
            ChannelsAPI api = new ChannelsAPI();
            api.sendCommandAsync(session.getCommandUrl("stop"), new ChannelsAPI.CommandResponseCallback() {
                @Override
                public void onResponse(String response) {
                    Log.d(Constants.TAG, "session: stop success: response=" + response);
                }

                @Override
                public void onFailure(String error) {
                    Log.e(Constants.TAG, "session: stop failed: err=" + error);
                }
            });
        }
    }

    protected void reloadStream() {
        Log.d(Constants.TAG, "session:reload");
        stopPlayback();

        // Notify we are busy tuning
        //notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

        // start engine
        // channel will be started when we got "engine ready" event
        reloadingStream = true;
        aceEngineReady = false;

        // reset "playbackStarted" flag
        if(mEngineSession != null) {
            mEngineSession.playbackStarted = false;
        }

        if(mServiceClient != null){
            try {
                mServiceClient.startEngine();
            }
            catch (ServiceClient.ServiceMissingException e) {
                Log.e(TAG, "AceStream is not installed");
            }
        }
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        //Log.v(TAG, "Session onSetCaptionEnabled: " + enabled + " (" + mSessionNumber + ")");
    }

    @Override
    public void onRelease() {
        if(mReleased) {
            Log.d(Constants.TAG, "session:" + mSessionNumber + ": already released");
            return;
        }

        mServiceHandler.removeCallbacks(mMaintainPlaybackTask);

        Log.d(Constants.TAG, "session:" + mSessionNumber + ": release");
        aceEngineReady = false;
        mReleased = true;

        if (mServiceHandler != null) {
            mServiceHandler.removeCallbacks(mPlayChannelRunnable);
        }

        final NowPlaying session = mEngineSession;
        mEngineSession = null;

        // don't release player on main thread
        mServiceHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onRelease: stop playback from service thread");
                stopPlayback(true, true);
                stopEngineSession(session);
            }
        });

        if(mServiceClient != null) {
            mServiceClient.unbind();
            mServiceClient = null;
        }
    }

    public void notifyStreamUnavailable() {
        Log.d(TAG, "Notifying video is unavailable");
        Toast.makeText(mContext, R.string.video_stream_not_availaible, Toast.LENGTH_SHORT).show();
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
    }

    @Override
    public void notifyVideoAvailable() {
        //Log.d(TAG, "session: notify video available");
        super.notifyVideoAvailable();
    }

    @Override
    public void notifyVideoUnavailable(int reason) {
        Log.d(TAG, "session: notify video unavailable: reason=" + Integer.toString(reason));
        super.notifyVideoUnavailable(reason);
    }

    @Override
    public void notifyTracksChanged(List<TvTrackInfo> tracks) {
        super.notifyTracksChanged(tracks);
        Log.d(TAG, "Notifying tracks changed");
        for(TvTrackInfo ti:tracks){
            Log.d(TAG,String.format("track id %s",ti.getId()));
            if(ti.getType()==TvTrackInfo.TYPE_VIDEO){
                Log.d(TAG,String.format("video size  %dx%d",ti.getVideoWidth(),ti.getVideoHeight()));
            }
        }
    }

    @Override
    public void notifyTrackSelected(int type, String trackId) {
        if (type == TvTrackInfo.TYPE_VIDEO) {
            Log.d(TAG, "Notifying video track selected: " + trackId);
        } else if (type == TvTrackInfo.TYPE_AUDIO) {
            Log.d(TAG, "Notifying audio track selected: " + trackId);
        } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
            Log.d(TAG, "Notifying subtitle track selected: " + trackId);
        }
        super.notifyTrackSelected(type, trackId);
    }

    @Override
    public boolean handleMessage(Message msg) {
        final NowPlaying session;
        final ChannelsAPI api;
        long timeStart = System.currentTimeMillis();

        if(mReleased) {
            if(isDebugLoggingEnabled()) {
                Log.v(TAG, "handleMessage: session is released: what=" + msg.what);
            }
            return false;
        }

        if(isDebugLoggingEnabled()) {
            Log.v(TAG, "handleMessage: what=" + msg.what);
        }

        switch (msg.what) {
            case MSG_TUNE:
                tuneChannelIfNotChanged((Uri)msg.obj);
                break;

            case MSG_UPDATE_SESSION:
                session = (NowPlaying) msg.obj;

                if(session.getStatusUrl() == null) {
                    Log.v(TAG, "session:status: missing status url");
                    break;
                }

                if(isDebugLoggingEnabled()) {
                    Log.d(TAG, "session:status: session=" + session.toString());
                }

                api = new ChannelsAPI();
                api.getStatusAsync(session.getStatusUrl(), new ChannelsAPI.StatusResponseCallback() {
                    @Override
                    public void onResponse(StatusResponse status) {
                        Date now = new Date();
                        long age = now.getTime() - session.getStartedAt().getTime();
                        // want seconds
                        age /= 1000;

                        if(isDebugLoggingEnabled()) {
                            Log.d(TAG, "session:status: started=" + session.playbackStarted + " age=" + age + " status=" + (status == null ? "null" : status.status));
                        }

                        if(!session.playbackStarted && status != null && status.status != null && status.status.equals("dl")) {
                            session.playbackStarted = true;

                            //displayMsg(null);
                            //notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);

                            // start playback on service handler thread
                            mServiceHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    playChannel(session);
                                }
                            });
                        }

                        if(status != null
                                && status.status != null
                                && status.status.equals("prebuf")
                                && status.peers == 0
                                && age >= 60
                                ) {
                            //TODO: translate
                            displayMsg("No active peers", false);
                        }
                        else {
                            displayEngineStatus(status, session);
                        }

                        // continue updating status
                        Message tosend = mSessionHandler.obtainMessage(MSG_UPDATE_SESSION, session);
                        mSessionHandler.sendMessageDelayed(tosend, 1000);
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(Constants.TAG, "session: status failed: err=" + error);
                    }
                });
                break;

            case MSG_STOP_ENGINE_SESSION:
                session = (NowPlaying) msg.obj;
                stopEngineSession(session);
                break;

            case MSG_PLAYBACK_FAILED: {
                    final String error = (String) msg.obj;
                    onPlaybackFailed(error, false);
                }
                break;

            case MSG_PLAYBACK_FAILED_ON_NETWORK_ERROR: {
                    final String error = (String) msg.obj;
                    onPlaybackFailed(error, true);
                }
                break;
        }

        if(isDebugLoggingEnabled()) {
            Log.v(TAG, "handleMessage: done: what=" + msg.what + " time=" + (System.currentTimeMillis() - timeStart));
        }

        return false;
    }

    // ServiceClient.Callback interface
    @Override
    public void onConnected(IAceStreamEngine service) {
        Log.d(TAG, "onConnected");
        aceEngineReady = true;
        mEngineWasStarted = true;
        Log.d(Constants.TAG, "session: engine ready");
        onEngineReady();
    }

    @Override
    public void onFailed() {
        Log.d(TAG, "onFailed");
        onEngineStopped();
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
        onEngineStopped();
    }

    @Override
    public void onUnpacking() {
        aceEngineReady = false;
        Log.d(Constants.TAG, "session: engine unpacking");
        onEngineUnpacking();
    }

    @Override
    public void onStarting() {
        aceEngineReady = false;
        onEngineStarting();
        Log.d(Constants.TAG, "session: engine starting");
    }

    @Override
    public void onStopped() {
        aceEngineReady = false;
        Log.d(Constants.TAG, "session: engine stopped: wasStarted=" + mEngineWasStarted);
        if(mEngineWasStarted) {
            // if engine was started at lease once then reload stream
            reloadStream();
        }
        else {
            onEngineStopped();
        }
    }

    @Override
    public void onPlaylistUpdated() {
    }

    @Override
    public void onEPGUpdated() {
    }

    @Override
    public void onSettingsUpdated() {
    }

    @Override
    public void onRestartPlayer() {
    }

    private void tuneChannelIfNotChanged(Uri channel) {
        if(mChannelToTune == null) {
            // no tune?
            Log.w(Constants.TAG, "session:post-tune: no channel");
            return;
        }

        if(!channel.equals(mChannelToTune)) {
            // channel was changed
            Log.d(Constants.TAG, "session:post-tune: changed");
            return;
        }

        // reset
        mChannelToTune = null;

        Log.d(Constants.TAG, "session:post-tune: load: uri=" + channel);
        if(setCurrentChannelUri(channel)) {
            reloadStream();
        }
    }

    private void onEngineUnpacking() {
        //TODO: translate
        displayMsg("Unpacking...", false);
    }

    private void onEngineStopped() {
        //TODO: translate
        displayMsg("Engine stopped", true);
    }

    private void onEngineStarting() {
        //TODO: translate
        displayMsg("Starting...", false);
    }

    private void onEngineReady() {
        Log.d(Constants.TAG, "session:onEngineReady: reloading=" + reloadingStream);

        if(reloadingStream) {
            if(mEngineSession != null) {
                Log.d(TAG, "onEngineReady: start engine session");
                displayMsg("Starting...", false);
                mServiceHandler.removeCallbacks(mPlayChannelRunnable);
                mPlayChannelRunnable = new PlayChannelRunnable(mEngineSession);
                mServiceHandler.post(mPlayChannelRunnable);
            }
            else {
                Log.d(TAG, "onEngineReady: missing engine session");
            }
            reloadingStream = false;
        }
    }

    private void onPlaybackFailed(String error, boolean isNetworkError) {
        Log.d(TAG, "onPlaybackFailed: engineReady=" + aceEngineReady + " isNetworkError=" + isNetworkError);

        if(aceEngineReady && !isNetworkError) {
            displayMsg(translateEngineError(error), true);
            resetEngineSession();
        }
        else {
            // Do nothing, stream will be reloaded in onEngineStopped
            // We assume that network error can be cause only by stopped engine.
        }
    }

    /**
     * Translate some known engine errors.
     *
     * @param errorMessage original engine error message
     * @return Translated message
     */
    private String translateEngineError(String errorMessage) {
        String translated;
        Resources res = mContext.getResources();

        switch(errorMessage) {
            case "failed to get manifest":
                translated = res.getString(R.string.error_broadcast_is_unavailable);
                break;
            default:
                translated = errorMessage;
        }

        return translated;
    }

    private void resetEngineSession() {
        Log.d(Constants.TAG, "reset engine session");
        //TODO: check if session was started on engine and stop it in such case
        mEngineSession = null;
    }

    private class PlayChannelRunnable implements Runnable {
        private final NowPlaying mSession;
        private final ChannelsAPI mApi = new ChannelsAPI();
        private final boolean mDisableP2P;

        public PlayChannelRunnable(NowPlaying session) {
            mSession = session;
            mDisableP2P = getBoolSharedPref("disable_p2p", false);
        }

        @Override
        public void run() {
            Uri channelUri = mSession.getChannelUri();
            Channel channel = TvContractUtils.getChannelFromChannelUri(mContext, channelUri);
            Program program = TvContractUtils.getCurrentProgram(mContext, channelUri);

            if (channel != null) {
                mSession.setChannel(channel);
                mSession.setProgram(program);

                String outputFormat = getSharedPref("output_format_live", "http");

                Log.d(Constants.TAG, "session: start playback: "
                    + " url=" + channel.getTransportFileUrl()
                    + " infohash=" + channel.getInfohash()
                    + " content_id=" + channel.getContentId()
                    + " uri=" + channelUri
                    + " disableP2P=" + mDisableP2P
                    + " outputFormat=" + outputFormat
                );

                // make content descriptor
                //TODO: add direct content
                final ContentDescriptor descriptor = new ContentDescriptor();
                if(!TextUtils.isEmpty(channel.getTransportFileUrl())) {
                    descriptor.type = "url";
                    descriptor.id = channel.getTransportFileUrl();
                }
                else if(!TextUtils.isEmpty(channel.getContentId())) {
                    descriptor.type = "content_id";
                    descriptor.id = channel.getContentId();
                }
                else if(!TextUtils.isEmpty(channel.getInfohash())) {
                    descriptor.type = "infohash";
                    descriptor.id = channel.getInfohash();
                }

                if("auto".equals(outputFormat)) {
                    outputFormat = "http";
                }
                else if("original".equals(outputFormat)) {
                    try {
                        // we analyze only the first file for now
                        Map<String, MediaFile> mediaFiles = mApi.getMediaFiles(descriptor);
                        for (Map.Entry<String, MediaFile> item : mediaFiles.entrySet()) {
                            MediaFile mediaFile = item.getValue();
                            Log.d(TAG, "media file: index=" + item.getKey() + " type=" + mediaFile.type + " mime=" + mediaFile.mime);

                            if(TextUtils.equals(mediaFile.mime, Constants.HLS_MIME_TYPE)) {
                                outputFormat = "hls";
                            }
                            else {
                                outputFormat = "http";
                            }

                            Log.d(TAG, "set output format: mime=" + mediaFile.mime + " outputFormat=" + outputFormat);

                            break;
                        }
                    } catch (ChannelsAPI.ApiError e) {
                        Log.e(TAG, "failed to get media files: " + e.getMessage());
                    }
                }

                mSession.setContentDescriptor(descriptor);
                mSession.setOutputFormat(outputFormat);

                mApi.startPlayback(
                    descriptor,
                    outputFormat,
                    mDisableP2P,
                    new StartPlaybackCallback(descriptor, new Date()));
            }
            else {
                Log.w(Constants.TAG, "session: no channel info: uri=" + channelUri);
                mSessionHandler.obtainMessage(MSG_PLAYBACK_FAILED, "Start failed").sendToTarget();
            }
        }

        private class StartPlaybackCallback implements ChannelsAPI.StartPlaybackCallback {
            private final ContentDescriptor mDescriptor;
            private final Date mStartTime;

            public StartPlaybackCallback(ContentDescriptor descriptor, Date startTime) {
                mDescriptor = descriptor;
                mStartTime = startTime;
            }
            @Override
            public void onResponse(PlaybackResponse response) {
                Log.d(Constants.TAG, "session: start done:"
                        + " time=" + (new Date().getTime() - mStartTime.getTime())
                        + " descriptor=" + mDescriptor.toString()
                        + " outputFormat=" + mSession.getOutputFormat()
                        + " isEncrypted=" + response.is_encrypted
                );

                if(mEngineSession == null || !mSession.equals(mEngineSession)) {
                    Log.d(Constants.TAG, "session changed on start: this=" + mSession + " curr=" + mEngineSession);
                    return;
                }

                if(mSession.getOutputFormat().equals("http") && response.is_encrypted == 1) {
                    // restart in HLS
                    Log.d(TAG, "session: restart encrypted stream in HLS");
                    mSession.setOutputFormat("hls");
                    mApi.startPlayback(
                        mDescriptor,
                        "hls",
                        mDisableP2P,
                        new StartPlaybackCallback(mDescriptor, new Date()));
                    return;
                }

                mSession.setStartedAt(new Date());
                mSession.setInfohash(response.infohash);
                mSession.setStreamUrl(response.playback_url);
                mSession.setStatusUrl(response.stat_url);
                mSession.setEventUrl(response.event_url);
                mSession.setCommandUrl(response.command_url);
                mSession.setIsDirect(response.is_direct);

                if(response.is_direct) {
                    Log.d(TAG, "start direct media playback");

                    // clear overlay message
                    displayMsg(null, true);

                    // start playback on service handler thread
                    mServiceHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            playChannel(mSession);
                        }
                    });
                    return;
                }

                mSessionHandler.removeMessages(MSG_UPDATE_SESSION);
                mSessionHandler.obtainMessage(MSG_UPDATE_SESSION, mSession).sendToTarget();
            }

            @Override
            public void onFailure(String error) {
                Log.e(Constants.TAG, "session: start failed: " + error);

                if(mEngineSession == null || !mSession.equals(mEngineSession)) {
                    Log.d(Constants.TAG, "session changed on failure: this=" + mSession + " curr=" + mEngineSession);
                    return;
                }

                mSessionHandler.obtainMessage(MSG_PLAYBACK_FAILED, error).sendToTarget();

            }

            @Override
            public void onNetworkError(String error) {
                Log.e(Constants.TAG, "session: start failed on network error: " + error);

                if(mEngineSession == null || !mSession.equals(mEngineSession)) {
                    Log.d(Constants.TAG, "session changed on failure: this=" + mSession + " curr=" + mEngineSession);
                    return;
                }

                mSessionHandler.obtainMessage(MSG_PLAYBACK_FAILED_ON_NETWORK_ERROR, error).sendToTarget();
            }
        }
    }

    /**
     * Makes a best guess to infer the type from a media {@link Uri} and an optional overriding file
     * extension.
     *
     * @param uri The {@link Uri} of the media.
     * @param fileExtension An overriding file extension.
     * @return The inferred type.
     */
    private static int inferContentType(Uri uri, String fileExtension) {
        String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension
                : uri.getLastPathSegment();
        return Util.inferContentType(lastPathSegment);
    }

    @Override
    public View onCreateOverlayView() {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mOverlayView = (RelativeLayout) inflater.inflate(R.layout.tv_overlay, null);
        mMsgView = (TextView) mOverlayView.findViewById(R.id.msg_view);
        mDebugView = (TextView) mOverlayView.findViewById(R.id.debug_view);
        return mOverlayView;
    }

    protected boolean getShowDebugInfo() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getBoolean("show_debug_info", false);
    }

    protected boolean isDebugLoggingEnabled() {
        return PreferenceManager
                .getDefaultSharedPreferences(mContext)
                .getBoolean("enable_debug_logging", BuildConfig.enableDebugLogging);
    }

    protected boolean isTuning() {
        return (mChannelToTune != null);
    }

    private void displayMsg(String msg, boolean showWhenPlaying) {
        boolean showOverlay;
        boolean showTransparent;
        boolean isTuning = isTuning();
        boolean isPlaying = isPlaying();
        boolean isBuffering = isBuffering();
        boolean showDebugInfo = getShowDebugInfo();

        if(mEngineSession != null && mEngineSession.isDirect()) {
            // force "playing" flag to show transparent overlay
            isPlaying = true;
        }

        if(mOverlayView != null && mMsgView != null) {
            if(isTuning) {
                // we're tuning: show overlay but hide message - we need blank window
                msg = "";
                showOverlay = true;
                showTransparent = false;
            }
            else {
                if(isPlaying) {
                    showOverlay = !TextUtils.isEmpty(msg) && showWhenPlaying;
                    showTransparent = true;
                }
                else {
                    // always show overlay when not playing
                    showOverlay = true;
                    showTransparent = false;
                }
            }

            if(msg == null) {
                msg = "";
            }

            mMsgView.setText(msg);

            if(showDebugInfo) {
                // always show overlay when debug info is requested
                showOverlay = true;
            }

            if(showOverlay) {
                // transparent overlay when playing
                if(showTransparent) {
                    mOverlayView.setBackgroundColor(Color.TRANSPARENT);
                }
                else {
                    mOverlayView.setBackgroundColor(mContext.getResources().getColor(R.color.tvactivity_background));
                }

                mOverlayView.setVisibility(View.VISIBLE);
            }
            else{
                mOverlayView.setVisibility(View.INVISIBLE);
            }

            if(!isTuning) {
                if(isBuffering && TextUtils.isEmpty(msg)) {
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                }
                else {
                    // need this to show overlay
                    notifyVideoAvailable();
                }
            }
        }
    }

    private float readUsage() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" ");

            long idle1 = Long.parseLong(toks[5]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (Exception e) {}

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" ");

            long idle2 = Long.parseLong(toks[5]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException ex) {
            Log.v(TAG, "error", ex);
        }

        return 0;
    }

    private void displayEngineStatus(StatusResponse status, final NowPlaying session) {
        String msg = null;
        String debugMsg = null;
        boolean addInfohash = true;
        boolean showDebugInfo = getShowDebugInfo();

        if(showDebugInfo) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);
            activityManager.getMemoryInfo(mi);
            double totalMegs = mi.totalMem / 0x100000L;
            double availableMegs = mi.availMem / 0x100000L;

            //Percentage can be calculated for API 16+
            double percentAvail = mi.availMem / (double) mi.totalMem * 100.0;
            double percentUsed = 100 - percentAvail;

            float cpu = readUsage();

            debugMsg = String.format(
                    "CPU:%d%%\nRAM:%d%%",
                    Math.round(cpu * 100),
                    Math.round(percentUsed)
            );
        }


        if(status != null && status.status != null) {
            String statusName = "?";
            boolean showMessage = true;

            switch(status.status) {
                case "prebuf":
                    statusName = "Prebuffering";
                    break;
                case "buf":
                    statusName = "Buffering";
                    break;
                case "dl":
                    statusName = "dl";
                    showMessage = false;
                    break;
                default:
                    showMessage = false;
                    break;
            }

            if(showMessage) {
                msg = String.format(
                        "%s %d%%\nPeers: %d\nDL: %d Kb/s",
                        statusName,
                        status.progress,
                        status.peers,
                        status.speed_down);
            }

            if(showDebugInfo) {
                debugMsg += String.format(
                        "\nPeers:%d DL:%d UL:%d",
                        status.peers,
                        status.speed_down,
                        status.speed_up
                );
            }
        }
        else {
            //TODO: translate
            msg = "Starting...";
        }

        // add infohash for debug
        //if(addInfohash && session != null) {
        //    String infohash = session.getChannel().getInfohash();
        //    if(infohash != null) {
        //        if(msg == null) {
        //            msg = "";
        //        }
        //        msg = infohash + "\n" + msg;
        //    }
        //}

        displayMsg(msg, false);

        if(showDebugInfo) {
            if(mEngineSession != null) {
                debugMsg += "\nOutput: " + mEngineSession.getOutputFormat();
            }
            mDebugView.setText(debugMsg);
            mDebugView.setVisibility(View.VISIBLE);
        }
        else {
            mDebugView.setVisibility(View.GONE);
        }
    }

    protected String getSharedPref(String name, String defaultValue) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            return prefs.getString(name, defaultValue);
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to get shared preference", e);
            return null;
        }
    }

    protected boolean getBoolSharedPref(String name, boolean defaultValue) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            return prefs.getBoolean(name, defaultValue);
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to get shared preference", e);
            return defaultValue;
        }
    }

    // abstract methods
    abstract protected void playChannel(NowPlaying nowPlaying);
    abstract protected void stopPlayback();
    abstract protected void stopPlayback(boolean releasePlayer, boolean releaseLibrary);
    abstract protected boolean isPlaying();
    abstract protected boolean isBuffering();
    abstract protected void maintainPlayback();
}
