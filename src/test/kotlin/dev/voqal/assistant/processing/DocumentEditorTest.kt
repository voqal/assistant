package dev.voqal.assistant.processing

import io.vertx.core.json.JsonObject
import org.junit.Test
import org.junit.jupiter.api.Assertions.*

class DocumentEditorTest {

    private val solution = """
        class Solution:
            def climbStairs(self, n: int) -> int:
                print("hello")
    """.trimIndent()

    @Test
    fun extractCodeBlock1() {
        val code = """
        ```PYTHON
        class Solution:
            def climbStairs(self, n: int) -> int:
                print("hello")
        ```
        """.trimIndent()

        val extractedCode = DocumentEditor.doStrictExtractCodeBlock(code)
        assertEquals(solution, extractedCode)
    }

    @Test
    fun extractCodeBlock2() {
        val code = """
        ```python

        class Solution:
            def climbStairs(self, n: int) -> int:
                print("hello")

        ```
        """.trimIndent()

        val extractedCode = DocumentEditor.doStrictExtractCodeBlock(code)
        assertEquals(solution, extractedCode)
    }

    @Test
    fun codeLlamaStyle1() {
        val json = """
            {
              "id": "87243e9d5e0646e9-DFW",
              "created": 1712767919,
              "model": "codellama/CodeLlama-7b-Instruct-hf",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "  Here's the code for the transcription:\n```\npublic class Main {\n    public static void main(String[] args) {\n        int i = 4;\n        int hundred = 100;\n        System.out.println(i + \" in a hundred is \" + (i * hundred));\n    }\n}\n```\nThe caret position is denoted by `↕`."
                  }
                }
              ]
            }
        """.trimIndent()
        val jsonObject = JsonObject(json)

        val extractedCode = DocumentEditor.doStrictExtractCodeBlock(
            jsonObject.getJsonArray("choices").getJsonObject(0).getJsonObject("message").getString("content")
        )
        assertEquals(
            """
            public class Main {
                public static void main(String[] args) {
                    int i = 4;
                    int hundred = 100;
                    System.out.println(i + " in a hundred is " + (i * hundred));
                }
            }
            """.trimIndent(), extractedCode
        )
    }

    @Test
    fun mixtralStyle1() {
        val text = "`for (int i = 0; i < 100; i++) {`"
        val extractedCode = DocumentEditor.extractCodeBlock(text)
        assertEquals("for (int i = 0; i < 100; i++) {", extractedCode)
    }

    @Test
    fun gemmaStyle1() {
        val text = "```kotlin\nfor (i in 0 until 100) {\n\n}\n```"
        val extractedCode = DocumentEditor.extractCodeBlock(text)
        assertEquals("for (i in 0 until 100) {\n\n}", extractedCode)
    }

    @Test
    fun gradleTask() {
        val text = "```groovy\n" +
                "task installWindows(type: Copy) {\n" +
                "\n" +
                "}\n" +
                "```"
        val extractedCode = DocumentEditor.extractCodeBlock(text)
        assertEquals("task installWindows(type: Copy) {\n\n}", extractedCode)
        assertTrue(DocumentEditor.isEmptyCodeBlock(extractedCode))
    }

    @Test
    fun emptyPythonFunction() {
        val text = "```python\n" +
                "def doSomething():\n" +
                "    pass\n" +
                "```"
        val extractedCode = DocumentEditor.extractCodeBlock(text)
        assertEquals("def doSomething():\n    pass", extractedCode)
        assertTrue(DocumentEditor.isEmptyCodeBlock(extractedCode))
    }

    @Test
    fun findText() {
        val text = """
            public class Main {

                public void aFunction() {
                    printHelloWorld();
                }

                public void printHelloWorld() {
                    System.out.println("Hello World");
                }

                public void anotherFunction() {
                    printHelloWorld();
                }
            }
        """.trimIndent()
        val codeBlock = """
            ```
            public void printHelloWorld() {
                System.out.println("Hello World");
            }
            ```
        """.trimIndent()
        val needle = DocumentEditor.extractCodeBlock(codeBlock)
        assertNotNull(DocumentEditor.findText(needle, text))
    }

    @Test
    fun spacingInLanguageName() {
        val codeBlock = """
            ```ECMASCRIPT 6
            class NewClass {
            
            }
            ```
        """.trimIndent()
        val extractedCode = DocumentEditor.extractCodeBlock(codeBlock)
        assertEquals("class NewClass {\n\n}", extractedCode)
    }

    @Test
    fun cancelVuiInteraction() {
        val responseCode = "```{\"cancel\": true}```"
        assertTrue(DocumentEditor.checkForVuiInteraction("cancel", responseCode))
    }

    @Test
    fun cancelVuiInteraction2() {
        val responseCode = "```{\"cancel\": true}```\n"
        assertTrue(DocumentEditor.checkForVuiInteraction("cancel", responseCode))
    }

    @Test
    fun tabbedResponseCode() {
        val responseCode = """
            ```
            /**
             *
             */
            public static class Savepoint_statementContext extends ParserRuleContext {
                public TerminalNode ID() { return getToken(PLSQLParser.ID, 0); }
            }
            ```
        """.trimIndent()

        //fixed indented code, replace leading 4 spaces with tab
        var fixedResponseCode = responseCode.lines()
            .joinToString("\n") { "\t$it" }
            .replace("    ", "\t")
        //remove indent from first and last line
        fixedResponseCode = fixedResponseCode.replaceFirst("\t", "")
        val lastTab = fixedResponseCode.lastIndexOf("\t")
        fixedResponseCode = fixedResponseCode.substring(0, lastTab) + fixedResponseCode.substring(lastTab + 1)

        val extractedCode = DocumentEditor.extractCodeBlock(fixedResponseCode)
        assertTrue(extractedCode.startsWith("\t"))
    }

    @Test
    fun languageNameOnNewLine() {
        val responseCode = "```\njava\npublic class Test {\n\n}\n```"
        val extractedCode = DocumentEditor.extractCodeBlock(responseCode)
        assertEquals(
            "public class Test {\n\n}",
            extractedCode
        )
    }

    @Test
    fun packageNameOnNewLine() {
        val responseCode = "```\npackage main\n\nimport \"fmt\"\n\ntype RemoveMethod struct{}\n```"
        val extractedCode = DocumentEditor.extractCodeBlock(responseCode)
        assertEquals(
            "package main\n\nimport \"fmt\"\n\ntype RemoveMethod struct{}",
            extractedCode
        )
    }

    @Test
    fun doubleBackticksError() {
        val responseCode = """
            ```
            ```java
            public class RemoveMethod {
                public int add(int x, int y) {
                    return x + y;
                }
            }
            ```
            ```
        """.trimIndent()
        val extractedCode = DocumentEditor.extractCodeBlock(responseCode)
        assertEquals(
            "public class RemoveMethod {\n" +
                    "    public int add(int x, int y) {\n" +
                    "        return x + y;\n" +
                    "    }\n" +
                    "}",
            extractedCode
        )
    }

    @Test
    fun `test extract code`() {
        val code = DocumentEditor.extractCodeBlock("```Python\\nprint(i, \\\"is odd\\\")")
        assertEquals("print(i, \"is odd\")", code)
    }

    @Test
    fun `test extract code2`() {
        val code = DocumentEditor.extractCodeBlock("""
            ```python
            print(i, "is odd")
            ```
        """.trimIndent())
        assertEquals("print(i, \"is odd\")", code)
    }
}