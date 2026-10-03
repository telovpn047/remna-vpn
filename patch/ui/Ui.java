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

    /* ======================= İkon (yumuşak, yuvarlak hatlı) ======================= */
    static final class Icon extends View {
        static final int PLUS = 1, GEAR = 2, REFRESH = 3, BOLT = 4, SIGNAL = 5, TRASH = 6, LINK = 7, CHEVRON = 8, CLOSE = 9, SHIELD = 10,
                GLOBE = 11, DOC = 12, PLAY = 13, CLOCK = 14, ROUTE = 15, SERVER = 16;
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
            float u = s / 24f;
            cv.save();
            cv.translate((w - s) / 2f, (h - s) / 2f);
            p.setStrokeWidth(1.75f * u);
            p.setStyle(Paint.Style.STROKE);
            p.setPathEffect(new android.graphics.CornerPathEffect(2.2f * u));
            path.reset();
            switch (type) {
                case PLUS:
                    cv.drawLine(12 * u, 6 * u, 12 * u, 18 * u, p);
                    cv.drawLine(6 * u, 12 * u, 18 * u, 12 * u, p);
                    break;
                case CLOSE:
                    cv.drawLine(7 * u, 7 * u, 17 * u, 17 * u, p);
                    cv.drawLine(17 * u, 7 * u, 7 * u, 17 * u, p);
                    break;
                case GEAR: { // modern "ayar kaydırıcıları"
                    float[] ys = {7, 12, 17}, ks = {15, 9, 13.5f};
                    for (int i = 0; i < 3; i++) {
                        cv.drawLine(4.5f * u, ys[i] * u, 19.5f * u, ys[i] * u, p);
                    }
                    Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                    f.setColor(p.getColor());
                    for (int i = 0; i < 3; i++) {
                        f.setStyle(Paint.Style.FILL);
                        cv.drawCircle(ks[i] * u, ys[i] * u, 2.4f * u, f);
                    }
                    break;
                }
                case REFRESH: {
                    RectF r = new RectF(5.5f * u, 5.5f * u, 18.5f * u, 18.5f * u);
                    cv.drawArc(r, -70, 300, false, p);
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(15.2f * u, 3.4f * u);
                    path.lineTo(19.4f * u, 6.0f * u);
                    path.lineTo(15.0f * u, 8.6f * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                }
                case BOLT:
                    p.setStyle(Paint.Style.FILL);
                    p.setPathEffect(new android.graphics.CornerPathEffect(1.6f * u));
                    path.moveTo(13.6f * u, 2.6f * u);
                    path.lineTo(5.6f * u, 13.4f * u);
                    path.lineTo(11.4f * u, 13.4f * u);
                    path.lineTo(10.4f * u, 21.4f * u);
                    path.lineTo(18.4f * u, 10.6f * u);
                    path.lineTo(12.6f * u, 10.6f * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                case SIGNAL: {
                    p.setStyle(Paint.Style.FILL);
                    p.setPathEffect(null);
                    float[] hs = {4, 7.5f, 11, 14.5f};
                    for (int i = 0; i < 4; i++) {
                        float x = (4.5f + i * 4.2f) * u;
                        cv.drawRoundRect(new RectF(x, (19 - hs[i]) * u, x + 2.8f * u, 19 * u), 1.4f * u, 1.4f * u, p);
                    }
                    break;
                }
                case TRASH:
                    cv.drawLine(5 * u, 7 * u, 19 * u, 7 * u, p);
                    cv.drawRoundRect(new RectF(9.5f * u, 4 * u, 14.5f * u, 7 * u), 1.5f * u, 1.5f * u, p);
                    cv.drawRoundRect(new RectF(6.8f * u, 7 * u, 17.2f * u, 20 * u), 2.5f * u, 2.5f * u, p);
                    break;
                case LINK:
                case ROUTE: { // iki nokta arasında kıvrımlı yol
                    p.setPathEffect(null);
                    cv.drawCircle(6 * u, 6.5f * u, 2.3f * u, p);
                    cv.drawCircle(18 * u, 17.5f * u, 2.3f * u, p);
                    path.moveTo(8.3f * u, 6.5f * u);
                    path.cubicTo(20 * u, 6.5f * u, 4 * u, 17.5f * u, 15.7f * u, 17.5f * u);
                    cv.drawPath(path, p);
                    break;
                }
                case CHEVRON:
                    path.moveTo(9.5f * u, 6.5f * u);
                    path.lineTo(15 * u, 12 * u);
                    path.lineTo(9.5f * u, 17.5f * u);
                    cv.drawPath(path, p);
                    break;
                case SHIELD:
                    path.moveTo(12 * u, 3 * u);
                    path.lineTo(19 * u, 5.8f * u);
                    path.cubicTo(19 * u, 13 * u, 16.2f * u, 18.4f * u, 12 * u, 21 * u);
                    path.cubicTo(7.8f * u, 18.4f * u, 5 * u, 13 * u, 5 * u, 5.8f * u);
                    path.close();
                    cv.drawPath(path, p);
                    path.reset();
                    path.moveTo(9 * u, 12 * u);
                    path.lineTo(11.2f * u, 14.2f * u);
                    path.lineTo(15.2f * u, 10 * u);
                    cv.drawPath(path, p);
                    break;
                case GLOBE:
                    p.setPathEffect(null);
                    cv.drawCircle(12 * u, 12 * u, 8.5f * u, p);
                    cv.drawOval(new RectF(8.2f * u, 3.5f * u, 15.8f * u, 20.5f * u), p);
                    cv.drawLine(3.8f * u, 12 * u, 20.2f * u, 12 * u, p);
                    break;
                case DOC:
                    cv.drawRoundRect(new RectF(5.5f * u, 3.5f * u, 18.5f * u, 20.5f * u), 3 * u, 3 * u, p);
                    cv.drawLine(9 * u, 9 * u, 15 * u, 9 * u, p);
                    cv.drawLine(9 * u, 12.5f * u, 15 * u, 12.5f * u, p);
                    cv.drawLine(9 * u, 16 * u, 12.5f * u, 16 * u, p);
                    break;
                case PLAY:
                    p.setStyle(Paint.Style.FILL);
                    p.setPathEffect(new android.graphics.CornerPathEffect(2.5f * u));
                    path.moveTo(8 * u, 5 * u);
                    path.lineTo(19.5f * u, 12 * u);
                    path.lineTo(8 * u, 19 * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                case CLOCK:
                    p.setPathEffect(null);
                    cv.drawCircle(12 * u, 12 * u, 8.5f * u, p);
                    cv.drawLine(12 * u, 7.5f * u, 12 * u, 12 * u, p);
                    cv.drawLine(12 * u, 12 * u, 15 * u, 14 * u, p);
                    break;
                case SERVER:
                    cv.drawRoundRect(new RectF(4.5f * u, 4.5f * u, 19.5f * u, 11 * u), 2.5f * u, 2.5f * u, p);
                    cv.drawRoundRect(new RectF(4.5f * u, 13 * u, 19.5f * u, 19.5f * u), 2.5f * u, 2.5f * u, p);
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(8 * u, 7.75f * u, 1.1f * u, p);
                    cv.drawCircle(8 * u, 16.25f * u, 1.1f * u, p);
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
            float core = R * 0.6f;
            float d = Ui.dp(getContext(), 1);

            // yumuşak dış hale
            float gl = state == OFF ? 0.16f : state == CONNECTING ? 0.2f + 0.14f * breathe : 0.26f + 0.1f * breathe;
            glow.setShader(new RadialGradient(cx, cy, R, new int[]{withA(c1, gl), withA(c2, gl * 0.45f), 0x00000000}, new float[]{0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, R, glow);

            // ince, soluk halka
            float r2 = core + 20 * d;
            ring.setStrokeWidth(1f * d);
            ring.setColor(withA(0xFFFFFFFF, 0.06f));
            cv.drawCircle(cx, cy, r2, ring);

            // durum yayı
            arc.setStrokeWidth(3f * d);
            if (state == CONNECTING) {
                arc.setShader(new android.graphics.SweepGradient(cx, cy, new int[]{0x00000000, withA(c1, 0.9f), c2}, new float[]{0f, 0.25f, 0.3f}));
                cv.save();
                cv.rotate(spin, cx, cy);
                cv.drawArc(new RectF(cx - r2, cy - r2, cx + r2, cy + r2), 0, 108, false, arc);
                cv.restore();
            } else if (state == ON) {
                arc.setShader(new android.graphics.SweepGradient(cx, cy, new int[]{c1, c2, c1}, null));
                arc.setAlpha((int) (150 + 80 * breathe));
                cv.drawCircle(cx, cy, r2, arc);
                arc.setAlpha(255);
            }

            // gövde: degrade + yumuşak gölge
            fill.setShader(new LinearGradient(cx - core, cy - core, cx + core, cy + core, c1, c2, Shader.TileMode.CLAMP));
            fill.setShadowLayer(24 * d, 0, 10 * d, withA(c2, 0.45f));
            cv.drawCircle(cx, cy, core, fill);
            fill.clearShadowLayer();
            // cam parlaklığı (üstte)
            Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
            gloss.setShader(new LinearGradient(cx, cy - core, cx, cy + core * 0.2f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, core, gloss);
            Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.2f * d);
            edge.setColor(0x33FFFFFF);
            cv.drawCircle(cx, cy, core - 0.6f * d, edge);

            // ince, yuvarlak güç simgesi
            float gr = core * 0.34f;
            glyph.setStrokeWidth(core * 0.075f);
            cv.drawArc(new RectF(cx - gr, cy - gr, cx + gr, cy + gr), -58, 296, false, glyph);
            cv.drawLine(cx, cy - gr * 1.22f, cx, cy - gr * 0.12f, glyph);
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
