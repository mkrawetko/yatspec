package com.googlecode.yatspec.parsing

import com.googlecode.totallylazy.Sequences.sequence
import com.googlecode.yatspec.junit.Notes
import com.googlecode.yatspec.junit.Notes.methods.notes
import com.googlecode.yatspec.junit.Row
import com.googlecode.yatspec.junit.SpecRunner
import com.googlecode.yatspec.junit.Table
import com.googlecode.yatspec.parsing.TestParser.parseTestMethods
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.String.format
import java.util.*

//@Ignore
@RunWith(SpecRunner::class)
class TestParserKotlinTest {

    @Test
    @Notes("Some method notes")
    fun testParseTestMethods() {
        val methods = parseTestMethods(javaClass)
        assertThat(notes(sequence(methods).first().annotations).get().value, `is`("Some method notes"))
    }

    @Test
    @Table(Row("meh"))
    fun yatspecWillTrimWhitespaceLeftBehindByQDoxInTableTestAnnotationsWhenAFieldVariableIsDeclared(something: String) {
        //A workaround for weirdness in QDox
    }

    @Test
    @Table(Row("string with\" quotes"))
    fun supportsQuotationMarksInParameters(value: String) {
        assertThat(value, `is`("string with\" quotes"))
    }

    @Test
    @Table(Row("string with\\ escape chars"))
    fun supportsEscapedCharactersInParameters(value: String) {
        assertThat(value, `is`("string with\\ escape chars"))
    }

    @Test
    fun shouldParseTestMethodsFromClassFoundInClassPathRatherThanFileSystem() {
        assertExistsInClassLoader("com/googlecode/yatspec/parsing/test/TestSource.java", "TestParserTest-sources.jar")
        assertExistsInClassLoader("com/googlecode/yatspec/parsing/test/TestSource.class", "TestParserTest.jar")

        val testMethods = parseTestMethods(Class.forName("com.googlecode.yatspec.parsing.test.TestSource"))
        assertThat(sequence(testMethods).first().name, equalTo("testParseMethodWithSrcJar"))
    }

    @Test
    @Table(Row(A_SIMPLE_STRING))
    fun shouldParseParametersDeclaredAsConstants(param: String) {
        assertEquals(format(Locale.ENGLISH, "failed to parse parameter [%s]", param), "aString", param)
    }

    companion object {
        private const val A_SIMPLE_STRING = "aString"

        private val thisShouldntInterfereWithTheBelowTableTest: Any? = null

        private fun assertExistsInClassLoader(resource: String, location: String) {
            assertNotNull(format("Resource '%s' not found, check location '%s' is in classpath?", resource, location), Thread.currentThread().contextClassLoader.getResource(resource))
        }
    }
}
