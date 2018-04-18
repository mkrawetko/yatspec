package com.googlecode.yatspec.parsing

import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleContent
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForProjectImpl
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.jvm.JvmAnalyzerFacade
import org.jetbrains.kotlin.resolve.jvm.JvmPlatformParameters
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.util.*
import java.util.logging.Logger

class KotlinScriptParser {
    private class SourceModuleInfo(
            override val name: Name,
            override val capabilities: Map<ModuleDescriptor.Capability<*>, Any?>,
            private val dependOnOldBuiltIns: Boolean
    ) : ModuleInfo {
        override fun dependencies() = listOf(this)

        override fun dependencyOnBuiltIns(): ModuleInfo.DependencyOnBuiltIns =
                if (dependOnOldBuiltIns) ModuleInfo.DependencyOnBuiltIns.LAST else ModuleInfo.DependencyOnBuiltIns.NONE
    }

    companion object {
        private val LOG = Logger.getLogger(KotlinScriptParser::class.java.name)
        private val messageCollector = object : MessageCollector {
            override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
//                val path = location.path
//                val position = if (path == null) "" else "$path: (${location.line}, ${location.column}) "
//
//                val text = position + message
                val text = "bla"

                if (CompilerMessageSeverity.VERBOSE.contains(severity)) {
                    LOG.finest(text)
                } else if (severity.isError) {
                    LOG.severe(text)
                    hasErrors = true
                } else if (severity == CompilerMessageSeverity.INFO) {
                    LOG.info(text)
                } else {
                    LOG.warning(text)
                }
            }

            private var hasErrors = false
            override fun clear() {
                hasErrors = false
            }

            override fun hasErrors(): Boolean {
                return hasErrors
            }

//            override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
//                val path = location.path
//                val position = if (path == null) "" else "$path: (${location.line}, ${location.column}) "
//
//                val text = position + message
//
//                if (CompilerMessageSeverity.VERBOSE.contains(severity)) {
//                    LOG.finest(text)
//                } else if (severity.isError) {
//                    LOG.severe(text)
//                    hasErrors = true
//                } else if (severity == CompilerMessageSeverity.INFO) {
//                    LOG.info(text)
//                } else {
//                    LOG.warning(text)
//                }
//            }
        }

        private val classPath: ArrayList<File> by lazy {
            val classpath = arrayListOf<File>()
            classpath += PathUtil.getResourcePathForClass(AnnotationTarget.CLASS.javaClass)
            classpath
        }
    }

    fun parse(vararg files: String): TopDownAnalysisContext {
        // The Kotlin compiler configuration
        val configuration = CompilerConfiguration()

        val groupingCollector = GroupingMessageCollector(messageCollector, true)
        val severityCollector = GroupingMessageCollector(groupingCollector, true)
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, severityCollector)


        configuration.addJvmClasspathRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
        // The path to .kt files sources
        files.forEach { configuration.addKotlinSourceRoot(it) }
        // Configuring Kotlin class path
        configuration.addJvmClasspathRoots(classPath)

        val rootDisposable = Disposer.newDisposable()
        try {
            val environment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val ktFiles = environment.getSourceFiles()

            val capabilities: Map<ModuleDescriptor.Capability<*>, Any?> = mapOf(MultiTargetPlatform.CAPABILITY to MultiTargetPlatform.Common)

            val moduleInfo = SourceModuleInfo(Name.special("<${JvmAbi.DEFAULT_MODULE_NAME}"), capabilities, false)
            val project = ktFiles.firstOrNull()?.project ?: throw AssertionError("No files to analyze")


            LanguageVersionSettingsImpl(LanguageVersion.LATEST_STABLE, ApiVersion.createByLanguageVersion(LanguageVersion.LATEST_STABLE))
            val library = object : ModuleInfo {
                override val name: Name = Name.special("<library>")
                override fun dependencies(): List<ModuleInfo> = listOf(this)
            }
            val module = object : ModuleInfo {
                override val name: Name = Name.special("<module>")
                override fun dependencies(): List<ModuleInfo> = listOf(this, library)
            }

            val projectContext = ProjectContext(project)
            val resolver = ResolverForProjectImpl(
                    "sources for metadata serializer",
                    projectContext,
                    listOf(moduleInfo),
                    { JvmAnalyzerFacade },
                    { ModuleContent(ktFiles, GlobalSearchScope.allScope(project)) },
                    JvmPlatformParameters {
                        val file = (it as JavaClassImpl).psi.containingFile.virtualFile
                        if (file in TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, ktFiles))
                            module
                        else
                            library
                    },
                    CompilerEnvironment,
//                    packagePartProviderFactory = { _, content ->
//                        JvmPackagePartProvider(LanguageVersionSettingsImpl(LanguageVersion.LATEST_STABLE, ApiVersion.createByLanguageVersion(LanguageVersion.LATEST_STABLE)), content.moduleContentScope)
//                    },
                    packagePartProviderFactory = { _, content ->
                        JvmPackagePartProvider(configuration.languageVersionSettings, content.moduleContentScope)
                    },
//                    builtIns = JvmBuiltIns(projectContext.storageManager),
                    modulePlatforms = { JvmPlatform.multiTargetPlatform }
            )

            val container = resolver.resolverForModule(moduleInfo).componentProvider

            return container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, ktFiles)
        } finally {
            rootDisposable.dispose()
            if (severityCollector.hasErrors()) {
                throw RuntimeException("Compilation error")
            }
        }
    }
}


fun main(args: Array<String>) {
    val scriptFile = "/Users/michal/workspace/my/test/kotlin-script-parser-test/TestParserKotlinTest.kt"

    val parser = KotlinScriptParser()

    val analyzeContext = parser.parse(scriptFile)

    val function = analyzeContext.functions.keys.first()
    val body = function.bodyExpression as KtBlockExpression

    val i = 0
}