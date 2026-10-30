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
class RefractionShader {

    private val shader = RuntimeShader(AGSL)

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
