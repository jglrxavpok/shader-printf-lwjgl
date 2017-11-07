package shaderprintf;

/**	MIT License

 Copyright(c) 2017 Pauli Kemppinen

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files(the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions :

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.

 <hr/>
 Java port of "shader-printf" for LWJGL
 */

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.IllegalFormatConversionException;
import java.util.LinkedList;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.system.MemoryUtil.*;

public class ShaderPrintf {

    /** (added) here sizeof(unsigned) is set to 4 */
    private static final int SIZEOF_UNSIGNED = 4;

    /**
     * Creates a shader storage buffer object to be used with the print functionality.<br/>
     * Any SSBO can be used, this is just for convenience and does nothing special.<br/>
     * (added) The default size is set to 16*1024*1024
     */
    public static int createPrintBuffer() {
        return createPrintBuffer(16*1024*1024);
    }

    /**
     * Creates a shader storage buffer object to be used with the print functionality.<br/>
     * Any SSBO can be used, this is just for convenience and does nothing special.<br/>
     */
    public static int createPrintBuffer(int size) {
        int printBuffer = glCreateBuffers();
        glNamedBufferData(printBuffer, size*SIZEOF_UNSIGNED, GL_STREAM_READ);
        return printBuffer;
    }

    /**
     * Deletes the given buffer
     */
    public static void deletePrintBuffer(int printBuffer) {
        glDeleteBuffers(printBuffer);
    }

    /**
     * Binds a print buffer to the current program; call anywhere between glUseProgram and the draw/dispatch call
     */
    public static void bindPrintBuffer(int program, int printBuffer) {
        // reset the buffer; only first value relevant (writing position / size of output), rest is filled up to the index this states
        ByteBuffer beginIterator = memCalloc(SIZEOF_UNSIGNED).putInt(0, 1);
        glNamedBufferSubData(printBuffer, 0, beginIterator);

        // bind to whatever slot we happened to get
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, glGetProgramResourceIndex(program, GL_SHADER_STORAGE_BLOCK, "printBuffer"), printBuffer);

        memFree(beginIterator); // (added) valid because glNamedBufferSubData **copies** the content
    }

    /**
     * Fetches the printed buffer from VRAM and turns it into an String
     */
    public static String getPrintBufferString(int printBuffer) {
        // get the size of what we want to read and the size of the print buffer
        ByteBuffer printedSizePointer = memAlloc(SIZEOF_UNSIGNED);
        IntBuffer bufferSizePointer = memAllocInt(1);
        glGetNamedBufferSubData(printBuffer, 0, printedSizePointer);
        glGetNamedBufferParameteriv(printBuffer, GL_BUFFER_SIZE, bufferSizePointer);

        int bufferSize = bufferSizePointer.get(0);
        int printedSize = printedSizePointer.getInt()-1;
        memFree(bufferSizePointer);
        memFree(printedSizePointer);

        bufferSize /= SIZEOF_UNSIGNED;

        // make sure we're not reading past the maximum size
        if (printedSize > bufferSize)
            printedSize = bufferSize;

        // this vector will hold the CPU copy of the print buffer
        int[] printfData = new int[printedSize]; // (added) here we start at 0 because Java allows to directly get the length of the array

        // get the rest of the buffer data (the actual text)
        glGetNamedBufferSubData(printBuffer, SIZEOF_UNSIGNED, printfData);

        // the final string we're going to build
        StringBuilder result = new StringBuilder();

        // (added) used to print numbers
        StringBuilder format = new StringBuilder();


        // to hold the temporary results of formatting
        //char[] intermediate = new char[1024]; (added) moved to second inner for-loop to use Java's standard library

        // this loop parses the formatting of the result
        for(int i = 0; i < printedSize; i++) {
            // % indicates the beginning of a formatted input
            if ((char)printfData[i] == '%') {
                // if followed by another %, we're actually supposed to print '%'
                if ((char)printfData[i + 1] == '%') {
                    result.append("%");
                    i++;
                }
                // otherwise we'll be printing numbers
                else {
                    // first parse out the possible vector size
                    int vecSize = 1;
                    format.setLength(0); // (added) clears the builder
                    while (!"eEfFgGdiuoxXaA".contains(String.valueOf((char)printfData[i]))) {
                        if ((char)printfData[i] == '^') {
                            vecSize = (char)printfData[i + 1] - '0';
                            i += 2;
                        }
                        else {
                            format.append(String.valueOf((char)printfData[i]));
                            i++;
                        }
                    }
                    char formatChar = (char)printfData[i];
                    if(formatChar == 'u') // (added), Java does not support unsigned integers
                        formatChar = 'd';
                    format.append(formatChar);

                    // determine whether we'll have to do type conversion
                    boolean isFloatType = !"diuoxX".contains(String.valueOf((char)printfData[i]));

                    // print to a temporary buffer, add result to string (for vectors add parentheses and commas as in "(a, b, c)")
                    if (vecSize > 1) result.append("(");

                    for (int j = 0; j < vecSize; ++j) {
                        i++;
                        String intermediate;
                        if (isFloatType)
                            intermediate = String.format(format.toString(), Float.intBitsToFloat(printfData[i]));
                        else
                            intermediate = String.format(format.toString(), printfData[i]);
                        result.append(intermediate);
                        if (vecSize > 1 && j < vecSize - 1) result.append(", ");
                    }

                    if (vecSize > 1) result.append(")");
                }
            }
            else // otherwise it's a single character, just add it to the result
                result.append((char)printfData[i]);
        }

        // ... and we're done.
        return result.toString();
    }

    /**
     * (added) Implementation of C 'isspace' because {@link Character#isSpaceChar(char)} does not handle tabulations
     */
    private static boolean isspace(char c) {
        switch (c) {
            case ' ': return true;
            case '\t': return true;
            case '\n': return true;
            case '\u240B': return true;
            case '\f': return true;
            case '\r': return true;
            default: return Character.isSpaceChar(c);
        }
    }

    /**
     * Helper function that finds a function call
     */
    public static int findCall(final String source, final String function) {
        // search for any occurrence of function name
        int tentative = source.indexOf(function);
        if (tentative == -1) {
            return -1;
        }

        // see if it's inside a comment
        boolean commentLong = false;
        boolean commentRow = false;
        for (int i = 0; i < tentative; ++i) {
            if (source.charAt(i) == '/' && source.charAt(i + 1) == '*') commentLong = true;
            if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') commentLong = false;
            if (source.charAt(i) == '/' && source.charAt(i + 1) == '/') commentRow = true;
            if (source.charAt(i) == '\n') commentRow = false;
        }
        int tentativeEnd = tentative + function.length();
        // if the tentative instance is not good...
        if (commentRow || commentLong || // comment
                (tentative > 0 && !isspace(source.charAt(tentative - 1))) || // is a part of a longer string
                tentativeEnd >= source.length() || // is the end of the file
                !(isspace(source.charAt(tentativeEnd)) || source.charAt(tentativeEnd) == '(')) { // is a part of a longer string
            // ... find the next one
            int result = findCall(source.substring(tentative + 1), function);
            if (result == -1)
                return -1;
		    else
                return tentative + 1 + result;
        }
    	else // otherwise return it
            return tentative;
    }

    /**
     * A preprocessor for shader source
     */
    public static String addPrintToSource(String source) {

        // get rid of comments beforehand
        String commentedSource = source;
        source = "";
        // (added) manual swap here
        //swap(source, commentedSource);

        boolean commentLong = false;
        boolean commentRow = false;
        boolean inStringFlag = false; // (added) name change to avoid conflicts with variables with same name below
        for (int i = 0; i < commentedSource.length(); ++i) {
            if (commentedSource.charAt(i) == '"' && (i==0||commentedSource.charAt(i + 1) !='\\')) inStringFlag = !inStringFlag;
            if (!inStringFlag) {
                if (i < commentedSource.length() - 1 && commentedSource.charAt(i) == '/' && commentedSource.charAt(i + 1) == '*') { commentLong = true; i++; continue; }
                if (i < commentedSource.length() - 1 && commentedSource.charAt(i) == '*' && commentedSource.charAt(i + 1) == '/') { commentLong = false; i++; continue; }
                if (i < commentedSource.length() - 1 && commentedSource.charAt(i) == '/' && commentedSource.charAt(i + 1) == '/') { commentRow = true; i++; continue; }
                if (commentedSource.charAt(i) == '\n') commentRow = false;
            }
            if (!commentLong && !commentRow)
                source += String.valueOf(commentedSource.charAt(i));
        }

        // insert our buffer definition after the glsl version define
        int version = source.indexOf("#version");
        int lineAfterVersion = 2, bufferInsertOffset = 0;

        if (version != -1) {
            ++bufferInsertOffset;

            for (int i = 0; i < version; ++i)
                if (source.charAt(i) == '\n')
                    ++lineAfterVersion;

            for (int i = version; i < source.length(); ++i)
                if (source.charAt(i) == '\n')
                    break;
                else
                    bufferInsertOffset += 1;
        }

        // go through all printfs in the shader
        int printfLoc = findCall(source, "printf");
        while (printfLoc != -1) {

            int printfEndLoc = printfLoc;

            int parentheses = 0;
            boolean inString = false;

            // gather the arguments
            LinkedList<String> args = new LinkedList<>();
            while (true) {

                printfEndLoc++; // FIXME: semble être la source d'erreur

                if (!inString && parentheses == 1 && source.charAt(printfEndLoc) == ',') {
                    String arg = "";

                    int argLoc = printfEndLoc + 1;
                    int argParentheses = 0;
                    while (argParentheses > 0 || source.charAt(argLoc) != ',') {
                        if (source.charAt(argLoc) == '(') ++argParentheses;
                        if (source.charAt(argLoc) == ')') --argParentheses;
                        if (argParentheses < 0) break;
                        if (source.charAt(argLoc) != ' ')
                            arg = arg + String.valueOf(source.charAt(argLoc));
                        ++argLoc;
                    }
                    args.add(arg);
                }

                if (source.charAt(printfEndLoc) == '"')
                    inString = !inString;
                if (source.charAt(printfEndLoc) == '\\')
                    ++printfEndLoc;
                if (!inString && source.charAt(printfEndLoc) == '(')
                    parentheses++;
                if (!inString && source.charAt(printfEndLoc) == ')') {
                    parentheses--;
                    if (parentheses == 0) {
                        do { printfEndLoc++; } while (source.charAt(printfEndLoc) != ';');
                        break;
                    }
                }
            }

            // come up with a list of data insertions that match the printf call
            String replacement = "";
            int argumentIndex = 0, writeSize = 0;
            inString = false;
            for (int i = printfLoc; i < printfEndLoc; ++i) {

                if (source.charAt(i) == '"')
                    inString = !inString;
                if (inString && source.charAt(i) == '\\') {
                    char ch = '\\';
                    switch (source.charAt(i + 1)) {
                        case '\'': ch = '\''; break;
                        case '\"': ch = '\"'; break;
                        case '?': ch = '?'; break;
                        case '\\': ch = '\\'; break;
                        case 'a': ch = '\u0007'; break; // (added) BELL character
                        case 'b': ch = '\b'; break;
                        case 'f': ch = '\f'; break;
                        case 'n': ch = '\n'; break;
                        case 'r': ch = '\r'; break;
                        case 't': ch = '\t'; break;
                        case 'v': ch = '\u240B'; break; // (added) vertical tab
                        default: ch = ' ';
                    }
                    replacement += "printData[printIndex++]=" + String.valueOf((int)ch) + ";";
                    writeSize++;
                    i++;
                }
                else if (inString && source.charAt(i) != '"') {
                    replacement += "printData[printIndex++]=" + String.valueOf((int)source.charAt(i)) + ";";
                    writeSize++;
                }
                if (inString && source.charAt(i) == '%')
                    if (source.charAt(i + 1) == '%') {
                    i++;
                    replacement += "printData[printIndex++]=" + String.valueOf((int)source.charAt(i)) + ";";
                    writeSize++;
                }
                else {
                    int vecSize = 1;
                    while (!"eEfFgGdiuoxXaA".contains(String.valueOf(source.charAt(i)))) {
                        // a special feature to support vector prints
                        if (source.charAt(i) == '^')
                            vecSize = source.charAt(i + 1) - '0';
                        i++;
                        replacement += "printData[printIndex++]=" + String.valueOf((int)source.charAt(i)) + ";";
                        writeSize++;
                    }
                    // store the actual data in the element after the format string
                    for (int j = 0; j < vecSize; ++j) {
                        String arg = args.get(argumentIndex);
                        if (vecSize > 1)
                            arg = "(" + arg + ")." + "xyzw".charAt(j);
                        switch (source.charAt(i)) {
                            case 'e': case 'E': case 'f': case 'F': case 'g': case 'G': case 'x': case 'X':
                                replacement += "printData[printIndex++]=floatBitsToUint(" + arg + ");"; break;
                            default:
                                replacement += "printData[printIndex++]=" + arg + ";"; break;
                        }
                        writeSize++;
                    }
                    argumentIndex++;
                }
            }

            source = source.substring(0, printfLoc) + "if(printfWriter){" + "uint printIndex=min(atomicAdd(printData[0]," + String.valueOf(writeSize) + "u),printData.length()-" + String.valueOf(writeSize) + "u);" + replacement + "}" + source.substring(printfEndLoc + 1);

            printfLoc = findCall(source, "printf");
        }

        // insert the ssbo definition and some helper functions after the #version line
        return source.substring(0, bufferInsertOffset) + "\nbuffer printBuffer{uint printData[];};bool printfWriter = false;void enablePrintf(){printfWriter=true;}void disablePrintf(){printfWriter=false;}\n#line " + String.valueOf(lineAfterVersion) + "\n" + source.substring(bufferInsertOffset);
    }

    /**
     * Replacement for glShaderSource that parses printf commands into buffer insertions
     * <br/>(added) variable length argument for 'strings' for convenience
     */
    public static void glShaderSourcePrint(int shader, final String... strings) {
        // first combine all of the potential source files to a single string
        String source = "";
        for (int i = 0; i < strings.length; ++i) {
            source += strings[i];
        }
        // parse
        source = addPrintToSource(source);

        // do the compilation
        glShaderSource(shader, source);
    }
}
