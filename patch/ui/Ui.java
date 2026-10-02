package com.v2ray.ang.remna;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Elle çizilen ikonlar ve özel bileşenler (font/emoji bağımlılığı yok). */
final class Ui {
    private Ui() {}

    static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /* ======================= İkon ======================= */
    static final class Icon extends View {
        static final int PLUS = 1, GEAR = 2, REFRESH = 3, BOLT = 4, SIGNAL = 5, TRASH = 6, LINK = 7, CHEVRON = 8, CLOSE = 9, SHIELD = 10;
        final int type;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Icon(Context c, int type, int color) {
            super(c);
            this.type = type;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int c) { p.setColor(c); invalidate(); }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            float u = s / 24f; // 24'lük ızgara
            cv.save();
            cv.translate((w - s) / 2f, (h - s) / 2f);
            p.setStrokeWidth(2f * u);
            p.setStyle(Paint.Style.STROKE);
            path.reset();
            switch (type) {
                case PLUS:
                    cv.drawLine(12 * u, 5 * u, 12 * u, 19 * u, p);
                    cv.drawLine(5 * u, 12 * u, 19 * u, 12 * u, p);
                    break;
                case CLOSE:
                    cv.drawLine(6 * u, 6 * u, 18 * u, 18 * u, p);
                    cv.drawLine(18 * u, 6 * u, 6 * u, 18 * u, p);
                    break;
                case GEAR: {
                    cv.drawCircle(12 * u, 12 * u, 3.2f * u, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        float x1 = (float) (12 + Math.cos(a) * 6.2), y1 = (float) (12 + Math.sin(a) * 6.2);
                        float x2 = (float) (12 + Math.cos(a) * 8.6), y2 = (float) (12 + Math.sin(a) * 8.6);
                        cv.drawLine(x1 * u, y1 * u, x2 * u, y2 * u, p);
                    }
                    cv.drawCircle(12 * u, 12 * u, 6.2f * u, p);
                    break;
                }
                case REFRESH: {
                    RectF r = new RectF(5 * u, 5 * u, 19 * u, 19 * u);
                    cv.drawArc(r, -60, 290, false, p);
                    path.moveTo(15.5f * u, 4f * u);
                    path.lineTo(18.6f * u, 6.4f * u);
                    path.lineTo(15.6f * u, 9f * u);
                    cv.drawPath(path, p);
                    break;
                }
                case BOLT:
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
                    p.setStrokeWidth(1f * u);
                    path.moveTo(13.5f * u, 2.5f * u);
                    path.lineTo(5.5f * u, 13.5f * u);
                    path.lineTo(11.5f * u, 13.5f * u);
                    path.lineTo(10.5f * u, 21.5f * u);
                    path.lineTo(18.5f * u, 10.5f * u);
                    path.lineTo(12.5f * u, 10.5f * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                case SIGNAL:
                    p.setStrokeWidth(2.4f * u);
                    cv.drawLine(6 * u, 19 * u, 6 * u, 15 * u, p);
                    cv.drawLine(10 * u, 19 * u, 10 * u, 12 * u, p);
                    cv.drawLine(14 * u, 19 * u, 14 * u, 8.5f * u, p);
                    cv.drawLine(18 * u, 19 * u, 18 * u, 5 * u, p);
                    break;
                case TRASH:
                    cv.drawLine(4.5f * u, 7 * u, 19.5f * u, 7 * u, p);
                    cv.drawLine(9.5f * u, 7 * u, 10 * u, 4.5f * u, p);
                    cv.drawLine(10 * u, 4.5f * u, 14 * u, 4.5f * u, p);
                    cv.drawLine(14 * u, 4.5f * u, 14.5f * u, 7 * u, p);
                    path.moveTo(6.5f * u, 7 * u);
                    path.lineTo(7.5f * u, 19.5f * u);
                    path.lineTo(16.5f * u, 19.5f * u);
                    path.lineTo(17.5f * u, 7 * u);
                    cv.drawPath(path, p);
                    break;
                case LINK: {
                    RectF a = new RectF(3 * u, 8.5f * u, 13 * u, 15.5f * u), b = new RectF(11 * u, 8.5f * u, 21 * u, 15.5f * u);
                    cv.drawRoundRect(a, 3.5f * u, 3.5f * u, p);
                    cv.drawRoundRect(b, 3.5f * u, 3.5f * u, p);
                    break;
                }
                case CHEVRON:
                    path.moveTo(9 * u, 6 * u);
                    path.lineTo(15 * u, 12 * u);
                    path.lineTo(9 * u, 18 * u);
                    cv.drawPath(path, p);
                    break;
                case SHIELD:
                    path.moveTo(12 * u, 2.8f * u);
                    path.lineTo(19.5f * u, 5.8f * u);
                    path.cubicTo(19.5f * u, 13 * u, 16.5f * u, 18.5f * u, 12 * u, 21.2f * u);
                    path.cubicTo(7.5f * u, 18.5f * u, 4.5f * u, 13 * u, 4.5f * u, 5.8f * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
            }
            cv.restore();
        }
    }

    /* ======================= Büyük güç düğmesi ======================= */
    static final class PowerButton extends View {
        static final int OFF = 0, CONNECTING = 1, ON = 2;
        int state = OFF;
        int c1 = 0xFF3B82F6, c2 = 0xFF8B5CF6;
        float spin = 0f, breathe = 0f;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), glyph = new Paint(Paint.ANTI_ALIAS_FLAG),
                ring = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        ValueAnimator anim;

        PowerButton(Context c) {
            super(c);
            glyph.setStyle(Paint.Style.STROKE);
            glyph.setStrokeCap(Paint.Cap.ROUND);
            glyph.setColor(0xFFFFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1400);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                spin = f * 360f;
                breathe = (float) (0.5 + 0.5 * Math.sin(f * Math.PI * 2));
                invalidate();
            });
        }

        void setState(int s) {
            state = s;
            if (s == ON) { c1 = 0xFF10B981; c2 = 0xFF06B6D4; }
            else if (s == CONNECTING) { c1 = 0xFFF59E0B; c2 = 0xFFF97316; }
            else { c1 = 0xFF3B82F6; c2 = 0xFF8B5CF6; }
            if (!anim.isStarted()) anim.start();
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            anim.cancel();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!anim.isStarted()) anim.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float R = Math.min(w, h) / 2f;
            float core = R * 0.62f;
            float d = Ui.dp(getContext(), 1);

            // dış parıltı
            float gl = state == OFF ? 0.18f : state == CONNECTING ? 0.22f + 0.18f * breathe : 0.32f + 0.12f * breathe;
            glow.setShader(new RadialGradient(cx, cy, R, new int[]{withA(c1, gl), withA(c2, gl * 0.5f), 0x00000000}, new float[]{0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, R, glow);

            // halkalar
            ring.setStrokeWidth(1.2f * d);
            ring.setColor(withA(c1, 0.22f));
            cv.drawCircle(cx, cy, core + 22 * d, ring);
            ring.setColor(withA(c1, 0.12f));
            cv.drawCircle(cx, cy, core + 40 * d, ring);

            // bağlanırken dönen yay
            if (state == CONNECTING) {
                arc.setStrokeWidth(3.5f * d);
                arc.setShader(new LinearGradient(cx - core, cy, cx + core, cy, c1, c2, Shader.TileMode.CLAMP));
                float r2 = core + 22 * d;
                cv.drawArc(new RectF(cx - r2, cy - r2, cx + r2, cy + r2), spin, 100, false, arc);
            } else if (state == ON) {
                arc.setStrokeWidth(3.5f * d);
                arc.setShader(new LinearGradient(cx - core, cy, cx + core, cy, c1, c2, Shader.TileMode.CLAMP));
                float r2 = core + 22 * d;
                cv.drawCircle(cx, cy, r2, arc);
            }

            // gövde
            fill.setShader(new LinearGradient(cx - core, cy - core, cx + core, cy + core, c1, c2, Shader.TileMode.CLAMP));
            fill.setShadowLayer(18 * d, 0, 6 * d, withA(c2, 0.55f));
            cv.drawCircle(cx, cy, core, fill);
            fill.clearShadowLayer();

            // güç simgesi
            float gr = core * 0.38f;
            glyph.setStrokeWidth(core * 0.09f);
            cv.drawArc(new RectF(cx - gr, cy - gr, cx + gr, cy + gr), -60, 300, false, glyph);
            cv.drawLine(cx, cy - gr * 1.25f, cx, cy - gr * 0.15f, glyph);
        }

        static int withA(int c, float a) {
            return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0x00FFFFFF);
        }
    }

    /* ======================= Anahtar (switch) ======================= */
    static final class Switch extends View {
        boolean on;
        float pos;
        final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        int onColor = 0xFF22D3EE;

        Switch(Context c) {
            super(c);
            knob.setColor(0xFFFFFFFF);
        }

        void set(boolean v, boolean animate) {
            on = v;
            if (!animate) { pos = v ? 1 : 0; invalidate(); return; }
            ValueAnimator a = ValueAnimator.ofFloat(pos, v ? 1 : 0);
            a.setDuration(160);
            a.addUpdateListener(x -> { pos = (float) x.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            int off = 0x33FFFFFF;
            int col = blend(off, onColor, pos);
            track.setColor(col);
            cv.drawRoundRect(new RectF(0, 0, w, h), r, r, track);
            float k = r - Ui.dp(getContext(), 3);
            float x = r + (w - 2 * r) * pos;
            cv.drawCircle(x, r, k, knob);
        }

        static int blend(int a, int b, float t) {
            int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
        }
    }
}
