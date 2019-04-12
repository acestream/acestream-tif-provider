package org.acestream.livechannels.tvinput;

import android.content.Context;
import android.media.MediaCodec;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.Util;

import org.acestream.livechannels.exoplayer.ExoVideoPlayer;
import org.acestream.livechannels.exoplayer.ExtractorRendererBuilder;
import org.acestream.livechannels.exoplayer.HlsRendererBuilder;
import org.acestream.livechannels.model.NowPlaying;
import org.acestream.livechannels.utils.TracksManager;
import org.acestream.livechannels.utils.TvContractUtils;
import org.acestream.livechannels.utils.VideoPlaybackSize;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public class ExoPlayerSession extends BaseSession implements ExoVideoPlayer.Listener, ExoVideoPlayer.InternalErrorListener {
    private static final String TAG = ExoPlayerSession.class.getName();
    private ExoVideoPlayer exoVideoPlayer;
    private final TracksManager tracksManager;
    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public ExoPlayerSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");
        tracksManager = new TracksManager();
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurface (" + mSessionNumber + ")");

        mSurface = surface;

        if (exoVideoPlayer != null) {
            exoVideoPlayer.setSurface(surface);
        }

        return true;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");

        mVolume = volume;

        if (exoVideoPlayer != null) {
           exoVideoPlayer.setVolume(volume);
        }
    }

    protected void playChannel(NowPlaying nowPlaying) {
        // Stop any existing playback
        stopPlayback();
        Log.d(TAG, "Starting playback of stream: " + nowPlaying.getStreamUrl());
        // Prepare the media player
        exoVideoPlayer = prepareMediaPlayer(Uri.parse(nowPlaying.getStreamUrl()),nowPlaying.getOutputFormat(), null);

        if (exoVideoPlayer != null) {
            // Start the media playback
            Log.d(TAG, "Starting playback of channel: " + nowPlaying.toString());
            exoVideoPlayer.setPlayWhenReady(true);
        } else {
            Toast.makeText(mContext, "Failed to prepare video", Toast.LENGTH_SHORT).show();
        }
    }

    protected void stopPlayback(boolean releasePlayer, boolean releaseLibrary) {
        //TODO: implement
    }

    protected void stopPlayback() {
        Log.d(TAG, "Session stopPlayback (" + mSessionNumber + ")");
        playbackSize = null;
        if (exoVideoPlayer != null) {
            exoVideoPlayer.setInternalErrorListener(null);
            exoVideoPlayer.removeListener(this);
            exoVideoPlayer.setSurface(null);
            exoVideoPlayer.stop();

            exoVideoPlayer.release();

            exoVideoPlayer = null;
        }
    }

    private ExoVideoPlayer prepareMediaPlayer(Uri videoUri, String outputFormat, Map<String, String> headers) {
        Log.d(TAG, "Preparing video: " + videoUri + ".");

        // Create and prep the ExoPlayer instance
        String userAgent = Util.getUserAgent(mContext, "android-tvchannels");

        ExoVideoPlayer exoPlayer = new ExoVideoPlayer(getRendererBuilder(mContext,outputFormat,videoUri,headers));


        exoPlayer.addListener(this);
        exoPlayer.setInternalErrorListener(this);
        exoPlayer.prepare();
        exoPlayer.setSurface(mSurface);
        exoPlayer.setVolume(mVolume);

        return exoPlayer;
    }


    private  static  ExoVideoPlayer.RendererBuilder getRendererBuilder(Context context,
                                                                 final String outputFormat,
                                                                 Uri videoUri, Map<String, String> headers) {
        String userAgent = Util.getUserAgent(context, "android-tvchannels");

        if("hls".equals(outputFormat)) {
            return new HlsRendererBuilder(context, userAgent, videoUri.toString());
        }
        else {
            return new ExtractorRendererBuilder(context, userAgent, videoUri);
        }
    }
    private boolean reloadIfNeeds = false;

    @Override
    protected void reloadStream() {
        Log.d(TAG, "reloadStream");
        reloadIfNeeds = false;
        errorState = false;
        super.reloadStream();
    }
    private boolean errorState = false;
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "onStateChanged: playWhenReady" + playWhenReady + " playbackState=" + playbackState);
        if (playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
            reloadIfNeeds = true;
        }
        else if (playWhenReady && playbackState == ExoPlayer.STATE_PREPARING) {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
        }
        else if (playWhenReady && (playbackState == ExoPlayer.STATE_IDLE||playbackState == ExoPlayer.STATE_ENDED)) {
            if(reloadIfNeeds){
                reloadStream();
            }
            else if(!errorState) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
            }
        }
        else if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
            tracksManager.updateExoPlayerTracks(getPlayerTracks());

            String selectedVideoTrackId = TracksManager.generateTrackId(TvTrackInfo.TYPE_VIDEO, exoVideoPlayer.getSelectedTrack(TvTrackInfo.TYPE_VIDEO));

            if(playbackSize!=null) {
                tracksManager.updateVideoSize(selectedVideoTrackId, playbackSize);
            }

            final List<TvTrackInfo> allTracks = tracksManager.getAllTracks();

            notifyTracksChanged(allTracks);

            String audioId = TracksManager.generateTrackId(TvTrackInfo.TYPE_AUDIO,
                    exoVideoPlayer.getSelectedTrack(TvTrackInfo.TYPE_AUDIO));

            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, audioId);
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, selectedVideoTrackId);
            notifyVideoAvailable();
        }
        errorState = false;
    }



    @Override
    public void onError(Exception e) {
        Log.e(TAG, e.getMessage());
        errorState = true;
        if(e instanceof ExoPlaybackException ||e instanceof HttpDataSource.HttpDataSourceException){
            notifyStreamUnavailable();
        }
        else {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
        }
    }


    private VideoPlaybackSize playbackSize = null;
    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.d(TAG, "onVideoSizeChanged W:" + width + " H:" + height);
        final String selectedTrackId = TracksManager.generateTrackId(TvTrackInfo.TYPE_VIDEO, exoVideoPlayer.getSelectedTrack(TvTrackInfo.TYPE_VIDEO));
        playbackSize = new VideoPlaybackSize(width, height, pixelWidthHeightRatio);
        if(playbackSize!=null) {
            final TvTrackInfo trackInfo = tracksManager.updateVideoSize(selectedTrackId, playbackSize);
            if (trackInfo != null) {
                notifyTracksChanged(tracksManager.getAllTracks());
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, selectedTrackId);
            }

            if(mEngineSession != null) {
                TvContractUtils.updateChannelDimensions(mContext, mEngineSession.getChannelUri(), width, height);
            }
        }

    }

    private SparseArray<MediaFormat[]> getPlayerTracks() {
        SparseArray<MediaFormat[]>  tracks = new SparseArray<>();

        int[] trackTypes = {
                ExoVideoPlayer.TYPE_AUDIO,
                ExoVideoPlayer.TYPE_VIDEO
        };

        for (int trackType : trackTypes) {
            int count = exoVideoPlayer.getTrackCount(trackType);

            MediaFormat[] formats  =  new MediaFormat[count];

            for (int i = 0; i < count; i++) {
                MediaFormat format = exoVideoPlayer.getTrackFormat(trackType, i);
                formats[i] = format;
            }
            tracks.append(trackType,formats);
        }
        return tracks;
    }

    @Override
    public void onRendererInitializationError(Exception e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        Log.e(TAG, "onAudioTrackUnderrun");
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onLoadError(int sourceId, IOException e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    protected boolean isPlaying() {
        boolean isPlaying = false;

        if(exoVideoPlayer == null) {
            return false;
        }

        switch(exoVideoPlayer.getPlaybackState()) {
            case ExoVideoPlayer.STATE_READY:
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
        //TODO: implement
        return false;
    }

    protected void maintainPlayback() {
        // do nothing
    }
}
