package com.example.videotrimmerapplication.videoTrimmer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.videotrimmerapplication.R;
import com.example.videotrimmerapplication.videoTrimmer.utils.BackgroundExecutor;
import com.example.videotrimmerapplication.videoTrimmer.utils.UiThreadExecutor;


public class VideoTrimSeekBar extends View {

    private int aX, aY;
    private int bX, bY;
    private int cX, cY;
    private int dX, dY;
    private int thumbLX, thumbLY;
    private int thumbRX, thumbRY;
    private Path path;
    private Paint paint;
    private Paint thumbPaint;
    private Thumb mThumb;
    private int weight;
    private int paddingLeftRight = 20;
    private int touchRadius = 13;
    private int leftCircleRadius = 15;
    private int rightCircleRadius = 15;
    private boolean isFirst = true;
    private float videoCursorX;
    private long thumbMinDistance = 100;
    private long thumbMaxDistance = 10000;
    private OnSeekChangedListener mOnSeekChangedListener;
    private long mVideoDuration;
    private long seekFrom, seekTo;
    private Uri mVideoUri;
    private int mHeightView;
    private LongSparseArray<Bitmap> mBitmapList = null;
    private final Paint mShadow = new Paint();

    public VideoTrimSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoTrimSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public VideoTrimSeekBar(Context context) {
        super(context);
        init();
    }

    private void init() {
        path = new Path();
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);

        thumbPaint = new Paint();
        thumbPaint.setAntiAlias(true);
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setStyle(Paint.Style.FILL);

        int shadowColor = ContextCompat.getColor(getContext(), R.color.shadow_color);
        mShadow.setAntiAlias(true);
        mShadow.setColor(shadowColor);
        mShadow.setAlpha(150);

        mHeightView = getContext().getResources().getDimensionPixelOffset(R.dimen.frames_video_height);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            Point aPoint = new Point(thumbLX, thumbLY);
            Point bPoint = new Point(thumbRX, thumbRY);
            Point point = new Point((int) x, (int) y);
            boolean left = pointInsideCircle(aPoint, point);
            boolean right = pointInsideCircle(bPoint, point);
            if (left)
                mThumb = Thumb.LEFT;
            if (right)
                mThumb = Thumb.RIGHT;
            if (left && right) {
                if (x < aPoint.x)
                    mThumb = Thumb.LEFT;

                if (x > bPoint.x)
                    mThumb = Thumb.RIGHT;
            }
            if (mThumb == Thumb.LEFT) {
                leftCircleRadius = 20;
            } else if (mThumb == Thumb.RIGHT) {
                rightCircleRadius = 20;
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (mThumb == Thumb.LEFT) {
                if ((x < bX - thumbMinDistance || x < aX) && x > paddingLeftRight) {
                    moveThumb(x, Thumb.LEFT);
                    if (thumbRX - x >= thumbMaxDistance) {
                        moveThumb(x + thumbMaxDistance, Thumb.RIGHT);
                    }
                }
            } else if (mThumb == Thumb.RIGHT) {
                if ((aX < x - thumbMinDistance || x > bX) && x < weight - paddingLeftRight) {
                    moveThumb(x, Thumb.RIGHT);
                    if (x - thumbLX >= thumbMaxDistance) {
                        moveThumb(x - thumbMaxDistance, Thumb.LEFT);
                    }
                }
            }
            invalidate();
            return true;
        } else {
            leftCircleRadius = 15;
            rightCircleRadius = 15;
            mOnSeekChangedListener.onChanged(seekFrom, seekTo, true);
            mThumb = null;
            videoCursorX = aX;
            invalidate();
            return false;
        }
    }

    private void moveThumb(float x, Thumb thumb) {
        float oneSecValue = weight / mVideoDuration;

        if (thumb == Thumb.LEFT) {
            aX = (int) x;
            dX = (int) x;
            thumbLX = (int) x;
            seekFrom = (long) (x / oneSecValue);
        } else if (thumb == Thumb.RIGHT) {
            bX = (int) x;
            cX = (int) x;
            thumbRX = (int) x;
            seekTo = (long) (x / oneSecValue);
        }
        if (mOnSeekChangedListener != null)
            mOnSeekChangedListener.onChanged(seekFrom, seekTo, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isFirst) {
            weight = getWidth();
            int height = getHeight();

            videoCursorX = paddingLeftRight;

            aX = paddingLeftRight;
            aY = 0;

            bX = weight - paddingLeftRight;
            bY = 0;

            thumbRX = weight - paddingLeftRight;
            thumbRY = height / 2;

            cX = weight - paddingLeftRight;
            cY = height;

            dX = paddingLeftRight;
            dY = height;

            thumbLX = paddingLeftRight;
            thumbLY = height / 2;
            isFirst = false;
        }

        if (mBitmapList != null) {
            int x = paddingLeftRight;
            int imageWeight = (weight - (2 * paddingLeftRight)) / mBitmapList.size();
            for (int i = 0; i < mBitmapList.size(); i++) {
                Bitmap bitmap = mBitmapList.get(i);
                if (bitmap != null && !bitmap.isRecycled()) {
                    canvas.drawBitmap(bitmap, x, 0, null);
                    x = x + imageWeight;
                }
            }
        }

        path.reset();
        path.moveTo(aX, aY);
        path.lineTo(aX, aY);
        path.lineTo(bX, bY);
        path.lineTo(cX, cY);
        path.lineTo(dX, dY);
        path.lineTo(aX, aY);
        canvas.drawPath(path, paint);

        drawShadow(canvas);

        if (mThumb == null)
            drawCursor(canvas);

        path.reset();
        path.addCircle(thumbRX, thumbRY, rightCircleRadius, Path.Direction.CCW);
        path.addCircle(thumbLX, thumbLY, leftCircleRadius, Path.Direction.CCW);
        canvas.drawPath(path, thumbPaint);
    }

    private void drawShadow(@NonNull Canvas canvas) {
        Rect mRectLeft = new Rect(paddingLeftRight, 0, aX, mHeightView);
        canvas.drawRect(mRectLeft, mShadow);

        Rect mRectRight = new Rect(weight - paddingLeftRight, 0, bX, mHeightView);
        canvas.drawRect(mRectRight, mShadow);
    }

    private void drawCursor(@NonNull Canvas canvas) {
        Path path = new Path();
        path.moveTo(videoCursorX, 0);
        path.lineTo(videoCursorX, 0);
        path.lineTo(videoCursorX, mHeightView);
        canvas.drawPath(path, paint);
    }

    private boolean pointInsideCircle(Point aPoint, Point bPoint) {
        return Math.sqrt(Math.abs(aPoint.x - bPoint.x) + Math.abs(aPoint.y - bPoint.y)) < touchRadius;
    }

    public void setVideoDuration(long durationSec) {
        if (durationSec == 0) {
            Log.e("Video duration", "duration can not be zero");
            return;
        }
        mVideoDuration = durationSec;
        seekFrom = 0;
        seekTo = durationSec;
    }

    public void setVideoMinDuration(long durationSec) {
        thumbMinDistance = (weight / mVideoDuration) * durationSec;
    }

    public void setVideoMaxDuration(long durationSec) {
        thumbMaxDistance = (weight / mVideoDuration) * durationSec;
        if (durationSec < mVideoDuration) {
            moveThumb(paddingLeftRight, Thumb.LEFT);
            moveThumb(paddingLeftRight + thumbMaxDistance, Thumb.RIGHT);
        }
        invalidate();
    }

    public void setOnSeekChangedListener(OnSeekChangedListener onSeekChangedListener) {
        mOnSeekChangedListener = onSeekChangedListener;
    }

    public void setCurrentTime(long videoCurrentTime) {
        int absoluteWeight = weight - paddingLeftRight;
        long oneSecWeight = absoluteWeight / mVideoDuration;
        long a = oneSecWeight * videoCurrentTime / 1000;
        a += paddingLeftRight;
        if (a > aX) {
            if (a <= bX)
                videoCursorX = a;
            else {
                videoCursorX = bX;
                mOnSeekChangedListener.stopVideo();
            }
        } else
            videoCursorX = aX;
        invalidate();
    }

    public interface OnSeekChangedListener {
        void onChanged(long seekFrom, long seekTo, boolean isStarted);

        void stopVideo();
    }

    enum Thumb {
        LEFT,
        RIGHT
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minW = getPaddingLeft() + getPaddingRight() + getSuggestedMinimumWidth();
        int w = resolveSizeAndState(minW, widthMeasureSpec, 1);

        final int minH = getPaddingBottom() + getPaddingTop() + mHeightView;
        int h = resolveSizeAndState(minH, heightMeasureSpec, 1);

        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(final int w, int h, final int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        if (w != oldW && mBitmapList == null && mVideoUri != null) {
            getBitmap(w);
        }
    }

    private void getBitmap(final int viewWidth) {
        BackgroundExecutor.execute(new BackgroundExecutor.Task("1", 0L, "") {
            @Override
            public void execute() {
                try {
                    LongSparseArray<Bitmap> thumbnailList = new LongSparseArray<>();

                    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                    mediaMetadataRetriever.setDataSource(getContext(), mVideoUri);

                    // Retrieve media data
                    long videoLengthInMs = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;

                    // Set thumbnail properties (Thumbs are squares)
                    final int thumbWidth = mHeightView;
                    final int thumbHeight = mHeightView;

                    int numThumbs = (int) Math.ceil(((float) viewWidth) / thumbWidth);

                    final long interval = videoLengthInMs / numThumbs;

                    for (int i = 0; i < numThumbs; ++i) {
                        Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(i * interval, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        // TODO: bitmap might be null here, hence throwing NullPointerException. You were right
                        try {
                            bitmap = Bitmap.createScaledBitmap(bitmap, (viewWidth - 2 * paddingLeftRight) / numThumbs, thumbHeight, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (bitmap != null)
                            thumbnailList.put(i, bitmap);
                    }

                    mediaMetadataRetriever.release();
                    returnBitmaps(thumbnailList);
                } catch (final Throwable e) {
                    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
                    if (uncaughtExceptionHandler != null)
                        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), e);
                }
            }
        });
    }

    private void returnBitmaps(final LongSparseArray<Bitmap> thumbnailList) {
        UiThreadExecutor.runTask("1", () -> {
                    mBitmapList = thumbnailList;
                    invalidate();
                }
                , 0L);
    }

    public void setVideo(@NonNull Uri data) {
        mVideoUri = data;
        getBitmap(getWidth());
    }

    public void onDestroy() {
        BackgroundExecutor.cancelAll("1", true);
        BackgroundExecutor.cancelAll("2", true);
        UiThreadExecutor.cancelAll("1");
        if (mBitmapList != null) {
            int count = mBitmapList.size();
            for (int i = 0; i < count; i++) {
                Bitmap bmp = mBitmapList.get(i);
                if (!bmp.isRecycled()) {
                    bmp.recycle();
                }
            }
        }
        mBitmapList = null;
    }
}