package org.acestream.livechannels.datasource;

import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import org.acestream.livechannels.Constants;
import org.acestream.livechannels.model.ContentDescriptor;
import org.acestream.sdk.AceStream;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

/**
 * Created by barbarian on 13.06.16.
 */
public class ChannelsAPI {

    private final static String TAG = "AS/ChannelsAPI";
    private static String mAccessToken = null;
    private static ConnectionPool mHttpConnectionPool = new ConnectionPool(10, 2, TimeUnit.MINUTES);

    public Channel[] getChannels(boolean forceUpdate) throws ChannelsAPI.ApiError {
        AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);

        final Call<APIResponse<Playlist>> call = service.getPlaylist(
            forceUpdate ? 1 : 0,
            0
        );
        try {
            final Response<APIResponse<Playlist>> response = call.execute();
            if(response!=null){
                APIResponse<Playlist> body = response.body();
                if(body!=null){
                    if(!TextUtils.isEmpty(body.error)){
                        Log.e(TAG, body.error);
                    }
                    return body.result.playlist;
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "getChannels:error: " + e.getMessage());
            throw new ApiError("failed to get channels");
        }
        return null;
    }

    public String getPlaylistHash() throws ChannelsAPI.ApiError {
        AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);

        final Call<APIResponse<Playlist>> call = service.getPlaylist(0, 1);
        try {
            final Response<APIResponse<Playlist>> response = call.execute();
            if(response!=null){
                APIResponse<Playlist> body = response.body();
                if(body!=null){
                    if(!TextUtils.isEmpty(body.error)){
                        Log.e(TAG, body.error);
                    }
                    return body.result.playlist_hash;
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "getPlaylistHash:error: " + e.getMessage());
            throw new ApiError("failed to get playlist hash");
        }
        return null;
    }

    public Program[] getChannelEPG(String channelId) throws ApiError {
        if(!TextUtils.isEmpty(channelId)) {
            AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);
            final Call<APIResponse<Map<String, Program[]>>> call = service.getEPG(channelId);
            try {
                final Response<APIResponse<Map<String, Program[]>>> response = call.execute();
                if (response != null) {
                    APIResponse<Map<String, Program[]>> body = response.body();
                    if (body != null) {
                        if (!TextUtils.isEmpty(body.error)) {
                            Log.e(TAG, body.error);
                        }

                        if (body.result != null && body.result.containsKey(channelId)) {
                            return body.result.get(channelId);
                        }
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "getChannelEPG:error: " + e.getMessage());
                throw new ApiError("api call failed");
            }
        }
        return null;
    }

    public Map<String,MediaFile> getMediaFiles(ContentDescriptor contentDescriptor) throws ApiError {
        if(contentDescriptor == null) {
            Log.e(TAG, "missing content descriptor");
            return null;
        }

        if(contentDescriptor.type == null) {
            Log.e(TAG, "missing content descriptor type");
            return null;
        }

        Map<String, String> params = new ArrayMap<>();
        params.put("mode", "full");
        params.put("expand_wrapper", "1");

        // add content descriptor to params
        switch(contentDescriptor.type) {
            case "url":
                params.put("url", contentDescriptor.id);
                break;
            case "infohash":
                params.put("infohash", contentDescriptor.id);
                break;
            case "content_id":
                params.put("content_id", contentDescriptor.id);
                break;
            default:
                Log.e(TAG, "unknown content descriptor type: " + contentDescriptor.type);
                return null;
        }

        AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);

        final Call<APIResponse<Map<String,MediaFile>>> call = service.getMediaFiles(params);

        try {
            final Response<APIResponse<Map<String,MediaFile>>> response = call.execute();
            if (response != null) {
                APIResponse<Map<String,MediaFile>> body = response.body();
                if (body != null) {
                    if (!TextUtils.isEmpty(body.error)) {
                        Log.e(TAG, body.error);
                    }

                    if (body.result != null) {
                        return body.result;
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "getMediaFiles:error: " + e.getMessage());
            throw new ApiError("api call failed");
        }
        return null;
    }

    public Map<String,Program[]> getChannelEPGBatch(Set<String> channelIds) throws ApiError {
        if(channelIds != null && channelIds.size() > 0) {
            AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);

            Log.d(TAG, "getChannelEPGBatch: get epg for " + channelIds.size() + " channels");
            final Call<APIResponse<Map<String, Program[]>>> call = service.getEPG(TextUtils.join(",", channelIds));
            Log.d(TAG, "getChannelEPGBatch: get epg done");

            try {
                final Response<APIResponse<Map<String, Program[]>>> response = call.execute();
                if (response != null) {
                    APIResponse<Map<String, Program[]>> body = response.body();
                    if (body != null) {
                        if (!TextUtils.isEmpty(body.error)) {
                            Log.e(TAG, body.error);
                        }

                        if (body.result != null) {
                            return body.result;
                        }
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "getChannelEPGBatch:error: " + e.getMessage());
                throw new ApiError("api call failed");
            }
        }
        return null;
    }

    public interface StartPlaybackCallback {
        void onResponse(PlaybackResponse response);
        void onFailure(String error);
        void onNetworkError(String error);
    }

    public void startPlayback(ContentDescriptor contentDescriptor, String outputFormat, boolean disableP2P, final StartPlaybackCallback callback)
    {
        if(contentDescriptor == null) {
            callback.onFailure("missing content descriptor");
            return;
        }

        if(contentDescriptor.type == null) {
            callback.onFailure("missing content descriptor type");
            return;
        }

        AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);
        Map<String, String> params = new ArrayMap<>();
        params.put("sid", Constants.ACESTREAM_PLAYER_SID);

        String productKey = AceStream.getHttpApiProductKey();
        if(!TextUtils.isEmpty(productKey)) {
            params.put("product_key", productKey);
        }

        // ensures correct bengine behaviour when VLC starts reading live from multiple threads
        params.put("stop_prev_read_thread", "1");

        // how many seconds to wait for manifest from p2p if direct access failed
        params.put("manifest_p2p_wait_timeout", "10");

        if(disableP2P) {
            params.put("disable_p2p", "1");
        }

        // add content descriptor to params
        switch(contentDescriptor.type) {
            case "url":
                params.put("url", contentDescriptor.id);
                break;
            case "infohash":
                params.put("infohash", contentDescriptor.id);
                break;
            case "content_id":
                params.put("id", contentDescriptor.id);
                break;
            default:
                callback.onFailure("unknown content descriptor type: " + contentDescriptor.type);
                return;
        }

        Log.d(TAG, "api: params=" + params.toString());

        final Call<APIResponse2<PlaybackResponse>> call =
                outputFormat.equals("hls")
                        ? service.getHLS(params)
                        : service.getStream(params);

        Log.d(TAG, "api: playback url: " + call.request().url().toString());

        call.enqueue(new Callback<APIResponse2<PlaybackResponse>>() {
            @Override
            public void onResponse(Call<APIResponse2<PlaybackResponse>> call, Response<APIResponse2<PlaybackResponse>> response) {
                if (response != null) {
                    APIResponse2<PlaybackResponse> body = response.body();
                    if (body != null) {
                        if (!TextUtils.isEmpty(body.error)) {
                            Log.e(TAG, "api: got error: " + body.error);
                            callback.onFailure(body.error);
                        }
                        else if (body.response == null) {
                            Log.e(TAG, "api: got null response");
                            callback.onFailure("null response");
                        }
                        else {
                            callback.onResponse(body.response);
                        }
                    }
                    else {
                        callback.onFailure("missing body");
                    }
                }
                else {
                    callback.onFailure("missing response");
                }
            }

            @Override
            public void onFailure(Call<APIResponse2<PlaybackResponse>> call, Throwable t) {
                callback.onNetworkError(t.toString());
            }
        });
    }

    public interface StatusResponseCallback {
        void onResponse(StatusResponse status);
        void onFailure(String error);
    }

    public void getStatusAsync(String url, final StatusResponseCallback callback) {
        if(!TextUtils.isEmpty(url)) {
            AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);
            final Call<APIResponse2<StatusResponse>> call = service.getStatus(url);

            call.enqueue(new Callback<APIResponse2<StatusResponse>>() {
                @Override
                public void onResponse(Call<APIResponse2<StatusResponse>> call, Response<APIResponse2<StatusResponse>> response) {
                    if (response != null) {
                        APIResponse2<StatusResponse> body = response.body();
                        if (body != null) {
                            if (!TextUtils.isEmpty(body.error)) {
                                Log.e(TAG, "api: got error: " + body.error);
                                callback.onFailure(body.error);
                            }
                            else {
                                callback.onResponse(body.response);
                            }
                        }
                        else {
                            callback.onFailure("missing body");
                        }
                    }
                    else {
                        callback.onFailure("missing response");
                    }
                }

                @Override
                public void onFailure(Call<APIResponse2<StatusResponse>> call, Throwable t) {
                    callback.onFailure(t.toString());
                }
            });
        }
    }

    public interface CommandResponseCallback {
        void onResponse(String response);
        void onFailure(String error);
    }

    public void sendCommandAsync(String url, final CommandResponseCallback callback) {
        if(!TextUtils.isEmpty(url)) {
            AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);
            final Call<APIResponse2<String>> call = service.sendCommand(url);
            Log.d(TAG, "sendCommandAsync: url=" + call.request().url().toString());
            call.enqueue(new Callback<APIResponse2<String>>() {
                @Override
                public void onResponse(Call<APIResponse2<String>> call, Response<APIResponse2<String>> response) {
                    if (response != null) {
                        APIResponse2<String> body = response.body();
                        if (body != null) {
                            if (!TextUtils.isEmpty(body.error)) {
                                Log.e(TAG, body.error);
                                callback.onFailure(body.error);
                            }
                            else {
                                callback.onResponse(body.response);
                            }
                        }
                    }
                    else {
                        callback.onFailure("missing response");
                    }
                }

                @Override
                public void onFailure(Call<APIResponse2<String>> call, Throwable t) {
                    callback.onFailure(t.toString());
                }
            });
        }
    }

    private String getAccessToken() throws Exception {
        if(mAccessToken == null) {
            AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);
            final Call<APIResponse<AccessToken>> call = service.getAccessToken();
            final Response<APIResponse<AccessToken>> response = call.execute();
            if(response == null) {
                Log.e(TAG, "api:get_token: empty response");
                throw new Exception("failed to get token");
            }

            APIResponse<AccessToken> body = response.body();
            if (body == null) {
                Log.e(TAG, "api:get_token: empty body");
                throw new Exception("failed to get token");
            }

            if (!TextUtils.isEmpty(body.error)) {
                Log.e(TAG, "api:get_token: got error: " + body.error);
                throw new Exception("failed to get token");
            }

            if(body.result == null) {
                Log.e(TAG, "api:get_token: empty result");
                throw new Exception("failed to get token");
            }

            mAccessToken = body.result.token;
        }

        return mAccessToken;
    }

    public interface SyncPlaylistCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public boolean syncPlaylist(final SyncPlaylistCallback callback) {
        try {
            String token = getAccessToken();

            AceStreamAPIService service = getAceStreamRetrofit().create(AceStreamAPIService.class);
            final Call<APIResponse<String>> call = service.syncPlaylist(token);
            Log.d(TAG, "api:sync_playlist");
            call.enqueue(new Callback<APIResponse<String>>() {
                @Override
                public void onResponse(Call<APIResponse<String>> call, Response<APIResponse<String>> response) {
                    if (response == null) {
                        callback.onFailure("missing response");
                        return;
                    }

                    APIResponse<String> body = response.body();
                    if (body == null) {
                        callback.onFailure("missing body");
                        return;
                    }

                    if (!TextUtils.isEmpty(body.error)) {
                        Log.e(TAG, "api:sync_playlist: got error: " + body.error);
                        callback.onFailure(body.error);
                        return;
                    }

                    callback.onSuccess();
                }

                @Override
                public void onFailure(Call<APIResponse<String>> call, Throwable t) {
                    callback.onFailure(t.toString());
                }
            });

            return true;
        }
        catch(Exception e) {
            Log.e(TAG, "api:sync_playlist: error", e);
            return false;
        }
    }

    private Retrofit getAceStreamRetrofit(){
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectionPool(mHttpConnectionPool )
                .connectTimeout(240, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl("http://127.0.0.1:6878")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public interface AceStreamAPIService {
        @GET("server/api?method=get_api_access_token")
        Call<APIResponse<AccessToken>> getAccessToken();

        @GET("server/api?method=playlist_sync&wait=300")
        Call<APIResponse<String>> syncPlaylist(@Query("token") String token);

        @GET("server/api?method=get_playlist&token=YUhjhfbF6G83jngF0&api_version=2&get_category_ids=1")
        Call<APIResponse<Playlist>> getPlaylist(
                @Query("force_update") int forceUpdate,
                @Query("get_hash_only") int get_hash_only
        );

        @GET("server/api?method=get_epg&token=YUhjhfbF6G83jngF0")
        Call<APIResponse<Map<String,Program[]>>> getEPG(@Query("id") String id);

        @GET("ace/getstream?format=json&use_api_events=1")
        Call<APIResponse2<PlaybackResponse>> getStream(@QueryMap Map<String, String> params);

        @GET("ace/manifest.m3u8?format=json&use_api_events=1&hlc=0&transcode_audio=1&transcode_mp3=0")
        Call<APIResponse2<PlaybackResponse>> getHLS(@QueryMap Map<String, String> params);

        @GET
        Call<APIResponse2<StatusResponse>> getStatus(@Url String url);

        @GET
        Call<APIResponse2<String>> sendCommand(@Url String url);

        @GET("server/api?method=get_media_files")
        Call<APIResponse<Map<String,MediaFile>>> getMediaFiles(@QueryMap Map<String, String> params);
    }

    public class ApiError extends Exception {
        public ApiError(String message) {
            super(message);
        }
    }
}
