package org.ziglang.jb

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

object ZigIcons {
    val zig = IconLoader.getIcon("/icons/zig.png", this::class.java)
    val file = IconLoader.getIcon("/icons/zig_file.png", this::class.java)
    val function = IconLoader.getIcon("/icons/zig_function.png", this::class.java)
    val icon = IconLoader.getIcon("/icons/zig_icon.png", this::class.java)
    val sdk = IconLoader.getIcon("/icons/zig_sdk.png", this::class.java)
    val variable = IconLoader.getIcon("/icons/zig_variable.png", this::class.java)
    var run = IconLoader.getIcon("/runConfigurations/testState/run.svg", AllIcons::class.java)
    var test = IconLoader.getIcon("/icons/zig_test_mark.svg", this::class.java)
    val testFailed = IconLoader.getIcon("/runConfigurations/testState/red2.svg", AllIcons::class.java)
    //val testSucceed = IconLoader.getIcon("/runConfigurations/testState/green2.svg", AllIcons::class.java)
}