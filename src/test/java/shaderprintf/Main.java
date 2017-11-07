package shaderprintf;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import static shaderprintf.ShaderPrintf.*;

public class Main {

    public static void main(String[] args) {
        Window.setupGL(1280, 720, "printf test");

        // (added) 'createShader' uses the methods in ShaderPrintf to handle the printf calls
        int vertex = Window.createShader("vertex.glsl", GL_VERTEX_SHADER), fragment = Window.createShader("fragment.glsl", GL_FRAGMENT_SHADER);
        int program = glCreateProgram();

        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);

        int err = glGetProgrami(program, GL_LINK_STATUS);
        if(err == 0)
            System.err.println("Error linking: "+glGetProgramInfoLog(program));


        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        boolean loop = true;
        while (loop) {
            Window.pollEvents();

            glClearColor(.0f, .0f, .0f, .0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glUseProgram(program);

            Window.Point mouse = Window.getMouse();
            glUniform2i(glGetUniformLocation(program, "mouse"), (int)mouse.x, (int)mouse.y);

            // create a buffer to hold the printf results
            int printBuffer = createPrintBuffer();
            // bind it to the current program
            bindPrintBuffer(program, printBuffer);

            // do any amount of draw/compute that appends to the buffer
            glDrawArrays(GL_TRIANGLES, 0, 3);

            // convert to string, output to console
            System.out.printf("%s\n", getPrintBufferString(printBuffer));
            // clean up
            deletePrintBuffer(printBuffer);

            Window.swapBuffers();

            loop = Window.shouldContinue();
        }


        glBindVertexArray(0);
        glDeleteVertexArrays(vao);
        glDeleteShader(vertex); glDeleteShader(fragment);
        glDeleteProgram(program);

        Window.closeGL();
    }
}
