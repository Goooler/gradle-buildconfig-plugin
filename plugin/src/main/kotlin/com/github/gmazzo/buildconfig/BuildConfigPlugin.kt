package com.github.gmazzo.buildconfig

import com.github.gmazzo.buildconfig.generators.BuildConfigJavaGenerator
import com.github.gmazzo.buildconfig.internal.BuildConfigSourceSetInternal
import com.github.gmazzo.buildconfig.internal.DefaultBuildConfigExtension
import com.github.gmazzo.buildconfig.internal.DefaultBuildConfigSourceSet
import com.github.gmazzo.buildconfig.internal.bindings.JavaHandler
import com.github.gmazzo.buildconfig.internal.bindings.KotlinHandler
import com.github.gmazzo.buildconfig.internal.bindings.KotlinMultiplatformHandler
import com.github.gmazzo.buildconfig.internal.bindings.PluginBindingHandler
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register

class BuildConfigPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        val sourceSets = objects.domainObjectContainer(DefaultBuildConfigSourceSet::class)

        val defaultSS = sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME)

        val extension = extensions.create(
            BuildConfigExtension::class.java,
            "buildConfig",
            DefaultBuildConfigExtension::class.java,
            sourceSets,
            defaultSS,
        )

        sourceSets.configureEach { configureSourceSet(it, defaultSS) }

        plugins.withId("java") {
            JavaHandler(project, extension).configure(sourceSets)
        }

        plugins.withAnyId(
            "org.jetbrains.kotlin.android",
            "org.jetbrains.kotlin.jvm",
            "org.jetbrains.kotlin.js",
            "kotlin2js",
        ) {
            KotlinHandler(project, extension).configure(sourceSets)
        }

        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            KotlinMultiplatformHandler(KotlinHandler(project, extension)).configure(sourceSets)
        }
    }

    private fun <SourceSet> PluginBindingHandler<SourceSet>.configure(
        specs: NamedDomainObjectContainer<out BuildConfigSourceSetInternal>
    ) {
        onBind()

        sourceSets.configureEach { ss ->
            val spec = specs.maybeCreate(nameOf(ss))

            onSourceSetAdded(ss, spec)

            (ss as? ExtensionAware)?.extensions?.add(BuildConfigClassSpec::class, "buildConfig", spec)
        }
    }

    private fun Project.configureSourceSet(
        sourceSet: BuildConfigSourceSetInternal,
        defaultSS: BuildConfigSourceSetInternal,
    ) {
        val prefix = when (sourceSet) {
            defaultSS -> ""
            else -> sourceSet.name.replaceFirstChar { it.titlecaseChar() }
        }
        val taskPrefix = if (plugins.hasPlugin("com.android.base")) "NonAndroid" else ""

        sourceSet.className.convention("${prefix}BuildConfig")
        sourceSet.packageName.convention(when (sourceSet) {
            defaultSS -> defaultPackage.map { it.replace("[^a-zA-Z._$]".toRegex(), "_") }
            else -> defaultSS.packageName
        })
        sourceSet.generator.convention(
            when (sourceSet) {
                defaultSS -> provider(::BuildConfigJavaGenerator)
                else -> defaultSS.generator
            }
        )
        sourceSet.generateTask = tasks.register<BuildConfigTask>("generate${prefix}${taskPrefix}BuildConfig") {
            group = "BuildConfig"
            description = "Generates the build constants class for '${sourceSet.name}' source"

            generator.set(sourceSet.generator)
            outputDir.set(layout.buildDirectory.dir("generated/sources/buildConfig/${sourceSet.name}"))
            specs.addAll(provider {
                (sequenceOf(sourceSet) + sourceSet.extraSpecs)
                    .map { isolate(it) }
                    .toList()
            })
        }
    }

    /**
     * Helper method to create a copy of a BuildConfigClassSpec not linked to Gradle related extensions/DSL
     * It makes it compatible with Configuration Cache
     */
    private fun Project.isolate(source: BuildConfigClassSpec) =
        objects.newInstance<BuildConfigClassSpec>(source.name).apply {
            className.set(source.className)
            packageName.set(source.packageName)
            documentation.set(source.documentation)
            buildConfigFields.addAll(source.buildConfigFields.map { isolate(it) })
        }

    private fun Project.isolate(source: BuildConfigField) =
        objects.newInstance<BuildConfigField>(source.name).apply {
            type.set(source.type)
            value.set(source.value)
            position.set(source.position)
        }

    private val Project.defaultPackage
        get() = provider {
            group
                .toString()
                .takeUnless { it.isEmpty() }
                ?.let { "$it.${project.name}" }
                ?: project.name
        }

    @Suppress("SameParameterValue")
    private fun PluginContainer.withAnyId(vararg ids: String, action: Action<in Plugin<*>>) {
        ids.forEach { withId(it, action) }
    }

}