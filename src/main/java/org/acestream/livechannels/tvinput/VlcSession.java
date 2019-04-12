package org.acestream.livechannels.tvinput;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import org.acestream.engine.controller.Callback;
import org.acestream.sdk.utils.Logger;
import org.acestream.livechannels.Constants;
import org.acestream.livechannels.exoplayer.ExoVideoPlayer;
import org.acestream.livechannels.model.NowPlaying;
import org.acestream.livechannels.utils.TracksManager;
import org.acestream.livechannels.utils.TvContractUtils;
import org.acestream.livechannels.utils.VideoPlaybackSize;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Date;

public class VlcSession
        extends
            BaseSession
        implements
        IVLCVout.Callback,
        IVLCVout.OnNewVideoLayoutListener
{
    protected static final String TAG = "AceStream/VLCS";

    private static final boolean RELEASE_PLAYER_ON_STOP = false;
    private static final boolean RELEASE_LIBVLC_ON_STOP = false;

    public static class VlcState {
        public static final int IDLE = 0;
        public static final int OPENING = 1;
        public static final int PLAYING = 3;
        public static final int PAUSED = 4;
        public static final int STOPPING = 5;
        public static final int ENDED = 6;
        public static final int ERROR = 7;
    }

    protected LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;
    private final TracksManager tracksManager;
    private NowPlaying mLastSession = null;
    private VideoPlaybackSize playbackSize = null;
    private boolean mIsBuffering = false;
    private float mBufferingProgress = 1.0f;

    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public VlcSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");
        tracksManager = new TracksManager();
        initLibVlc();
    }

    protected void initLibVlc() {
        Log.d(Constants.TAG, "vlc: init libvlc");

        ArrayList<String> options = new ArrayList<>();
        options.add("--http-reconnect");
        options.add("--network-caching=2000");

        // it's better to not use deinterlace by default
        options.add("--deinterlace=0");

        // Force opengl off (in "auto" mode libVLC will always use opengl because we don't set
        // subtitles surface and "android_window" fails).
        options.add("--vout=android_display,none");

        // todo:for testing
        options.add("-vv");
        // this option is never set
//        if(getBoolSharedPref("debug_vlc", false)) {
//            options.add("-vv");
//        }

        mLibVLC = new LibVLC(mContext, options);

        String userAgent = getUserAgent(mContext);
        mLibVLC.setUserAgent(userAgent, userAgent);
    }

    public String getUserAgent(Context context) {
        String versionName;

        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }

        return "android-tvchannels/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE
                + ") VLC/" + mLibVLC.version();
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(Constants.TAG, "vlc:" + mSessionNumber + ":onSetSurface");

        try {
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            mSurfaceWidth = metrics.widthPixels;
            mSurfaceHeight = metrics.heightPixels;

            Log.d(Constants.TAG, "vlc: screen size: " + mSurfaceWidth + "x" + mSurfaceHeight);
        }
        catch(Throwable e) {
            Log.e(Constants.TAG, "vlc: failed to get screen size", e);
        }

        mSurface = surface;
        if (mMediaPlayer != null && mSurface != null) {
            final IVLCVout vlcVout = mMediaPlayer.getVLCVout();

            if (vlcVout.areViewsAttached()) {
                // If we're already attached, we need to detach before switching surface
                vlcVout.detachViews();
            }

            vlcVout.setVideoSurface(surface, null);

            if (mSurfaceWidth * mSurfaceHeight != 0) {
                vlcVout.setWindowSize(mSurfaceWidth, mSurfaceHeight);
            }
            vlcVout.removeCallback(this);
            vlcVout.addCallback(this);
            vlcVout.attachViews(this);

            updateSurfaceLayout();
        }

        return true;
    }

    @Override
    public void onSurfaceChanged(int format, int width, int height) {
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");

        mVolume = volume;

        // do not interact with media player on the main thread
        mServiceHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.setVolume((int) mVolume * 100);
                    }
                }
                catch(Throwable e) {
                    Log.e(TAG, "failed to set volume", e);
                }
            }
        });
    }

    @Override
    protected void playChannel(NowPlaying nowPlaying) {
        try {
            mLastSession = nowPlaying;

            // Stop any existing playback
            stopPlayback();

            // init LibVLC if it was released
            if (mLibVLC == null) {
                initLibVlc();
            }

            // Prepare the media player
            if (mMediaPlayer == null) {
                mMediaPlayer = createMediaPlayer();
            }

            if (mMediaPlayer != null) {
                prepareMediaPlayer(Uri.parse(nowPlaying.getStreamUrl()));

                // Start the media playback
                Log.d(Constants.TAG, "vlc: start playback: session=: " + nowPlaying.toString());
                mMediaPlayer.play();
            } else {
                Toast.makeText(mContext, "Failed to prepare video", Toast.LENGTH_SHORT).show();
            }
        }
        catch(Throwable e) {
            Log.e(Constants.TAG, "vlc:playChannel: error", e);
        }
    }

    protected void stopPlayback() {
        stopPlayback(RELEASE_PLAYER_ON_STOP, RELEASE_LIBVLC_ON_STOP);
    }

    protected void stopPlayback(boolean releasePlayer, boolean releaseLibVlc) {
        Log.d(Constants.TAG, "vlc:stop: sn=" + mSessionNumber
                + " releasePlayer=" + releasePlayer
                + " releaseLibVlc=" + releaseLibVlc
        );
        Date startTime = new Date();

        if(releasePlayer) {
            playbackSize = null;
            if (mMediaPlayer != null) {
                IVLCVout vout = mMediaPlayer.getVLCVout();
                if (vout != null) {
                    vout.removeCallback(this);
                    if (vout.areViewsAttached()) {
                        vout.detachViews();
                    }
                }

                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }

            if(releaseLibVlc) {
                if (mLibVLC != null) {
                    mLibVLC.release();
                    mLibVLC = null;
                }
            }
        }
        else {
            // don't release, just stop
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
            }
        }

        Log.d(TAG, "vlc:stop done: time=" + (new Date().getTime() - startTime.getTime()));
    }

    private MediaPlayer createMediaPlayer() {
        Log.d(Constants.TAG, "vlc: create player");

        // Create and prep the MediaPlayer instance
        MediaPlayer mediaPlayer = new MediaPlayer(mLibVLC);
        mediaPlayer.setEventListener(new MediaPlayerEventListener());

        // explicitly set audio device
        mediaPlayer.setAudioOutput("android_audiotrack");
        mediaPlayer.setAudioOutputDevice("pcm");

        if (mSurface != null) {
            Log.d(Constants.TAG, "vlc: create player: setup surface (size=" + mSurfaceWidth + "x" + mSurfaceHeight + ")");
            IVLCVout vlcOut = mediaPlayer.getVLCVout();

            if (vlcOut.areViewsAttached()) {
               vlcOut.detachViews();
            }

            vlcOut.setVideoSurface(mSurface, null);

            if (mSurfaceWidth * mSurfaceHeight != 0) {
                vlcOut.setWindowSize(mSurfaceWidth, mSurfaceHeight);
            }

            vlcOut.removeCallback(this);
            vlcOut.addCallback(this);
            vlcOut.attachViews(this);
        }
        mediaPlayer.setVolume((int) mVolume * 100);

        return mediaPlayer;
    }

    private void prepareMediaPlayer(Uri videoUri) {
        try {
            String hw = getSharedPref("vlc_hardware_acceleration", null);
            if(hw == null) {
                hw = "auto";
            }

            Log.d(Constants.TAG, "vlc: prepare: hw=" + hw + " uri=" + videoUri);

            if(mMediaPlayer != null) {
                Media currentMedia = new Media(mLibVLC, videoUri);
                currentMedia.setEventListener(new MediaEventListener());

                // setup hardware acceleration
                switch(hw) {
                    case "disabled":
                        currentMedia.setHWDecoderEnabled(false, false);
                        break;
                    case "decoding":
                        currentMedia.setHWDecoderEnabled(true, true);
                        currentMedia.addOption(":no-mediacodec-dr");
                        currentMedia.addOption(":no-omxil-dr");
                        break;
                    case "full":
                        currentMedia.setHWDecoderEnabled(true, true);
                        break;
                }

                mMediaPlayer.setMedia(currentMedia);
            }
        }
        catch (Throwable e) {
            Log.e(Constants.TAG, "vlc: prepare error", e);
            if(mMediaPlayer != null) {
                mMediaPlayer.release();
            }
        }
    }

    private void updateSurfaceLayout() {
        Log.d(TAG, "updateSurfaceLayout");
        mMediaPlayer.setAspectRatio(null);
        mMediaPlayer.setScale(0);
    }

    private class MediaPlayerEventListener implements MediaPlayer.EventListener {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch(event.type) {
                case MediaPlayer.Event.ESAdded:
                    Log.v(Constants.TAG, "vlc:event:ESAdded");
                    break;
                case MediaPlayer.Event.ESDeleted:
                    Log.v(Constants.TAG, "vlc:event:ESDeleted");
                    break;
                case MediaPlayer.Event.ESSelected:
                    if (event.getEsChangedType() == Media.VideoTrack.Type.Video) {
                        updateSurfaceLayout();
                    }
                    break;
                case MediaPlayer.Event.Vout:
                    Log.v(Constants.TAG, "vlc:event:vlcVout");
                    break;
                case MediaPlayer.Event.Playing:
                    Log.v(Constants.TAG, "vlc:event:Playing");
                    updatePlayerState();
                    break;
                case MediaPlayer.Event.TimeChanged:
                case MediaPlayer.Event.PositionChanged:
                    // Don't log these events, VLC fires them all the time...
                    break;
                case MediaPlayer.Event.EndReached:
                    Log.v(Constants.TAG, "vlc:event:EndReached");
                    mIsBuffering = false;
                    mBufferingProgress = 0;
                    updatePlayerState();
                    break;
                case MediaPlayer.Event.Paused:
                    Log.v(Constants.TAG, "vlc:event:Paused");
                    updatePlayerState();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.v(Constants.TAG, "vlc:event:Stopped");
                    mIsBuffering = false;
                    mBufferingProgress = 0;
                    updatePlayerState();
                    break;
                case MediaPlayer.Event.Opening:
                    Log.v(Constants.TAG, "vlc:event:Opening");
                    updatePlayerState();
                    break;
                case MediaPlayer.Event.MediaChanged:
                    Log.v(Constants.TAG, "vlc:event:MediaChanged");
                    break;
                case MediaPlayer.Event.Buffering:
                    Log.v(Constants.TAG, "vlc:event:Buffering: buffering=" + event.getBuffering());
                    mBufferingProgress = event.getBuffering();
                    if(mBufferingProgress >= 100.0) {
                        mIsBuffering = false;
                    }
                    else {
                        mIsBuffering = true;
                    }
                    updatePlayerState();
                    break;
                default:
                    //Log.d(TAG, "Received VLC MediaPlayer.Event: " + event.type);
                    break;
            }
        }
    }

    private void initTracks() {
        if(mMediaPlayer == null) {
            return;
        }

        tracksManager.updateTracks(getPlayerTracks());
        int videoTrackIndex = mMediaPlayer.getVideoTrack();
        if(videoTrackIndex<0&&tracksManager.hasTracks(TvTrackInfo.TYPE_VIDEO)){
            videoTrackIndex = TracksManager.getIndexFromTrackId(tracksManager.getTracks(TvTrackInfo.TYPE_VIDEO)[0].getId());
        }

        final String selectedVideoTrackId = TracksManager.generateTrackId(TvTrackInfo.TYPE_VIDEO, videoTrackIndex);

        if (playbackSize != null) {
            tracksManager.updateVideoSize(selectedVideoTrackId, playbackSize);
            Log.d(TAG,String.format(
                    "Updating track: id=%s w=%d h=%d ar=%.2f",
                    selectedVideoTrackId,
                    playbackSize.width,
                    playbackSize.height,
                    playbackSize.pixelAspectRatio
                    ));
        }

        notifyTracksChanged(tracksManager.getAllTracks());
        if(mMediaPlayer.getAudioTrack()>=0) {
            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO,
                    TracksManager.generateTrackId(TvTrackInfo.TYPE_AUDIO, mMediaPlayer.getAudioTrack()));
        }
        if(videoTrackIndex>=0) {
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO,
                    selectedVideoTrackId);
        }
    }

    private class MediaEventListener implements Media.EventListener {
        @Override
        public void onEvent(Media.Event event) {
            switch(event.type) {
                default:
                    //Log.v(Constants.TAG, "vlc:media_event: type=" + event.type);
                    break;
            }
        }
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout,
                            int width, int height,
                            int visibleWidth, int visibleHeight,
                            int sarNum, int sarDen) {

        Log.d(TAG, "vlc:onNewVideoLayout: w=" + width + " h=" + height);

        playbackSize = new VideoPlaybackSize(width, height, 1.0f);

        if(playbackSize!=null) {
            initTracks();
            TvTrackInfo trackInfo = null;
            if(!tracksManager.hasTracks(TvTrackInfo.TYPE_VIDEO)) {
                trackInfo = tracksManager.addVideoTrack(playbackSize);
                String selectedVideoTrackId = TracksManager.generateTrackId(TvTrackInfo.TYPE_VIDEO, 0);

                if (trackInfo != null) {
                    Log.d(Constants.TAG, String.format("vlc:onNewLayout: updating track: %s", selectedVideoTrackId));
                    notifyTracksChanged(tracksManager.getAllTracks());
                    notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, selectedVideoTrackId);
                }
            }

            if(mEngineSession != null) {
                TvContractUtils.updateChannelDimensions(mContext, mEngineSession.getChannelUri(), width, height);
            }
        }
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
        Log.d(Constants.TAG, "vlc:onSurfacesCreated");
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        Log.d(Constants.TAG, "vlc:onSurfacesDestroyed");
    }

    private SparseArray<TvTrackInfo[]> getPlayerTracks() {
        SparseArray<TvTrackInfo[]>  tracks = new SparseArray<>();

        if(mMediaPlayer == null) {
            return tracks;
        }

        int[] trackTypes = {
                ExoVideoPlayer.TYPE_AUDIO,
                ExoVideoPlayer.TYPE_VIDEO
        };

        for (int trackType : trackTypes) {
            MediaPlayer.TrackDescription[] playerTracks = null;
            if(trackType == ExoVideoPlayer.TYPE_AUDIO){
                playerTracks = mMediaPlayer.getAudioTracks();
            }
            else if(trackType == ExoVideoPlayer.TYPE_VIDEO){
                playerTracks = mMediaPlayer.getVideoTracks();
            }

            if(playerTracks!=null) {
                ArrayList<TvTrackInfo> tvTrackInfos = new ArrayList<>();

                for (MediaPlayer.TrackDescription td : playerTracks) {
                    if (td.id == -1) continue;

                    Log.d(TAG, String.format("Found track %d. ID: %d  Name: %s", trackType, td.id, td.name));
                    String trackId = TracksManager.generateTrackId(trackType, td.id);

                    TvTrackInfo.Builder builder = new TvTrackInfo.Builder(trackType, trackId);

                    if(!TextUtils.isEmpty(td.name)){
                        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.M) {
                            builder.setDescription(td.name);
                        }
                    }

                    tvTrackInfos.add(builder.build());
                }

                tracks.append(trackType, tvTrackInfos.toArray(new TvTrackInfo[tvTrackInfos.size()]));
            }
        }
        return tracks;
    }

    @Override
    protected boolean isPlaying() {
        boolean isPlaying = false;

        if(mMediaPlayer == null) {
            return false;
        }

        if(mIsBuffering) {
            return false;
        }

        switch(mMediaPlayer.getPlayerState()) {
            case VlcState.PLAYING:
            case VlcState.PAUSED:
                isPlaying = true;
                break;
            default:
                isPlaying = false;
                break;
        }

        return isPlaying;
    }

    @Override
    protected boolean isBuffering() {
        return mIsBuffering;
    }

    private void checkVolume() {
        if (mMediaPlayer != null) {
            int current = mMediaPlayer.getVolume();
            int wanted = (int) mVolume * 100;

            if(current != wanted) {
                Log.d(TAG, "fix volume: current=" + current + " wanted=" + wanted);
                mMediaPlayer.setVolume(wanted);
            }
        }
    }

    protected void maintainPlayback() {
        // VLC can reset volume by itself so we need to check periodically
        // that volume is the same as we want.
        checkVolume();
    }

    private void updatePlayerState() {
        if(mMediaPlayer == null) {
            Log.d(TAG, "update player state: no player");
        }
        else {
            Log.d(TAG, "update player state: state=" + mMediaPlayer.getPlayerState() + " isPlaying=" + mMediaPlayer.isPlaying() + " buffering=" + mIsBuffering + "(" + mBufferingProgress + ")");
        }

        if(isPlaying() && !isTuning()) {
            notifyVideoAvailable();
        }
    }

    @Override
    public boolean onSelectTrack(int type, String trackId) {
        Log.d(TAG, "onSelectTrack: type=" + type + " id=" + trackId);
        try {
            if(type == 0) {
                int trackIndex = TracksManager.getIndexFromTrackId(trackId);
                mMediaPlayer.setAudioTrack(trackIndex);
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "onSelectTrack: error", e);
        }
        notifyTrackSelected(type, trackId);
        return true;
    }
}
