package org.ziglang.jb.execution

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.OutputListener
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer
import com.intellij.ide.actions.runAnything.execution.RunAnythingRunProfile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.execution.ParametersListUtil
import org.ziglang.jb.ZigIcons
import org.ziglang.jb.psi.ZigTestDecl
import javax.swing.Icon

class ZigTestRunLineMarkerProvider : RunLineMarkerProvider() {

    companion object {
        val testFailures = mutableMapOf<String, String>()
    }

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (psiElement is ZigTestDecl) {
            @Suppress("UnnecessaryVariable")
            val zigTestDecl = psiElement
            var testName = ""
            val testNameLiteral = zigTestDecl.findElementAt(5)
            if (testNameLiteral != null) {
                testName = testNameLiteral.text.trim('"')
            } else {
                val textBlock = zigTestDecl.text
                val offset = textBlock.indexOf('"')
                val endOffset = textBlock.indexOf('"', offset + 1)
                if (endOffset > offset) {
                    testName = textBlock.substring(offset + 1, endOffset)
                }
            }
            if (testName.isNotEmpty()) {
                testName = escapeTestName(testName)
                val zigTestCommand = getZigTestCommand(zigTestDecl, testName)
                val gutterIcon = if (testFailures.contains(zigTestCommand)) {
                    ZigIcons.testFailed
                } else {
                    ZigIcons.test
                }
                return LineMarkerInfo(
                    psiElement,
                    psiElement.textRange,
                    gutterIcon,
                    { _: PsiElement? ->
                        "Run $testName"
                    },
                    { e, elt ->
                        runSingleZigTest(zigTestDecl, testName)
                    },
                    GutterIconRenderer.Alignment.CENTER,
                    {
                        "Run $testName"
                    }
                )
            }
        }
        return null
    }

    private fun runSingleZigTest(testDeclaration: ZigTestDecl, testName: String) {
        val project = testDeclaration.project
        val workDir = project.guessProjectDir()!!
        val zigTestCommand = getZigTestCommand(testDeclaration, testName)
        val testedVirtualFile = testDeclaration.containingFile.virtualFile
        runCommand(
            project,
            workDir,
            testedVirtualFile,
            zigTestCommand,
            DefaultRunExecutor.getRunExecutorInstance(),
            SimpleDataContext.getProjectContext(project)
        )
    }

    private fun getZigTestCommand(testDeclaration: ZigTestDecl, testName: String): String {
        val project = testDeclaration.project
        val testedVirtualFile = testDeclaration.containingFile.virtualFile
        val workDir = project.guessProjectDir()!!
        val relativePath = VfsUtil.getRelativePath(testedVirtualFile, workDir)
        val zigExePath = "zig"
        return zigExePath + " test --test-filter \"${testName}\" $relativePath"
    }

    private fun runCommand(project: Project, workDirectory: VirtualFile, testedVirtualFile: VirtualFile, commandString: String, executor: Executor, dataContext: DataContext) {
        var commandDataContext = dataContext
        commandDataContext = RunAnythingCommandCustomizer.customizeContext(commandDataContext)
        val initialCommandLine = GeneralCommandLine(ParametersListUtil.parse(commandString, false, true))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkDirectory(workDirectory.path)
        val commandLine = RunAnythingCommandCustomizer.customizeCommandLine(commandDataContext, workDirectory, initialCommandLine)
        try {
            val generalCommandLine = if (Registry.`is`("run.anything.use.pty", false)) PtyCommandLine(commandLine) else commandLine
            val runAnythingRunProfile = RunZigTestProfile(generalCommandLine, commandString)
            val environment = ExecutionEnvironmentBuilder.create(project, executor, runAnythingRunProfile)
                .dataContext(commandDataContext)
                .build()
            environment.runner.execute(environment) {
                it.processHandler!!.addProcessListener(object : OutputListener(StringBuilder(), StringBuilder()) {
                    override fun processTerminated(event: ProcessEvent) {
                        super.processTerminated(event)
                        val succeeded = event.exitCode == 0
                        if ((testFailures.contains(commandString) && succeeded)
                            || (!succeeded && !testFailures.contains(commandString))
                        ) { //refresh required
                            ApplicationManager.getApplication().runReadAction {
                                if (succeeded) {
                                    testFailures.remove(commandString)
                                } else {
                                    testFailures[commandString] = "failed"
                                }
                                PsiManager.getInstance(project).findFile(testedVirtualFile)?.let { psiFile ->
                                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                                }
                            }
                        } else {
                            if (succeeded) {
                                testFailures.remove(commandString)
                            } else {
                                testFailures[commandString] = "failed"
                            }
                        }
                    }
                })
            }
        } catch (e: ExecutionException) {
            Messages.showInfoMessage(project, e.message, IdeBundle.message("run.anything.console.error.title"))
        }
    }

}

fun escapeTestName(testName: String): String {
    return testName.replace("`", "\\`")
        .replace(")", "\\)")
        .replace("(", "\\(")
}

class RunZigTestProfile(commandLine: GeneralCommandLine, originalCommand: String) : RunAnythingRunProfile(commandLine, originalCommand) {
    override fun getIcon(): Icon {
        return ZigIcons.test
    }

    override fun getName(): String {
        return if (originalCommand.contains("zig test ")) {
            originalCommand.substring(originalCommand.indexOf("zig test "))
        } else if (originalCommand.contains("zig.exe test ")) {
            originalCommand.substring(originalCommand.indexOf("zig.exe test "))
        } else {
            originalCommand
        }
    }
}
