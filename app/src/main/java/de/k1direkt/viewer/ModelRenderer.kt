package de.k1direkt.viewer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Einfache 3D-Vorschau mit Druckplatte des K1 Max (300 × 300 mm). Drehen mit einem Finger, Zoomen mit zwei. */
class ModelSurfaceView(context: Context) : GLSurfaceView(context) {
    val renderer = ModelRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.zoom = (renderer.zoom / detector.scaleFactor).coerceIn(0.15f, 6f)
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setMesh(mesh: Mesh) {
        queueEvent { renderer.setMesh(mesh) }
        requestRender()
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
            MotionEvent.ACTION_UP -> performClick()
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> { lastX = event.x; lastY = event.y }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                renderer.yaw -= (event.x - lastX) * 0.4f
                renderer.pitch = (renderer.pitch + (event.y - lastY) * 0.4f).coerceIn(-89f, 89f)
                lastX = event.x; lastY = event.y
                requestRender()
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}

class ModelRenderer : GLSurfaceView.Renderer {
    @Volatile var yaw = -60f
    @Volatile var pitch = 25f
    @Volatile var zoom = 1f

    private var meshProgram = 0
    private var lineProgram = 0
    private var meshVbo = 0
    private var normalVbo = 0
    private var vertexCount = 0
    private var gridVbo = 0
    private var gridCount = 0
    private var pendingMesh: Mesh? = null
    private var currentMesh: Mesh? = null
    private var center = floatArrayOf(0f, 0f, 30f)
    private var radius = 100f
    private var offset = floatArrayOf(0f, 0f, 0f)

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)
    private var aspect = 1f

    fun setMesh(mesh: Mesh) {
        currentMesh = mesh
        pendingMesh = mesh
        if (meshProgram != 0) uploadMesh()
    }

    private fun uploadMesh() {
        val mesh = pendingMesh ?: return
        pendingMesh = null
        if (meshVbo == 0) {
            val ids = IntArray(2)
            GLES20.glGenBuffers(2, ids, 0)
            meshVbo = ids[0]; normalVbo = ids[1]
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.positions.size * 4, buffer(mesh.positions), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.normals.size * 4, buffer(mesh.normals), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        vertexCount = mesh.positions.size / 3
        // Modell mittig auf die Platte stellen
        offset = floatArrayOf(
            -(mesh.min[0] + mesh.max[0]) / 2f,
            -(mesh.min[1] + mesh.max[1]) / 2f,
            -mesh.min[2],
        )
        center = floatArrayOf(0f, 0f, mesh.sizeZ / 2f)
        radius = max(max(mesh.sizeX, mesh.sizeY), mesh.sizeZ).coerceAtLeast(10f) * 0.9f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.09f, 0.10f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        meshProgram = program(MESH_VS, MESH_FS)
        lineProgram = program(LINE_VS, LINE_FS)
        meshVbo = 0; normalVbo = 0
        buildGrid()
        if (pendingMesh == null) pendingMesh = currentMesh
        uploadMesh()
    }

    private fun buildGrid() {
        val lines = ArrayList<Float>()
        val half = 150f
        var v = -half
        while (v <= half + 0.1f) {
            lines += listOf(v, -half, 0f, v, half, 0f, -half, v, 0f, half, v, 0f)
            v += 25f
        }
        val arr = lines.toFloatArray()
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        gridVbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, arr.size * 4, buffer(arr), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        gridCount = arr.size / 3
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val dist = radius * 2.6f * zoom
        val y = Math.toRadians(yaw.toDouble())
        val p = Math.toRadians(pitch.toDouble())
        val ex = center[0] + (dist * cos(p) * cos(y)).toFloat()
        val ey = center[1] + (dist * cos(p) * sin(y)).toFloat()
        val ez = center[2] + (dist * sin(p)).toFloat()
        Matrix.setLookAtM(view, 0, ex, ey, ez, center[0], center[1], center[2], 0f, 0f, 1f)
        Matrix.perspectiveM(proj, 0, 40f, aspect, dist * 0.02f, dist * 20f)

        // Druckplatte
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)
        GLES20.glUseProgram(lineProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(lineProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(lineProgram, "uColor"), 0.35f, 0.38f, 0.44f, 1f)
        val lPos = GLES20.glGetAttribLocation(lineProgram, "aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridVbo)
        GLES20.glEnableVertexAttribArray(lPos)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridCount)
        GLES20.glDisableVertexAttribArray(lPos)

        if (vertexCount == 0) return
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, offset[0], offset[1], offset[2])
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)
        GLES20.glUseProgram(meshProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(meshProgram, "uMVP"), 1, false, mvp, 0)
        val aPos = GLES20.glGetAttribLocation(meshProgram, "aPos")
        val aNormal = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalVbo)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun buffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data); position(0)
        }

    private fun shader(type: Int, src: String): Int = GLES20.glCreateShader(type).also {
        GLES20.glShaderSource(it, src)
        GLES20.glCompileShader(it)
    }

    private fun program(vs: String, fs: String): Int = GLES20.glCreateProgram().also {
        GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(it)
    }

    private companion object {
        const val MESH_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                vNormal = aNormal;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """
        const val MESH_FS = """
            precision mediump float;
            varying vec3 vNormal;
            void main() {
                vec3 n = normalize(vNormal);
                float key = abs(dot(n, normalize(vec3(0.4, -0.5, 0.75))));
                float fill = abs(dot(n, normalize(vec3(-0.6, 0.4, 0.3))));
                vec3 base = vec3(0.30, 0.56, 1.0);
                gl_FragColor = vec4(base * (0.25 + 0.65 * key + 0.25 * fill), 1.0);
            }
        """
        const val LINE_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            void main() { gl_Position = uMVP * vec4(aPos, 1.0); }
        """
        const val LINE_FS = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """
    }
}
