package org.metaborg.spoofax.eclipse.meta.build;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.metaborg.core.MetaborgException;
import org.metaborg.core.project.IProject;
import org.metaborg.core.project.IProjectService;
import org.metaborg.spoofax.core.project.settings.ISpoofaxProjectSettingsService;
import org.metaborg.spoofax.core.project.settings.SpoofaxProjectSettings;
import org.metaborg.spoofax.eclipse.meta.SpoofaxMetaPlugin;
import org.metaborg.spoofax.eclipse.meta.ant.EclipseAntLogger;
import org.metaborg.spoofax.eclipse.processing.EclipseCancellationToken;
import org.metaborg.spoofax.eclipse.resource.IEclipseResourceService;
import org.metaborg.spoofax.meta.core.MetaBuildInput;
import org.metaborg.spoofax.meta.core.SpoofaxMetaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;

public class PreJavaBuilder extends Builder {
    public static final String id = SpoofaxMetaPlugin.id + ".builder.prejava";

    private static final Logger logger = LoggerFactory.getLogger(PreJavaBuilder.class);

    private final ISpoofaxProjectSettingsService projectSettingsService;
    private final SpoofaxMetaBuilder builder;


    public PreJavaBuilder() {
        super(SpoofaxMetaPlugin.injector().getInstance(IEclipseResourceService.class), SpoofaxMetaPlugin.injector()
            .getInstance(IProjectService.class));
        final Injector injector = SpoofaxMetaPlugin.injector();
        this.projectSettingsService = injector.getInstance(ISpoofaxProjectSettingsService.class);
        this.builder = injector.getInstance(SpoofaxMetaBuilder.class);
    }


    @Override protected void build(final IProject project, final IProgressMonitor monitor) throws CoreException,
        MetaborgException {
        final SpoofaxProjectSettings settings = projectSettingsService.get(project);
        final MetaBuildInput input = new MetaBuildInput(project, settings);

        final IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
            @Override public void run(IProgressMonitor workspaceMonitor) throws CoreException {
                try {
                    logger.info("Building language project {}", project);
                    builder.compilePreJava(input, AntClasspathGenerator.classpaths(), new EclipseAntLogger(),
                        new EclipseCancellationToken(monitor));
                } catch(Exception e) {
                    workspaceMonitor.setCanceled(true);
                    monitor.setCanceled(true);
                    logger.error("Cannot build language project {}; build failed unexpectedly", e, project);
                } finally {
                    // Refresh project to force resource updates for files generated by the build.
                    getProject().refreshLocal(IResource.DEPTH_INFINITE, workspaceMonitor);
                }
            }
        };
        ResourcesPlugin.getWorkspace().run(runnable, getProject(), IWorkspace.AVOID_UPDATE, monitor);
    }

    @Override protected void clean(IProject project, IProgressMonitor monitor) throws CoreException, MetaborgException {

    }

    @Override protected String description() {
        return "build";
    }
}