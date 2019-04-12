package org.acestream.livechannels.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import org.acestream.livechannels.Constants;
import org.acestream.livechannels.model.Channel;
import org.acestream.livechannels.model.Program;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by barbarian on 03.06.16.
 */
public class TvContractUtils {
    private static final String TAG = "AS/TvContractUtils";
    private static final SparseArray<String> VIDEO_HEIGHT_TO_FORMAT_MAP =
            new SparseArray<>();

    static {
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(480, TvContract.Channels.VIDEO_FORMAT_480P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(576, TvContract.Channels.VIDEO_FORMAT_576P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(720, TvContract.Channels.VIDEO_FORMAT_720P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(1080, TvContract.Channels.VIDEO_FORMAT_1080P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(2160, TvContract.Channels.VIDEO_FORMAT_2160P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(4320, TvContract.Channels.VIDEO_FORMAT_4320P);
    }

    private static String getVideoFormat(int videoHeight) {
        return VIDEO_HEIGHT_TO_FORMAT_MAP.get(videoHeight);
    }

    public static TvContentRating[] stringToContentRatings(String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return null;
        }
        String[] ratings = commaSeparatedRatings.split("\\s*,\\s*");
        TvContentRating[] contentRatings = new TvContentRating[ratings.length];
        for (int i = 0; i < contentRatings.length; ++i) {
            contentRatings[i] = TvContentRating.unflattenFromString(ratings[i]);
        }
        return contentRatings;
    }

    public static String contentRatingsToString(TvContentRating[] contentRatings) {
        if (contentRatings == null || contentRatings.length == 0) {
            return null;
        }
        final String DELIMITER = ",";
        StringBuilder ratings = new StringBuilder(contentRatings[0].flattenToString());
        for (int i = 1; i < contentRatings.length; ++i) {
            ratings.append(DELIMITER);
            ratings.append(contentRatings[i].flattenToString());
        }
        return ratings.toString();
    }

    private static Channel getChannelByNumber(String channelNumber,
                                              List<Channel> channels) {
        for (Channel info : channels) {
            if (info.getDisplayNumber().equals(channelNumber)) {
                return info;
            }
        }
        throw new IllegalArgumentException("Unknown channel: " + channelNumber);
    }

    private TvContractUtils() {}

    public static List<Channel> getChannels(ContentResolver resolver) {
        List<Channel> channels = new ArrayList<>();
        // TvProvider returns programs in chronological order by default.
        try (Cursor cursor = resolver.query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return channels;
            }
            while (cursor.moveToNext()) {
                channels.add(Channel.fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get channels", e);
        }
        return channels;
    }

    public static List<Program> getPrograms(ContentResolver resolver, Uri channelUri) {
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri);
        List<Program> programs = new ArrayList<>();
        // TvProvider returns programs in chronological order by default.
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return programs;
            }
            while (cursor.moveToNext()) {
                programs.add(Program.fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get programs for " + channelUri, e);
        }
        return programs;
    }

    public static Channel getChannelFromChannelUri(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(channelUri, null, null,null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }

            if (cursor.moveToNext()) {
                return Channel.fromCursor(cursor);
            }
        } catch (Exception e) {
            Log.d(TAG, "Content provider query: " + e.getStackTrace());
            return null;
        }
        return null;
    }

    public static Program getCurrentProgram(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();
        List<Program> programs = getPrograms(resolver, channelUri);
        long nowMs = System.currentTimeMillis();
        for (Program program : programs) {
            if (program.getStartTimeUtcMillis() <= nowMs && program.getEndTimeUtcMillis() > nowMs) {
                return program;
            }
        }
        return null;
    }

    /**
     * Finds the last exact hour and returns that in MS. 1:52 would become 1:00.
     * @return An exact hour in milliseconds
     */
    public static long getNearestHour() {
        return getNearestHour(new Date().getTime());
    }
    /**
     * Finds the last exact hour and returns that in MS. 1:52 would become 1:00.
     * @param startMs Your starting time
     * @return An exact hour in milliseconds
     */
    public static long getNearestHour(long startMs) {
        return (long) (Math.floor(startMs/1000/60/60)*1000*60*60);
    }
    /**
     * Finds the last exact half hour and returns that in MS. 1:52 would become 1:30.
     * @return An exact half hour in milliseconds
     */
    public static long getNearestHalfHour() {
        return (long) (Math.floor(new Date().getTime()/1000/60/30)*1000*60*30);
    }
    /**
     * Finds the last exact half hour and returns that in MS. 1:52 would become 1:30.
     * @param startMs Your starting time
     * @return An exact half hour in milliseconds
     */
    public static long getNearestHalfHour(long startMs) {
        return (long) (Math.floor(startMs/1000/60/30)*1000*60*30);
    }

    /**
     * If you don't have access to an EPG or don't want to supply programs, you can simply
     * add several instances of this generic program object.
     *
     * Note you will have to set the start and end times manually.
     * @param channel The channel for which the program will be displayed
     * @return A very generic program object
     */


    public static final TvContentRating RATING_PG = TvContentRating.createRating(
            "com.android.tv",
            "US_TV",
            "US_TV_PG",
            "US_TV_D", "US_TV_L");
    public static final TvContentRating RATING_MA = TvContentRating.createRating(
            "com.android.tv",
            "US_TV",
            "US_TV_MA",
            "US_TV_V", "US_TV_S");



    public static void updateChannels(
            Context context, String inputId, List<Channel> channels) {

        if(channels == null) {
            Log.d(Constants.TAG, "updateChannels: got null channels");
            return;
        }

        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Long> channelMap = new SparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID};
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                channelMap.put(originalNetworkId, rowId);
            }
        }

        // If a channel exists, update it. If not, insert a new one.

        Map<Uri, String> logos = new HashMap<>();
        for (Channel channel : channels) {

            ContentValues values = channel.toContentValues();
            values.put(TvContract.Channels.COLUMN_INPUT_ID, inputId);

            Long rowId = channelMap.get(channel.getOriginalNetworkId());
            Uri uri;
            if (rowId == null) {
                if(Build.VERSION.SDK_INT < 26) {
                    // Set field CATEGORY_BROWSABLE on Android before 8.0 (Oreo, API level 26)
                    // Starting from API level 26 this field is not accesible by regular applications.
                    values.put("browsable", 1);
                }
                uri = resolver.insert(TvContract.Channels.CONTENT_URI, values);
                Log.d(TAG, "add channel: id=" + channel.getEPGId() + " title=" + channel.getDisplayName() + " logo=" + channel.getLogo());
            } else {
                uri = TvContract.buildChannelUri(rowId);
                resolver.update(uri, values, null, null);
                channelMap.remove(channel.getOriginalNetworkId());
                Log.d(TAG, "update channel: id=" + channel.getEPGId() + " title=" + channel.getDisplayName() + " logo=" + channel.getLogo());
            }
            if (uri!=null&&channel.getLogo() != null && !TextUtils.isEmpty(channel.getLogo())) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.getLogo());
            }
        }
        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }

        // Deletes channels which don't exist in the new feed.
        int size = channelMap.size();
        for (int i = 0; i < size; ++i) {
            Long rowId = channelMap.valueAt(i);
            Log.d(TAG, "delete channel: rowid=" + rowId);
            resolver.delete(TvContract.buildChannelUri(rowId), null, null);
        }
    }


    public static void updateChannelDimensions(Context context, Uri channelUri, int width, int height) {
        if(width>0&&height>0) {
            ContentValues values = new ContentValues();
                if (height == 720) {
                    values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, TvContract.Channels.VIDEO_FORMAT_720P);
                }

                if (height > 720 && height <= 1080) {
                    values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, TvContract.Channels.VIDEO_FORMAT_1080I);
                } else if (height == 2160) {
                    values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, TvContract.Channels.VIDEO_FORMAT_2160P);
                } else if (height == 4320) {
                    values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, TvContract.Channels.VIDEO_FORMAT_4320P);
                } else {
                    values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, TvContract.Channels.VIDEO_FORMAT_576I);
                }

                    if (context.getContentResolver().update(channelUri, values, null, null) != 1) {
                    Log.e(TAG, "unable to update channel properties");
                }
            }
        }

        public static boolean haveChannels(ContentResolver resolver) {
            try (Cursor cursor = resolver.query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
                return !(cursor == null || cursor.getCount() == 0);
            } catch (Exception e) {
                Log.w(TAG, "Unable to get channels", e);
            }

            return false;
        }

    public static class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
        private final Context mContext;

        InsertLogosTask(Context context) {
            mContext = context;
        }

        @Override
        public Void doInBackground(Map<Uri, String>... logosList) {
            for (Map<Uri, String> logos : logosList) {
                for (Uri uri : logos.keySet()) {
                    try {
                        insertUrl(mContext, uri, new URL(logos.get(uri)));
                    } catch (MalformedURLException e) {
                        Log.e(TAG, "Can't load " + logos.get(uri), e);
                    }
                }
            }
            return null;
        }
    }

    public static void insertUrl(Context context, Uri contentUri, URL sourceUrl) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = sourceUrl.openStream();
            os = context.getContentResolver().openOutputStream(contentUri);
            copy(is, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to write " + sourceUrl + "  to " + contentUri, ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore exception.
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // Ignore exception.
                }
            }
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }
}
