package com.github.gmazzo.gradle.plugins.internal

import com.github.gmazzo.gradle.plugins.BuildConfigClassSpec
import javax.inject.Inject

internal abstract class BuildConfigClassSpecInternal @Inject constructor() :
    BuildConfigClassSpec,
    GroovyNullValueWorkaround()
