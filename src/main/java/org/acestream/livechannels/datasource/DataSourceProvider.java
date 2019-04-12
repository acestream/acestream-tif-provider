package org.acestream.livechannels.datasource;

import android.content.Context;
import android.content.res.Resources;
import android.media.tv.TvContract;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import org.acestream.livechannels.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by barbarian on 08.06.16.
 */
public class DataSourceProvider {
    private final static String TAG = "DSP";

    public static List<org.acestream.livechannels.model.Program> getEPG(org.acestream.livechannels.model.Channel channel, long startTimeMs, long endTimeMs) throws ChannelsAPI.ApiError {
        ChannelsAPI api = new ChannelsAPI();
        final Program[] epg = api.getChannelEPG(channel.getEPGId());

        return processPrograms(channel, epg);
    }

    public static Map<org.acestream.livechannels.model.Channel,List<org.acestream.livechannels.model.Program>> getEPGBatch(Context context, List<org.acestream.livechannels.model.Channel> channels, long startTimeMs, long endTimeMs) throws ChannelsAPI.ApiError {
        ChannelsAPI api = new ChannelsAPI();
        Map<String,org.acestream.livechannels.model.Channel> channelsMap = new ArrayMap<>(channels.size());

        for(org.acestream.livechannels.model.Channel channel: channels) {
            channelsMap.put(channel.getEPGId(), channel);
        }

        Map<String,Program[]> epg = api.getChannelEPGBatch(channelsMap.keySet());

        if(epg == null) {
            return null;
        }

        Map<org.acestream.livechannels.model.Channel,List<org.acestream.livechannels.model.Program>> res = new HashMap<>();
        for(Map.Entry<String,Program[]> item: epg.entrySet()) {
            if(channelsMap.containsKey(item.getKey())) {
                List<org.acestream.livechannels.model.Program> programs = processPrograms(channelsMap.get(item.getKey()), item.getValue());
                res.put(channelsMap.remove(item.getKey()), programs);
            }
            else {
                Log.e(TAG, "missing key: " + item.getKey());
            }
        }

        Resources resources = context.getResources();
        if(resources != null) {
            for (Map.Entry<String, org.acestream.livechannels.model.Channel> item : channelsMap.entrySet()) {
                // add fake program if we have categories for channel
                int[] categoryIds = item.getValue().getCategoryIds();
                if (categoryIds != null) {
                    String[] genres = getCanonicalGenresFromCategoryIds(categoryIds);
                    if (genres != null) {
                        List<org.acestream.livechannels.model.Program> programs = new ArrayList<>();
                        org.acestream.livechannels.model.Program.Builder builder = new org.acestream.livechannels.model.Program.Builder();

                        builder.setTitle(resources.getString(R.string.epg_no_info));
                        long now = new Date().getTime();
                        builder.setStartTimeUtcMillis(now - 3600000);
                        builder.setEndTimeUtcMillis(now + 86400000 / 2);
                        builder.setCanonicalGenres(genres);

                        programs.add(builder.build());
                        res.put(item.getValue(), programs);
                    }
                }
            }
        }

        return res;
    }

    public static List<org.acestream.livechannels.model.Program> processPrograms(org.acestream.livechannels.model.Channel channel, Program[] epg) {
        List<org.acestream.livechannels.model.Program> res = new ArrayList<>();
        if(epg!=null){
            for( Program event:epg) {
                org.acestream.livechannels.model.Program.Builder program = new org.acestream.livechannels.model.Program.Builder();
                String img = channel.getLogo();

                program.setTitle(event.title);
                program.setDescription(event.description);
                program.setLongDescription(event.description);

                if(!TextUtils.isEmpty(event.poster)) {
                    img = event.poster;

                    // Handle URIs without scheme (like "//example.com/1.png")
                    if(img.startsWith("//")) {
                        img = "http:" + img;
                    }
                }
                if(!TextUtils.isEmpty(img)){
                    program.setPosterArtUri(img);
                    program.setThumbnailUri(img);
                }

                int[] categoryIds = event.category_ids;
                if(categoryIds == null) {
                    categoryIds = channel.getCategoryIds();
                }

                if(categoryIds != null) {
                    String[] genres = getCanonicalGenresFromCategoryIds(categoryIds);
                    if(genres != null) {
                        program.setCanonicalGenres(genres);
                    }
                }

                //convert timestamp to milliseconds
                program.setStartTimeUtcMillis(event.start*1000L);
                program.setEndTimeUtcMillis(event.stop*1000L);

                res.add(program.build());
            }
        }
        return res;
    }

    public static List<org.acestream.livechannels.model.Channel> getAllChannels(Context context, String inputId, boolean forceUpdate) throws ChannelsAPI.ApiError {
        List<org.acestream.livechannels.model.Channel> res = new ArrayList<>();
        ChannelsAPI api = new ChannelsAPI();
        final Channel[] channels = api.getChannels(forceUpdate);
        if(channels!=null){
            // sort by title
            List<Channel> channelsList = new ArrayList<>(Arrays.asList(channels));
            Collections.sort(channelsList);

            for(Channel ch:channelsList) {
                String dispNumber = Integer.toString(res.size() + 1);
                org.acestream.livechannels.model.Channel channel = new org.acestream.livechannels.model.Channel.Builder()
                        .setDisplayName(ch.title)
                        .setDisplayNumber(dispNumber)
                        .setOriginalNetworkId((Long.toString(ch.id) + ch.title).hashCode())
                        .setEPGId(Long.toString(ch.id))
                        .setPlaybackUrl(ch.playback_url)
                        .setInfohash(ch.infohash)
                        .setContentId(ch.content_id)
                        .setTransportFileUrl(ch.url)
                        .setLogoUrl(ch.icon)
                        .setCategoryNames(ch.categories)
                        .setCategoryIds(ch.category_ids)
                        //
                        .setAppLinkIntentUri(ch.url)
                        .setAppLinkPosterArtUri("http://static.acestream.net/sites/acestream/img/ACE-logo.png")
                        .setAppLinkText(context.getString(R.string.open_in_video_player))

                       .build();

                res.add(channel);
            }
        }
        return res;
    }

    public static String getPlaylistHash(Context context, String inputId) throws ChannelsAPI.ApiError {
        ChannelsAPI api = new ChannelsAPI();
        return api.getPlaylistHash();
    }

    public static String[] getCanonicalGenresFromCategoryIds(int[] categoryIds) {
        List<String> result = new ArrayList<>();

        for(int categoryId: categoryIds) {
            switch(categoryId) {
                case 1:
                    result.add(TvContract.Programs.Genres.ENTERTAINMENT);
                    break;
                case 2:
                    // general
                    result.add(TvContract.Programs.Genres.ENTERTAINMENT);
                    break;
                case 3:
                    result.add(TvContract.Programs.Genres.FAMILY_KIDS);
                    break;
                case 4:
                    result.add(TvContract.Programs.Genres.EDUCATION);
                    break;
                case 5:
                    result.add(TvContract.Programs.Genres.MOVIES);
                    break;
                case 6:
                    result.add(TvContract.Programs.Genres.MUSIC);
                    break;
                case 7:
                    result.add(TvContract.Programs.Genres.NEWS);
                    break;
                case 8:
                    result.add(TvContract.Programs.Genres.SPORTS);
                    break;
                case 9:
                    result.add(TvContract.Programs.Genres.ANIMAL_WILDLIFE);
                    break;
                case 10:
                    result.add(TvContract.Programs.Genres.ARTS);
                    break;
                case 11:
                    result.add(TvContract.Programs.Genres.COMEDY);
                    break;
                case 12:
                    result.add(TvContract.Programs.Genres.DRAMA);
                    break;
                case 13:
                    result.add(TvContract.Programs.Genres.EDUCATION);
                    break;
                case 14:
                    result.add(TvContract.Programs.Genres.GAMING);
                    break;
                case 15:
                    result.add(TvContract.Programs.Genres.LIFE_STYLE);
                    break;
                case 16:
                    result.add(TvContract.Programs.Genres.PREMIER);
                    break;
                case 17:
                    result.add(TvContract.Programs.Genres.SHOPPING);
                    break;
                case 18:
                    result.add(TvContract.Programs.Genres.TECH_SCIENCE);
                    break;
                case 19:
                    result.add(TvContract.Programs.Genres.TRAVEL);
                    break;
            }
        }

        if(result.size() == 0) {
            return null;
        }

        return result.toArray(new String[result.size()]);
    }
}
