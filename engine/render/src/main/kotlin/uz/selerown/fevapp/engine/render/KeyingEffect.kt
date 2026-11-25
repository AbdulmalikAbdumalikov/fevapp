@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import uz.selerown.fevapp.core.model.OverlayKeying

/**
 * Export effect for overlays: chroma key + mask written into the alpha
 * channel, so the compositor shows the main video through the removed parts.
 * The same formulas are used by the preview's AGSL shader (KeyingShaders).
 */
internal class KeyingEffect(private val keying: OverlayKeying) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(keying, useHdr)

    private class Program(private val keying: OverlayKeying, useHdr: Boolean) :
        BaseGlShaderProgram(useHdr, 1) {

        private val program = GlProgram(VERTEX, FRAGMENT).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setIntUniform("uChroma", if (keying.chroma) 1 else 0)
                val c = keying.keyColor
                program.setFloatsUniform("uKey", floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f))
                program.setFloatUniform("uSim", keying.strength * 0.5f)
                program.setIntUniform("uMask", keying.mask.ordinal)
                program.setIntUniform("uInvert", if (keying.invert) 1 else 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() {
                gl_Position = aFramePosition;
                vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        // uMask: 0 none, 1 ellipse, 2 rounded, 3 left half, 4 top half (GL: y up).
        const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform int uChroma;
            uniform vec3 uKey;
            uniform float uSim;
            uniform int uMask;
            uniform int uInvert;
            varying vec2 vUv;
            vec2 chroma(vec3 c) {
                return vec2(-0.1687 * c.r - 0.3313 * c.g + 0.5 * c.b, 0.5 * c.r - 0.4187 * c.g - 0.0813 * c.b);
            }
            void main() {
                vec4 c = texture2D(uTexSampler, vUv);
                float a = c.a;
                if (uChroma == 1) {
                    float d = distance(chroma(c.rgb), chroma(uKey));
                    a *= smoothstep(uSim, uSim + 0.08, d);
                }
                float m = 1.0;
                if (uMask == 1) {
                    vec2 q = (vUv - 0.5) * 2.0;
                    m = 1.0 - smoothstep(0.96, 1.0, dot(q, q));
                } else if (uMask == 2) {
                    vec2 q = abs(vUv - 0.5) - (0.5 - 0.18);
                    float dd = length(max(q, 0.0)) - 0.18;
                    m = 1.0 - smoothstep(-0.01, 0.0, dd);
                } else if (uMask == 3) {
                    m = step(vUv.x, 0.5);
                } else if (uMask == 4) {
                    m = step(0.5, vUv.y);
                }
                if (uInvert == 1 && uMask != 0) m = 1.0 - m;
                gl_FragColor = vec4(c.rgb, a * m);
            }
        """
    }
}
