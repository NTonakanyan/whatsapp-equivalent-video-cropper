package com.example.videotrimmerapplication;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.videotrimmerapplication.videoTrimmer.OnTrimVideoListener;
import com.example.videotrimmerapplication.videoTrimmer.VideoTrimSeekBar;
import com.example.videotrimmerapplication.videoTrimmer.utils.BackgroundExecutor;
import com.example.videotrimmerapplication.videoTrimmer.utils.TrimVideoUtils;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private VideoTrimSeekBar mVideoTrimSeekBar;
    private TextView mStartEnd;
    private Button mCutButton, mSelectVideoButton;
    private File mFile;
    private Handler handler;
    private Runnable runnable;
    private long mSeekFrom, mSeekTo;
    private SimpleExoPlayer simpleExoplayer;
    private long duration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        playerView = findViewById(R.id.activity_main_player);
        mVideoTrimSeekBar = findViewById(R.id.activity_main_video_trim_seek_bar);

        mStartEnd = findViewById(R.id.activity_main_video_trim_seek_bar_start_end);
        mCutButton = findViewById(R.id.activity_main_video_cut_button);
        mCutButton.setOnClickListener(view -> startVideoUpload());
        mSelectVideoButton = findViewById(R.id.activity_main_select_video);
        mSelectVideoButton.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Video"), 200);
        });
    }

    private void initVideoView() {
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, getString(R.string.app_name)), bandwidthMeter);
        // This is the MediaSource representing the media to be played.
        MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.fromFile(mFile));
        simpleExoplayer = ExoPlayerFactory.newSimpleInstance(this);
        playerView.setPlayer(simpleExoplayer);
        simpleExoplayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (duration == 0) {
                    duration = simpleExoplayer.getDuration() / 1000;
                    if (duration < 0)
                        duration = 0;
                    else
                        videoTrimSetVideoInformation();
                }
            }
        });
        simpleExoplayer.prepare(videoSource);
        simpleExoplayer.setPlayWhenReady(true);
    }

    private void videoTrimSetVideoInformation() {
        mVideoTrimSeekBar.setVideoDuration(duration);
        mVideoTrimSeekBar.setVideoMinDuration(5);
        mVideoTrimSeekBar.setVideoMaxDuration(30);
        mVideoTrimSeekBar.setOnSeekChangedListener(new VideoTrimSeekBar.OnSeekChangedListener() {
            @Override
            public void onChanged(long seekFrom, long seekTo, boolean isStarted) {
                mSeekFrom = seekFrom;
                mSeekTo = seekTo;
                setVideoStartEndInView();
                simpleExoplayer.seekTo(mSeekFrom * 1000);
            }

            @Override
            public void stopVideo() {
                playerView.getPlayer().stop();
                simpleExoplayer.seekTo(mSeekFrom * 1000);
            }
        });

        handler = new Handler();
        runnable = () -> {
            long videoCurrentTime = simpleExoplayer.getCurrentPosition();
            mVideoTrimSeekBar.setCurrentTime(videoCurrentTime);
            handler.postDelayed(runnable, 50);
        };
        handler.post(runnable);
    }

    @SuppressLint("DefaultLocale")
    private void setVideoStartEndInView() {
        mStartEnd.setText(String.format("%02d:%02d-%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(mSeekFrom * 1000),
                TimeUnit.MILLISECONDS.toSeconds(mSeekFrom * 1000),
                TimeUnit.MILLISECONDS.toMinutes(mSeekTo * 1000),
                TimeUnit.MILLISECONDS.toSeconds(mSeekTo * 1000)));
    }

    private void startVideoUpload() {
        BackgroundExecutor.execute(new BackgroundExecutor.Task("2", 0L, "") {
            @Override
            public void execute() {
                try {
                    TrimVideoUtils.startTrim(mFile, mFile.getPath(), mSeekFrom * 1000, mSeekTo * 1000, new OnTrimVideoListener() {
                        @Override
                        public void onTrimStarted() {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Video trim started", Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void getResult(Uri uri) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Video trim Finished", Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void cancelAction() {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Video trim canceled", Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    });
                } catch (final Throwable e) {
                    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
                    if (uncaughtExceptionHandler != null)
                        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), e);
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 200) {

                Uri selectedImageUri = data.getData();

                String selectedImagePath;
                selectedImagePath = getPath(this, selectedImageUri);
                if (selectedImagePath != null) {
                    mFile = new File(selectedImagePath);
                    duration = 0;
                    mVideoTrimSeekBar.setVideo(Uri.fromFile(mFile));
                    mVideoTrimSeekBar.setVisibility(View.GONE);
                    mVideoTrimSeekBar.setVisibility(View.VISIBLE);
                    initVideoView();
                }
            }
        }
    }

    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}