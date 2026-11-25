package uz.selerown.fevapp.engine.render

/**
 * AGSL (Android 13+) version of the export keying shader, for the preview.
 * Coordinates are in pixels with y DOWN, so "top half" is y < h/2.
 * AGSL colors are premultiplied: the result is the color scaled by alpha.
 */
object KeyingShaders {
    const val AGSL = """
        uniform shader content;
        uniform float2 size;
        uniform float3 key;
        uniform float sim;
        uniform int chroma;
        uniform int mask;
        uniform int invertMask;
        float2 cbcr(float3 c) {
            return float2(-0.1687 * c.r - 0.3313 * c.g + 0.5 * c.b, 0.5 * c.r - 0.4187 * c.g - 0.0813 * c.b);
        }
        half4 main(float2 p) {
            half4 c = content.eval(p);
            float a = 1.0;
            if (chroma == 1) {
                float3 rgb = c.a > 0.0 ? float3(c.rgb) / float(c.a) : float3(0.0);
                a *= smoothstep(sim, sim + 0.08, distance(cbcr(rgb), cbcr(key)));
            }
            float2 uv = p / size;
            float m = 1.0;
            if (mask == 1) {
                float2 q = (uv - 0.5) * 2.0;
                m = 1.0 - smoothstep(0.96, 1.0, dot(q, q));
            } else if (mask == 2) {
                float2 q = abs(uv - 0.5) - (0.5 - 0.18);
                float dd = length(max(q, 0.0)) - 0.18;
                m = 1.0 - smoothstep(-0.01, 0.0, dd);
            } else if (mask == 3) {
                m = step(uv.x, 0.5);
            } else if (mask == 4) {
                m = step(uv.y, 0.5);
            }
            if (invertMask == 1 && mask != 0) m = 1.0 - m;
            return c * half(a * m);
        }
    """
}
