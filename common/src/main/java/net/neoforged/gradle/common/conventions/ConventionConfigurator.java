package net.neoforged.gradle.common.conventions;

import net.neoforged.gradle.common.extensions.IdeManagementExtension;
import net.neoforged.gradle.common.runs.ide.IdeRunIntegrationManager;
import net.neoforged.gradle.common.util.ConfigurationUtils;
import net.neoforged.gradle.dsl.common.extensions.subsystems.Conventions;
import net.neoforged.gradle.dsl.common.extensions.subsystems.Subsystems;
import net.neoforged.gradle.dsl.common.extensions.subsystems.conventions.Configurations;
import net.neoforged.gradle.dsl.common.extensions.subsystems.conventions.IDE;
import net.neoforged.gradle.dsl.common.extensions.subsystems.conventions.Runs;
import net.neoforged.gradle.dsl.common.extensions.subsystems.conventions.SourceSets;
import net.neoforged.gradle.dsl.common.extensions.subsystems.conventions.ide.IDEA;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import net.neoforged.gradle.dsl.common.runs.run.RunManager;
import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.DefaultTaskExecutionRequest;

import java.util.ArrayList;
import java.util.List;

public class ConventionConfigurator {

    public static void configureConventions(Project project) {
        final Conventions conventions = project.getExtensions().getByType(Subsystems.class).getConventions();
        configureRunConventions(project, conventions);
        configureSourceSetConventions(project, conventions);
        configureIDEConventions(project, conventions);
    }

    private static void configureSourceSetConventions(Project project, Conventions conventions) {
        final SourceSets sourceSets = conventions.getSourceSets();
        final Configurations configurations = conventions.getConfigurations();

        if (!sourceSets.getIsEnabled().get())
            return;

        if (configurations.getIsEnabled().get()) {
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                final Configuration sourceSetLocalRuntimeConfiguration = project.getConfigurations().maybeCreate(ConfigurationUtils.getSourceSetName(sourceSet, configurations.getLocalRuntimeConfigurationPostFix().get()));
                project.getConfigurations().maybeCreate(ConfigurationUtils.getSourceSetName(sourceSet, configurations.getRunRuntimeConfigurationPostFix().get()));

                final Configuration sourceSetRuntimeClasspath = project.getConfigurations().maybeCreate(sourceSet.getRuntimeClasspathConfigurationName());
                sourceSetRuntimeClasspath.extendsFrom(sourceSetLocalRuntimeConfiguration);
            });
        }
    }

    private static void configureRunConventions(Project project, Conventions conventions) {
        final Configurations configurations = conventions.getConfigurations();
        final Runs runs = conventions.getRuns();

        if (!runs.getIsEnabled().get())
            return;

        if (!configurations.getIsEnabled().get())
            return;

        final Configuration runRuntimeConfiguration = project.getConfigurations().maybeCreate(configurations.getRunRuntimeConfigurationName().get());

        project.getExtensions().configure(RunManager.class, runContainer -> runContainer.configureAll(run -> {
            run.getDependencies().getRuntime().add(runRuntimeConfiguration);
        }));

        final RunManager runManager = project.getExtensions().getByType(RunManager.class);
        project.getConfigurations().addRule("Create run specific runtime configuration", configurationName -> {
            if (!configurationName.endsWith(configurations.getPerRunRuntimeConfigurationPostFix().get()))
                return;

            final String runName = configurationName.substring(0, configurationName.length() - configurations.getPerRunRuntimeConfigurationPostFix().get().length());
            if (runManager.findByName(runName) == null)
                return;

            final Run run = runManager.getByName(runName);
            final Configuration runSpecificRuntimeConfiguration = project.getConfigurations().maybeCreate(ConfigurationUtils.getRunName(run, configurations.getPerRunRuntimeConfigurationPostFix().get()));

            run.getDependencies().getRuntime().add(runSpecificRuntimeConfiguration);
        });
    }

    private static void configureIDEConventions(Project project, Conventions conventions) {
        final IDE ideConventions = conventions.getIde();
        configureIDEAIDEConventions(project, ideConventions);
    }

    private static void configureIDEAIDEConventions(Project project, IDE ideConventions) {
        final IDEA ideaConventions = ideConventions.getIdea();
        //We need to configure the tasks to run during sync.
        final IdeManagementExtension ideManagementExtension = project.getExtensions().getByType(IdeManagementExtension.class);
        ideManagementExtension
                .onIdea((innerProject, rootProject, idea, ideaExtension) -> {
                    if (ideaConventions.getShouldUsePostSyncTask().get())
                        return;

                    if (!ideManagementExtension.isIdeaSyncing())
                        return;

                    final StartParameter startParameter = innerProject.getGradle().getStartParameter();
                    final List<TaskExecutionRequest> taskRequests = new ArrayList<>(startParameter.getTaskRequests());

                    final TaskProvider<?> ideImportTask = ideManagementExtension.getOrCreateIdeImportTask();
                    final List<String> taskPaths = new ArrayList<>();

                    final String ideImportTaskName = ideImportTask.getName();
                    final String projectPath = innerProject.getPath();

                    String taskPath;
                    if (ideImportTaskName.startsWith(":")) {
                        if (projectPath.equals(":")) {
                            taskPath = ideImportTaskName;
                        } else {
                            taskPath = String.format("%s%s", projectPath, ideImportTaskName);
                        }
                    } else {
                        if (projectPath.equals(":")) {
                            taskPath = String.format(":%s", ideImportTaskName);
                        } else {
                            taskPath = String.format("%s:%s", projectPath, ideImportTaskName);
                        }
                    }

                    taskPaths.add(taskPath);

                    taskRequests.add(new DefaultTaskExecutionRequest(taskPaths));
                    startParameter.setTaskRequests(taskRequests);
                });

        IdeRunIntegrationManager.getInstance().configureIdeaConventions(project, ideaConventions);
    }

}
