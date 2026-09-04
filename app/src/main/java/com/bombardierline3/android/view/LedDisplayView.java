package com.bombardierline3.android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.os.Handler;
import android.os.Looper;

import com.bombardierline3.android.controller.MetroController;

public class LedDisplayView extends View {

    private String rawTopText = "";
    private String rawBottomText = "";
    private MetroController.DisplayState currentSystemState = MetroController.DisplayState.DOOR_OPEN_STATIC;
    
    private boolean isBlackoutActive = false;
    private boolean isInitialized = false;

    private float globalOffset = 0;
    
    private final int LED_SIZE = 14;
    private final int LED_GAP = 4;
    private final int SPACING = LED_SIZE + LED_GAP;

    private final int TOP_ROWS = 17;
    private final int BOTTOM_ROWS = 15;
    private final int TOTAL_ROWS = TOP_ROWS + BOTTOM_ROWS;

    private final Paint unlitPaint = new Paint();
    private final Paint topLitPaint = new Paint();
    private final Paint bottomLitPaint = new Paint();
    private final Paint textPaint = new Paint();

    private Bitmap textBuffer;
    private int[] cachedPixels;
    private int cachedBufferWidth;
    private int cachedBufferHeight;

    private long lastFrameTime = -1;
    private final float scrollPixelsPerSecond = 450f;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (!isInitialized || isBlackoutActive) {
                lastFrameTime = -1;
            } else {
                long now = System.nanoTime();
                if (lastFrameTime != -1) {
                    float dt = (now - lastFrameTime) / 1_000_000_000f;
                    if (currentSystemState == MetroController.DisplayState.NEXT_STATION || 
                        currentSystemState == MetroController.DisplayState.THIS_STATION) {
                        globalOffset -= scrollPixelsPerSecond * dt;
                    }
                }
                lastFrameTime = now;
                invalidate();
            }
            handler.postDelayed(this, 16); // ~60fps
        }
    };

    public LedDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.BLACK);

        unlitPaint.setColor(Color.rgb(15, 15, 15));
        unlitPaint.setStyle(Paint.Style.FILL);

        topLitPaint.setColor(Color.rgb(255, 215, 0)); // Yellow
        topLitPaint.setStyle(Paint.Style.FILL);

        bottomLitPaint.setColor(Color.rgb(0, 255, 60)); // Green
        bottomLitPaint.setStyle(Paint.Style.FILL);

        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(false);
        textPaint.setColor(Color.WHITE);

        handler.post(animator);
    }

    public void clearDisplayForBlackout() {
        isBlackoutActive = true;
        invalidate();
        handler.postDelayed(() -> {
            isBlackoutActive = false;
            invalidate();
        }, 500);
    }

    public void applySystemDisplayPayload(MetroController.DisplayState state, String top, String bottom) {
        this.isInitialized = true;
        this.isBlackoutActive = false;
        
        boolean needsRebuild = !top.equals(rawTopText) || !bottom.equals(rawBottomText) || (state != currentSystemState);
        
        this.rawTopText = top;
        this.rawBottomText = bottom;
        this.currentSystemState = state;
        
        if (needsRebuild) {
            rebuildBitmapTemplate();
            globalOffset = 0;
            if (state == MetroController.DisplayState.NEXT_STATION || state == MetroController.DisplayState.THIS_STATION) {
                globalOffset = getWidth() > 0 ? getWidth() : 2000;
            }
        }
    }

    private void rebuildBitmapTemplate() {
        if (rawTopText.isEmpty() && rawBottomText.isEmpty()) return;

        float topWidth = textPaint.measureText(rawTopText);
        float bottomWidth = textPaint.measureText(rawBottomText);
        
        int reqWidth = (int) Math.max(topWidth, bottomWidth) + 100;
        int reqHeight = TOTAL_ROWS;

        Bitmap rawBuffer = Bitmap.createBitmap(reqWidth, reqHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rawBuffer);
        
        if (currentSystemState == MetroController.DisplayState.DOOR_OPEN_STATIC || 
            currentSystemState == MetroController.DisplayState.DOOR_CLOSING) {
            
            float tx = (reqWidth - topWidth) / 2f;
            float bx = (reqWidth - bottomWidth) / 2f;
            canvas.drawText(rawTopText, tx, TOP_ROWS - 4, textPaint);
            canvas.drawText(rawBottomText, bx, reqHeight - 4, textPaint);
        } else {
            canvas.drawText(rawTopText, 0, TOP_ROWS - 4, textPaint);
            canvas.drawText(rawBottomText, 0, reqHeight - 4, textPaint);
        }

        cachedBufferWidth = rawBuffer.getWidth();
        cachedBufferHeight = rawBuffer.getHeight();
        cachedPixels = new int[cachedBufferWidth * cachedBufferHeight];
        rawBuffer.getPixels(cachedPixels, 0, cachedBufferWidth, 0, 0, cachedBufferWidth, cachedBufferHeight);
        
        // Thresholding
        for (int i = 0; i < cachedPixels.length; i++) {
            int alpha = (cachedPixels[i] >> 24) & 0xFF;
            if (alpha > 128) {
                cachedPixels[i] = 0xFFFFFFFF;
            } else {
                cachedPixels[i] = 0x00000000;
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (isInitialized) {
            rebuildBitmapTemplate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        int startY = (h / 2) - ((TOTAL_ROWS * SPACING) / 2);

        // Draw unlit
        for (int y = 0; y < TOTAL_ROWS; y++) {
            int rY = startY + (y * SPACING);
            for (int x = 0; x < w / SPACING; x++) {
                int rX = x * SPACING;
                canvas.drawCircle(rX + LED_SIZE/2f, rY + LED_SIZE/2f, LED_SIZE/2f, unlitPaint);
            }
        }

        if (!isInitialized || isBlackoutActive || cachedPixels == null) {
            return;
        }

        int activeStartX = 0;
        if (currentSystemState == MetroController.DisplayState.DOOR_OPEN_STATIC || 
            currentSystemState == MetroController.DisplayState.DOOR_CLOSING) {
            int textPixelWidth = cachedBufferWidth * SPACING;
            activeStartX = (w / 2) - (textPixelWidth / 2);
        } else {
            activeStartX = (int) globalOffset;
            int textPixelWidth = cachedBufferWidth * SPACING;
            if (globalOffset + textPixelWidth < 0) {
                globalOffset = w;
            }
        }

        for (int y = 0; y < cachedBufferHeight; y++) {
            int rowBase = y * cachedBufferWidth;
            int renderY = startY + (y * SPACING);
            Paint p = (y < TOP_ROWS) ? topLitPaint : bottomLitPaint;

            for (int x = 0; x < cachedBufferWidth; x++) {
                int rgb = cachedPixels[rowBase + x];
                if ((rgb & 0xFF000000) != 0x00000000) {
                    int renderX = activeStartX + (x * SPACING);
                    if (renderX >= 0 && renderX <= w) {
                        canvas.drawCircle(renderX + LED_SIZE/2f, renderY + LED_SIZE/2f, LED_SIZE/2f, p);
                    }
                }
            }
        }
    }
}
