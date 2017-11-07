package shaderprintf;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.io.*;

import static shaderprintf.ShaderPrintf.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.glfw.GLFW.*;


public class Window {

    private static long windowHandle = MemoryUtil.NULL;

    public static void setupGL(int width, int height, String title) {
        boolean glfwInit = glfwInit();
        if (!glfwInit) {
            throw new RuntimeException("Could not setup GLFW");
        }

        GLFWErrorCallback.createPrint(System.err).set();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL)
            throw new RuntimeException("Could not open window");

        glfwMakeContextCurrent(windowHandle);
        glfwShowWindow(windowHandle);

        GL.createCapabilities();

        glfwSwapInterval(0);
    }

    public static void closeGL() {
        GL.destroy();
        glfwDestroyWindow(windowHandle);
    }

    public static void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    public static Point getMouse() {
        double[] xBuf = new double[1];
        double[] yBuf = new double[1];
        glfwGetCursorPos(windowHandle, xBuf, yBuf);
        return new Point(xBuf[0], yBuf[0]); // not the most optimized way but it is supposed to be just for testing
    }

    // probably old and ineffective method
    private static String read(String path) {
        InputStream stream = Window.class.getResourceAsStream("/"+path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return reader.lines().reduce((acc, line) -> acc+"\n"+line).get();
    }

    public static int createShader(final String path, int shaderType) {
        String source = read(path);
        int shader = glCreateShader(shaderType);

        glShaderSourcePrint(shader, source);
        glCompileShader(shader);
        int success = glGetShaderi(shader, GL_COMPILE_STATUS);
        if (success == 0) {
            String log = glGetShaderInfoLog(shader);
            System.out.printf("log of compiling (error %d) %s:\n>%s<\n", success, path, log);
        }
        return shader;
    }

    public static boolean shouldContinue() {
        return !glfwWindowShouldClose(windowHandle);
    }

    public static void pollEvents() {
        glfwPollEvents();
    }

    public static class Point {

        public double x;
        public double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

    }
}
