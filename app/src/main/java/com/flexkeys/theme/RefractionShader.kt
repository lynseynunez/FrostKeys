package com.flexkeys.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.annotation.TargetApi
import android.os.Build

/**
 * "Liquid glass" refraction for Android 13+: around the edge of every key the blurred background is bent,
 * like light passing through a thick pane, with a thin bright rim. Written in AGSL and run on the GPU as a
 * [RenderEffect] over the blurred backdrop. Kept in its own class so older Android versions never load it.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
class RefractionShader(droplet: Boolean = false, lens: Boolean = false) {

    private val shader = RuntimeShader(if (lens) LENS_AGSL else if (droplet) DROPLET_AGSL else AGSL)

    /** The effect for [count] key rectangles (left, top, right, bottom in backdrop pixels), to be chained after the blur. */
    fun effect(rects: FloatArray, count: Int, radius: Float, bevel: Float, strength: Float): RenderEffect {
        shader.setFloatUniform("rects", rects)
        shader.setFloatUniform("count", count.toFloat())
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("strength", strength)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    companion object {
        const val MAX_RECTS = 56

        /**
         * The water-droplet look: no hard ring. The bend eases in from the very edge, every key is a slightly different
         * drop (it leans its own way and is a little thicker or thinner), the lower edge bends more as if gravity pulled
         * the water down, the edge shows a faint color fringe like real glass, and a soft highlight sits on the upper left.
         */
        private const val DROPLET_AGSL = """
uniform shader content;
uniform float4 rects[56];
uniform float count;
uniform float radius;
uniform float bevel;
uniform float strength;

float sdBox(float2 p, float2 c, float2 h, float r) {
    float2 q = abs(p - c) - h + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 offset = float2(0.0, 0.0);
    float disp = 0.0;
    float glint = 0.0;
    float glow = 0.0;
    for (int i = 0; i < 56; i++) {
        if (float(i) >= count) { break; }
        float4 r = rects[i];
        float2 c = (r.xy + r.zw) * 0.5;
        float2 h = (r.zw - r.xy) * 0.5;
        float d = sdBox(coord, c, h, radius);
        if (d < 0.0) {
            float seed = fract(sin(dot(c, float2(12.9898, 78.233))) * 43758.5453);
            float bev = bevel * (0.85 + 0.3 * seed);
            if (d > -bev) {
                float2 n = float2(
                    sdBox(coord + float2(1.0, 0.0), c, h, radius) - sdBox(coord - float2(1.0, 0.0), c, h, radius),
                    sdBox(coord + float2(0.0, 1.0), c, h, radius) - sdBox(coord - float2(0.0, 1.0), c, h, radius));
                float nl = length(n);
                if (nl < 0.0001) {
                    n = float2(0.0, -1.0);
                } else {
                    n = n / nl;
                }
                float2 lean = float2(seed - 0.5, fract(seed * 7.31) - 0.5);
                n = normalize(n + 0.35 * lean);
                float t = clamp(-d / bev, 0.0, 1.0);
                float prof = 1.0 - t;
                prof = prof * prof * (3.0 - 2.0 * prof);
                float feather = smoothstep(0.0, bev * 0.22, -d);
                float gravity = 1.0 + 0.3 * n.y;
                offset = offset - n * (strength * prof * feather * gravity);
                disp = max(disp, prof * feather);
                float2 light = normalize(float2(-0.55, -0.85));
                float facing = dot(n, light);
                float band = (1.0 - smoothstep(0.0, bev * 0.45, -d)) * feather;
                glint = max(glint, pow(max(facing, 0.0), 2.4) * band);
                glow = max(glow, pow(max(-facing, 0.0), 3.0) * band * 0.5);
            }
        }
    }
    if (disp <= 0.0) {
        return content.eval(coord);
    }
    half4 g = content.eval(coord + offset);
    half4 rr = content.eval(coord + offset * (1.0 - 0.18 * disp));
    half4 bb = content.eval(coord + offset * (1.0 + 0.18 * disp));
    half3 col = half3(rr.r, g.g, bb.b);
    col = col + half3(glint * 0.22 + glow * 0.10);
    col = min(col, half3(1.0));
    return half4(col, g.a);
}
"""

        /**
         * The Sakuri lens look, borrowed from the Sakuri launcher's liquid glass: the bend is strongest right at the edge and
         * fades out quickly toward the middle of the key, red, green and blue bend by slightly different amounts so the rim
         * has a thin color fringe, and a soft sheen follows a light from the upper left.
         */
        private const val LENS_AGSL = """
uniform shader content;
uniform float4 rects[56];
uniform float count;
uniform float radius;
uniform float bevel;
uniform float strength;

float sdBox(float2 p, float2 c, float2 h, float r) {
    float2 q = abs(p - c) - h + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 offset = float2(0.0, 0.0);
    float shine = 0.0;
    float amount = 0.0;
    for (int i = 0; i < 56; i++) {
        if (float(i) >= count) { break; }
        float4 r = rects[i];
        float2 c = (r.xy + r.zw) * 0.5;
        float2 h = (r.zw - r.xy) * 0.5;
        float d = sdBox(coord, c, h, radius);
        if (d < 0.0 && d > -bevel) {
            float2 n = float2(
                sdBox(coord + float2(1.0, 0.0), c, h, radius) - sdBox(coord - float2(1.0, 0.0), c, h, radius),
                sdBox(coord + float2(0.0, 1.0), c, h, radius) - sdBox(coord - float2(0.0, 1.0), c, h, radius));
            float nl = length(n);
            if (nl < 0.0001) {
                n = float2(0.0, -1.0);
            } else {
                n = n / nl;
            }
            float t = clamp(-d / bevel, 0.0, 1.0);
            float m = pow(1.0 - t, 2.2);
            float feather = smoothstep(0.0, 1.5, -d);
            offset = offset - n * (strength * m * feather);
            amount = max(amount, m * feather);
            float facing = dot(n, normalize(float2(-0.55, -0.83)));
            shine = max(shine, (facing * facing * 0.75 + max(-facing, 0.0) * 0.18) * exp(-(-d) / (bevel * 0.12)) * feather);
        }
    }
    if (amount <= 0.0) {
        return content.eval(coord);
    }
    half4 g = content.eval(coord + offset * 1.08);
    half4 rr = content.eval(coord + offset);
    half4 bb = content.eval(coord + offset * 1.16);
    half3 col = half3(rr.r, g.g, bb.b) + half3(shine * 0.20);
    col = min(col, half3(1.0));
    return half4(col, g.a);
}
"""

        private const val AGSL = """
uniform shader content;
uniform float4 rects[56];
uniform float count;
uniform float radius;
uniform float bevel;
uniform float strength;

float sdBox(float2 p, float2 c, float2 h, float r) {
    float2 q = abs(p - c) - h + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 offset = float2(0.0, 0.0);
    float rim = 0.0;
    for (int i = 0; i < 56; i++) {
        if (float(i) >= count) { break; }
        float4 r = rects[i];
        float2 c = (r.xy + r.zw) * 0.5;
        float2 h = (r.zw - r.xy) * 0.5;
        float d = sdBox(coord, c, h, radius);
        if (d < 0.0 && d > -bevel) {
            float t = 1.0 + d / bevel;
            float2 toCenter = c - coord;
            float len = max(length(toCenter), 0.001);
            offset += (toCenter / len) * (t * t) * strength;
            rim = max(rim, 1.0 - smoothstep(0.0, 2.5, abs(d + 1.2)));
        }
    }
    half4 col = content.eval(coord + offset);
    col.rgb = min(col.rgb + half3(rim * 0.16), half3(1.0));
    return col;
}
"""
    }
}
