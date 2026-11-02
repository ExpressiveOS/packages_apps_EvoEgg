/*
 * Copyright (C) 2019-2021 The Evolution X Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Referenced from https://github.com/AICP/packages_apps_Settings/blob/n7.1/src/com/android/settings/aicp/Nyandroid.java

package org.evolution.easteregg;

import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.HashMap;
import java.util.Random;

import org.evolution.easteregg.R;

public class EasterEgg extends Activity {
    final static boolean DEBUG = false;

    public static class Board extends FrameLayout {
        public static final boolean FIXED_STARS = true;
        public static final int NUM_CATS = 20;
        // The Bloom mark's petals (violet, blue, green, coral), mid tones, so the
        // half-transparent stars read on light and dark backgrounds alike
        static final int[] STAR_COLORS = { 0xFFAE69FC, 0xFF417BFC, 0xFF4AD29D, 0xFFFD7771 };

        // Settings.Secure key with the banner colours of the holiday theme that is
        // on, written by the holiday themes in Settings and also read by the
        // ExpressiveOS wordmark: "<id>;<sweep on dark>;<sweep on light>;<fringe on
        // dark>;<fringe on light>", a sweep being 4 RRGGBB colours split by ','.
        // Unset, empty or anything else means the Bloom colours above.
        static final String KEY_HOLIDAY_PALETTE = "expressiveos_holiday_palette";

        static Random sRNG = new Random();

        static float lerp(float a, float b, float f) {
            return (b - a) * f + a;
        }

        static float randfrange(float a, float b) {
            return lerp(a, b, sRNG.nextFloat());
        }

        // A holiday theme's colours for the stars while its banner is on, else
        // Bloom's: its mid tones on the dark theme's background and its deep tones
        // on the light theme's, where the mids would hardly show at half opacity
        static int[] starColors(Context context) {
            final String value;
            try {
                value = Settings.Secure.getString(
                        context.getContentResolver(), KEY_HOLIDAY_PALETTE);
            } catch (RuntimeException e) {
                return STAR_COLORS;
            }
            if (value == null) return STAR_COLORS;

            // -1 keeps empty trailing groups, so "a;b;c;d;e;" counts as six
            final String[] groups = value.trim().split(";", -1);
            if (groups.length != 5 || groups[0].isEmpty() || groups[0].indexOf(',') >= 0) {
                return STAR_COLORS;
            }
            final boolean night = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            final int[] colors = parseColors(night ? groups[1] : groups[2]);
            return colors != null ? colors : STAR_COLORS;
        }

        // Exactly four opaque colours from RRGGBB hex split by ',', else null
        static int[] parseColors(String group) {
            final String[] parts = group.split(",", -1);
            if (parts.length != 4) return null;
            final int[] colors = new int[4];
            for (int i = 0; i < 4; i++) {
                final String part = parts[i];
                if (part.length() != 6) return null;
                int rgb = 0;
                for (int j = 0; j < 6; j++) {
                    final int digit = hexDigit(part.charAt(j));
                    if (digit < 0) return null;
                    rgb = (rgb << 4) | digit;
                }
                colors[i] = 0xFF000000 | rgb;
            }
            return colors;
        }

        // Only ASCII hex digits (Character.digit also takes other scripts' digits)
        static int hexDigit(char c) {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            return -1;
        }

        public class FlyingCat extends ImageView {
            public static final float VMAX = 1000.0f;
            public static final float VMIN = 100.0f;

            public float v;

            public float dist;
            public float z;

            public FlyingCat(Context context, AttributeSet as) {
                super(context, as);
                setImageResource(R.drawable.evoandroid_anim); // @@@

                if (DEBUG) setBackgroundColor(0x80FF0000);
            }

            public void reset() {
                final float scale = lerp(0.1f, 2f, z);
                setScaleX(scale);
                setScaleY(scale);

                setX(-scale * getWidth() + 1);
                setY(randfrange(0, Board.this.getHeight() - scale * getHeight()));
                v = lerp(VMIN, VMAX, z);

                dist = 0;
            }

            public void update(float dt) {
                dist += v * dt;
                setX(getX() + v * dt);
            }
        }

        TimeAnimator mAnim;

        public Board(Context context, AttributeSet as) {
            super(context, as);

            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            // setBackgroundColor(0xFFFFFFF);
        }

        private void reset() {
            removeAllViews();

            final ViewGroup.LayoutParams wrap = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            if (FIXED_STARS) {
                final int[] colors = starColors(getContext());
                for (int i = 0; i < 20; i++) {
                    ImageView fixedStar = new ImageView(getContext(), null);
                    if (DEBUG) fixedStar.setBackgroundColor(0x8000FF80);
                    fixedStar.setImageResource(R.drawable.star_anim); // @@@
                    fixedStar.setColorFilter(colors[i % colors.length]);
                    fixedStar.setAlpha(0.5F);
                    addView(fixedStar, wrap);
                    final float scale = randfrange(0.1f, 1f);
                    fixedStar.setScaleX(scale);
                    fixedStar.setScaleY(scale);
                    fixedStar.setX(randfrange(0, getWidth()));
                    fixedStar.setY(randfrange(0, getHeight()));
                    final AnimationDrawable anim = (AnimationDrawable) fixedStar.getDrawable();
                    postDelayed(anim::start, (int) randfrange(0, 1000));
                }
            }

            for (int i = 0; i < NUM_CATS; i++) {
                FlyingCat nv = new FlyingCat(getContext(), null);
                addView(nv, wrap);
                nv.z = ((float) i / NUM_CATS);
                nv.z *= nv.z;
                nv.reset();
                nv.setX(randfrange(0, Board.this.getWidth()));
                final AnimationDrawable anim = (AnimationDrawable) nv.getDrawable();
                postDelayed(anim::start, (int) randfrange(0, 1000));
            }

            if (mAnim != null) {
                mAnim.cancel();
            }
            mAnim = new TimeAnimator();
            mAnim.setTimeListener((animation, totalTime, deltaTime) -> {
                for (int i = 0; i < getChildCount(); i++) {
                    View v = getChildAt(i);
                    if (!(v instanceof FlyingCat)) continue;
                    FlyingCat nv = (FlyingCat) v;
                    nv.update(deltaTime / 1000f);
                    final float catWidth = nv.getWidth() * nv.getScaleX();
                    final float catHeight = nv.getHeight() * nv.getScaleY();
                    if (nv.getX() + catWidth < -2
                            || nv.getX() > getWidth() + 2
                            || nv.getY() + catHeight < -2
                            || nv.getY() > getHeight() + 2) {
                        nv.reset();
                    }
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            post(() -> {
                reset();
                mAnim.start();
            });
        }


        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            // None yet when the board goes before its first layout's reset()
            if (mAnim != null) {
                mAnim.cancel();
            }
        }

        @Override
        public boolean isOpaque() {
            return true;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
	try {
		getActionBar().hide();
	}
	catch (Exception e) {}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override
    public void onResume() {
        super.onResume();
        Board mBoard = new Board(this, null);
        setContentView(mBoard);

        mBoard.setOnSystemUiVisibilityChangeListener(vis -> {
            if (0 == (vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)) {
                // EasterEgg.this.finish();
            }
        });
    }

    @Override
    public void onUserInteraction() {
//        finish();
    }
}
